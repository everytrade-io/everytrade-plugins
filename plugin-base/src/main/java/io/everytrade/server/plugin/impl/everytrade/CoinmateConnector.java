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
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.experimental.FieldDefaults;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.ExchangeFactory;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.coinmate.CoinmateException;
import org.knowm.xchange.coinmate.CoinmateExchange;
import org.knowm.xchange.coinmate.dto.trade.CoinmateTransactionHistoryEntry;
import org.knowm.xchange.coinmate.service.CoinmateTradeService;
import org.knowm.xchange.coinmate.service.CoinmateTradeServiceRaw;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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

        long from = Math.max(0L, state.nextFrom);
        long initialTo = Instant.now().toEpochMilli();

        List<CoinmateTransactionHistoryEntry> all = new ArrayList<>();

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
                    all.add(e);
                    if (ts > maxTsSeenInBlock) {
                        maxTsSeenInBlock = ts;
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

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @FieldDefaults(level = PRIVATE)
    private static class DownloadState {
        private static final String SEPARATOR = "=";

        long nextFrom;

        public static DownloadState deserialize(String state) {
            if (isEmpty(state)) {
                return new DownloadState(0L);
            }

            String[] parts = state.split(SEPARATOR);

            if (parts.length == 1) {
                try {
                    return new DownloadState(Long.parseLong(parts[0]));
                } catch (NumberFormatException ignored) {
                }
            }

            long legacyTxFrom = parts.length > 2 ? parseLongSafe(parts[2]) : 0L;
            long legacyHighest = parts.length > 5 ? parseLongSafe(parts[5]) : 0L;

            long nextFrom = Math.max(legacyTxFrom, legacyHighest);
            return new DownloadState(nextFrom);
        }

        public String serialize() {
            return Long.toString(nextFrom);
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
