package io.everytrade.server.plugin.impl.everytrade;

import io.everytrade.server.model.SupportedExchange;
import io.everytrade.server.plugin.api.IPlugin;
import io.everytrade.server.plugin.api.connector.ConnectorDescriptor;
import io.everytrade.server.plugin.api.connector.ConnectorParameterDescriptor;
import io.everytrade.server.plugin.api.connector.ConnectorParameterType;
import io.everytrade.server.plugin.api.connector.DownloadResult;
import io.everytrade.server.plugin.api.connector.IConnector;
import io.everytrade.server.plugin.api.parser.ParseResult;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.experimental.FieldDefaults;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.ExchangeFactory;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.coinmate.CoinmateExchange;
import org.knowm.xchange.coinmate.dto.trade.CoinmateTransactionHistoryEntry;
import org.knowm.xchange.coinmate.service.CoinmateTradeServiceRaw;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static lombok.AccessLevel.PRIVATE;
import static org.apache.commons.lang3.StringUtils.isEmpty;

@AllArgsConstructor
@FieldDefaults(level = PRIVATE)
public class CoinmateConnector implements IConnector {

    private static final String ID = WhaleBooksPlugin.ID + IPlugin.PLUGIN_PATH_SEPARATOR + "coinmateApiConnector";
    private static final Logger LOG = LoggerFactory.getLogger(CoinmateConnector.class);
    // MAX 100 request per minute per user, https://coinmate.docs.apiary.io/#reference/request-limits
    // https://coinmate.docs.apiary.io/#reference/transaction-history/get-transaction-history

    private static final int TX_PER_REQUEST = 1000;
    private static final String SORT_DESC = "DESC";
    private static final int MAX_ITERATIONS = 200;

    private static final long REQUEST_SLEEP_MS = 650;
    private static final long MIN_INTERVAL_MS = 750;
    // Withdrawals show up in transactionHistory immediately, but with status NEW; they flip to COMPLETED
    // only after settlement while keeping their creation timestamp (see ETS-5075). A forward-only
    // watermark would skip them forever once anything newer is downloaded, so every sync re-scans this
    // trailing window. The host has NO import-time deduplication, therefore the download state also
    // carries the ids already handed to the host and the connector filters re-reads itself (this also
    // drops within-batch duplicates from the archived/live passes and survives Coinmate's ms timestamp
    // jitter between fetches).
    private static final long RESCAN_WINDOW_MS = 30L * 24 * 60 * 60 * 1000;
    private static final int MAX_TRACKED_IDS = 20_000;
    private static final Set<String> FINAL_STATUSES = Set.of("OK", "COMPLETED", "CANCELLED");
    private long nextAllowedRequestAtMs = 0;

    private static final ConnectorParameterDescriptor PARAMETER_API_USERNAME =
        new ConnectorParameterDescriptor(
            "username",
            ConnectorParameterType.STRING,
            "Client ID",
            "",
            false
        );

    private static final ConnectorParameterDescriptor PARAMETER_API_SECRET =
        new ConnectorParameterDescriptor(
            "apiSecret",
            ConnectorParameterType.SECRET,
            "API Secret",
            "",
            false
        );

    private static final ConnectorParameterDescriptor PARAMETER_API_KEY =
        new ConnectorParameterDescriptor(
            "apiKey",
            ConnectorParameterType.STRING,
            "API Key",
            "",
            false
        );

    public static final ConnectorDescriptor DESCRIPTOR = new ConnectorDescriptor(
        ID,
        "Coinmate Connector",
        "",
        SupportedExchange.COINMATE.getInternalId(),
        List.of(PARAMETER_API_USERNAME, PARAMETER_API_KEY, PARAMETER_API_SECRET)
    );

    Exchange exchange;

    public CoinmateConnector(Map<String, String> parameters) {
        this(
            parameters.get(PARAMETER_API_USERNAME.getId()),
            parameters.get(PARAMETER_API_KEY.getId()),
            parameters.get(PARAMETER_API_SECRET.getId())
        );
    }

    public CoinmateConnector(@NonNull String username, @NonNull String apiKey, @NonNull String secret) {
        final ExchangeSpecification exSpec = new CoinmateExchange().getDefaultExchangeSpecification();
        exSpec.setUserName(username);
        exSpec.setApiKey(apiKey);
        exSpec.setSecretKey(secret);
        exSpec.setHttpReadTimeout(Integer.MAX_VALUE);
        this.exchange = ExchangeFactory.INSTANCE.createExchange(exSpec);
    }

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public DownloadResult getTransactions(String stateStr) {
        var downloadState = DownloadState.deserialize(stateStr);
        var transactions = downloadTransactions(downloadState);
        ParseResult parseResult = new XChangeConnectorParser().getCoinMateResult(transactions);
        return new DownloadResult(parseResult, downloadState.serialize());
    }

    private List<CoinmateTransactionHistoryEntry> downloadTransactions(DownloadState state) {
        var tradeServiceRaw = new CoinmateTradeServiceRaw(exchange);

        // the rescan window must not reach behind the point where id tracking started: rows imported
        // by older plugin versions are not in importedIds and would be re-imported as duplicates
        long from = Math.max(0L, Math.max(state.rescanFloor, state.nextFrom - RESCAN_WINDOW_MS));
        long initialTo = Instant.now().toEpochMilli();

        List<CoinmateTransactionHistoryEntry> all = new ArrayList<>();
        Map<String, Long> seenThisRun = new HashMap<>();

        int iterations = 0;

        for (boolean archived : new boolean[]{false, true}) {
            long to = initialTo;
            long lastTo = -1L;

            while (true) {
                if (++iterations > MAX_ITERATIONS) {
                    LOG.warn("Hit MAX_ITERATIONS={}, returning partial result (count={})", MAX_ITERATIONS, all.size());
                    break;
                }

                if (to == lastTo) {
                    break;
                }
                lastTo = to;

                List<CoinmateTransactionHistoryEntry> block;
                try {
                    block = fetchWithRetry(tradeServiceRaw, from, to, archived);
                } catch (IOException e) {
                    throw new IllegalStateException("Download Coinmate transaction history failed.", e);
                }

                if (block == null || block.isEmpty()) {
                    break;
                }

                long maxTsSeenInBlock = -1L;

                for (CoinmateTransactionHistoryEntry e : block) {
                    long ts = e.getTimestamp();
                    if (ts < from) {
                        break;
                    }
                    if (ts > maxTsSeenInBlock) {
                        maxTsSeenInBlock = ts;
                    }
                    // A non-final entry (e.g. a withdrawal still in status NEW) must be neither emitted
                    // nor remembered, so the rescan window keeps re-reading it until it settles.
                    String status = e.getStatus();
                    if (status == null || !FINAL_STATUSES.contains(status)) {
                        continue;
                    }
                    String txId = String.valueOf(e.getTransactionId());
                    if (seenThisRun.put(txId, ts) == null && !state.importedIds.containsKey(txId)) {
                        all.add(e);
                    }
                }

                if (maxTsSeenInBlock >= 0) {
                    state.nextFrom = Math.max(state.nextFrom, maxTsSeenInBlock + 1);
                }

                if (block.size() < TX_PER_REQUEST) {
                    break;
                }

                long oldestTs = block.get(block.size() - 1).getTimestamp();
                long newTo = oldestTs - 1;

                if (newTo >= to) {
                    break;
                }
                to = newTo;

                sleepQuietly(REQUEST_SLEEP_MS);
            }
        }

        state.rememberImported(seenThisRun);

        return all;
    }

    private static void sleepQuietly(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while throttling Coinmate requests.", ie);
        }
    }

    private void throttle() {
        long now = System.currentTimeMillis();
        long wait = nextAllowedRequestAtMs - now;
        if (wait > 0) {
            sleepQuietly(wait);
        }
        nextAllowedRequestAtMs = System.currentTimeMillis() + MIN_INTERVAL_MS;
    }

    private List<CoinmateTransactionHistoryEntry> fetchWithRetry(
        CoinmateTradeServiceRaw raw,
        long from,
        long to,
        boolean archived
    ) throws IOException {

        int attempts = 0;
        long backoff = 1_000;

        while (true) {
            throttle();
            try {
                var resp = raw.getCoinmateTransactionHistory(
                    0, TX_PER_REQUEST, SORT_DESC, from, to, null, archived
                );
                return resp.getData();
            } catch (org.knowm.xchange.coinmate.CoinmateException e) {
                String msg = e.getMessage();
                if (msg != null && msg.toLowerCase().contains("too many requests")) {
                    attempts++;
                    if (attempts >= 8) {
                        throw e;
                    }
                    sleepQuietly(backoff);
                    backoff = Math.min(backoff * 2, 30_000);
                    continue;
                }
                throw e;
            }
        }
    }

    private static class DownloadState {
        private static final String V3_PREFIX = "v3=";
        // a value below this (2000-01-01) cannot be a millisecond timestamp; the most ancient states
        // stored a Coinmate transaction id instead, from which no resume point can be derived
        private static final long MIN_PLAUSIBLE_TIMESTAMP_MS = 946_684_800_000L;

        private long nextFrom;
        // ids are tracked only from this timestamp on; the rescan window must not reach behind it
        private long rescanFloor;
        // txId -> timestamp of transactions already handed to the host, kept for the rescan window;
        // the host has no import-time dedup, so re-scanned rows must be filtered out here
        private final Map<String, Long> importedIds = new HashMap<>();

        DownloadState(long nextFrom, long rescanFloor) {
            this.nextFrom = nextFrom;
            this.rescanFloor = rescanFloor;
        }

        public static DownloadState deserialize(String state) {
            if (isEmpty(state)) {
                return new DownloadState(0L, 0L);
            }

            if (state.startsWith(V3_PREFIX)) {
                String[] parts = state.substring(V3_PREFIX.length()).split("=", 3);
                DownloadState result = new DownloadState(
                    parts.length > 1 ? parseLongSafe(parts[1]) : 0L,
                    parseLongSafe(parts[0])
                );
                if (parts.length > 2 && !parts[2].isEmpty()) {
                    for (String pair : parts[2].split(",")) {
                        int colon = pair.indexOf(':');
                        if (colon > 0) {
                            result.importedIds.put(pair.substring(0, colon), parseLongSafe(pair.substring(colon + 1)));
                        }
                    }
                }
                return result;
            }

            // States written by older connector versions. Ids were not tracked yet, so the rescan floor
            // must equal the watermark - the host has no dedup and a re-read would import duplicates.
            // Observed legacy shapes: "<timestamp>", "<txId>=<timestamp>", "=0=<ts>=..." and
            // "<txId>=<ts>=<ts2>=..." where the third and sixth field hold the resume timestamp.
            String[] parts = state.split("=");
            long nextFrom;
            if (parts.length == 1) {
                nextFrom = parseLongSafe(parts[0]);
            } else if (parts.length == 2) {
                nextFrom = parseLongSafe(parts[1]);
            } else {
                nextFrom = Math.max(
                    parseLongSafe(parts[2]),
                    parts.length > 5 ? parseLongSafe(parts[5]) : 0L
                );
            }
            if (nextFrom > 0 && nextFrom < MIN_PLAUSIBLE_TIMESTAMP_MS) {
                LOG.warn("Legacy Coinmate download state '{}' holds no usable timestamp, downloading the full history.", state);
                nextFrom = 0L;
            }
            return new DownloadState(nextFrom, nextFrom);
        }

        /* The field order (floor BEFORE nextFrom) is deliberate: the previous plugin version reads the
           third '='-separated field as its resume timestamp, so after a plugin rollback it resumes
           exactly where v3 left off instead of re-downloading (and duplicating) the whole history. */
        public String serialize() {
            return V3_PREFIX + rescanFloor + "=" + nextFrom + "="
                + importedIds.entrySet().stream()
                    .map(e -> e.getKey() + ":" + e.getValue())
                    .collect(Collectors.joining(","));
        }

        /* keep ids seen in this run that still fall into the rescan window; older ones can never be
           re-downloaded again (from = nextFrom - window), so they are dropped to bound the state size */
        void rememberImported(Map<String, Long> seenThisRun) {
            importedIds.putAll(seenThisRun);
            long threshold = nextFrom - RESCAN_WINDOW_MS;
            importedIds.values().removeIf(ts -> ts < threshold);
            if (importedIds.size() > MAX_TRACKED_IDS) {
                List<Long> byAge = new ArrayList<>(importedIds.values());
                byAge.sort(null);
                long cutoff = byAge.get(importedIds.size() - MAX_TRACKED_IDS);
                importedIds.values().removeIf(ts -> ts < cutoff);
            }
        }

        private static long parseLongSafe(String s) {
            try {
                return Long.parseLong(s);
            } catch (Exception e) {
                return 0L;
            }
        }
    }
}
