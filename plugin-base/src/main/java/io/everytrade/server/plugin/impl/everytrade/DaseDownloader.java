package io.everytrade.server.plugin.impl.everytrade;

import io.everytrade.server.util.serialization.DownloadState;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.dase.dto.account.ApiAccountTxn;
import org.knowm.xchange.dase.dto.account.ApiGetAccountTxnsOutput;
import org.knowm.xchange.dase.service.DaseAccountService;
import si.mazi.rescu.HttpStatusIOException;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class DaseDownloader {
    private final DaseAccountService accountService;
    private final DownloadState state;

    public DaseDownloader(String downloadState, Exchange exchange) {
        this.accountService = (DaseAccountService) exchange.getAccountService();
        this.state = DownloadState.from(downloadState);
    }

    public String serializeState() {
        return state.serialize();
    }

    public List<ApiAccountTxn> downloadAllTransactions(int maxLimitTransactions) {
        final List<ApiAccountTxn> result = new ArrayList<>(Math.min(1_000, Math.max(0, maxLimitTransactions)));
        if (maxLimitTransactions <= 0) {
            return result;
        }

        final String lastCursor = firstNonBlank(state.getLastDepositTs(), state.getLastWithdrawalTs());

        final int limit = 100;
        String before = lastCursor;

        int pages = 0;
        final int maxPages = 200;

        String pagingCursor = lastCursor;

        while (pages++ < maxPages && result.size() < maxLimitTransactions) {
            final ApiGetAccountTxnsOutput resp;
            resp = getAccountTransactionsWithRetry(limit, before);

            final List<ApiAccountTxn> txns = (resp == null) ? null : resp.getTransactions();
            if (txns == null || txns.isEmpty()) {
                break;
            }

            for (ApiAccountTxn t : txns) {
                if (result.size() >= maxLimitTransactions) {
                    break;
                }
                if (t == null) {
                    continue;
                }
                result.add(t);
            }

            final String oldestIdOnPage = txns.get(txns.size() - 1).getId();
            if (oldestIdOnPage == null || oldestIdOnPage.isBlank() || oldestIdOnPage.equals(before)) {
                break;
            }

            before = oldestIdOnPage;
            pagingCursor = oldestIdOnPage;
        }

        String safeCursor = computeSafeCursorNotCuttingTrades(result, pagingCursor);

        if (safeCursor != null && !safeCursor.isBlank()) {
            state.setNewDepositTs(safeCursor);
            state.setNewWithdrawalTs(safeCursor);
        }

        result.sort(
            Comparator.comparing(ApiAccountTxn::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                .reversed()
        );

        return result;
    }

    private static String computeSafeCursorNotCuttingTrades(List<ApiAccountTxn> batch, String defaultCursor) {
        if (batch == null || batch.isEmpty()) {
            return defaultCursor;
        }

        final int tailN = Math.min(25, batch.size());
        final List<ApiAccountTxn> tail = batch.subList(batch.size() - tailN, batch.size());

        final Set<String> tailTradeIds = new HashSet<>();
        for (ApiAccountTxn t : tail) {
            if (t != null && t.getTradeId() != null && !t.getTradeId().isBlank()) {
                tailTradeIds.add(t.getTradeId());
            }
        }
        if (tailTradeIds.isEmpty()) {
            return defaultCursor;
        }

        Set<String> incomplete = new HashSet<>();
        for (String tradeId : tailTradeIds) {
            boolean hasBase = false;
            boolean hasQuote = false;

            for (ApiAccountTxn t : batch) {
                if (t == null) {
                    continue;
                }
                if (!tradeId.equals(t.getTradeId())) {
                    continue;
                }

                String tt = t.getTxnType();
                if ("trade_fill_credit_base".equals(tt) || "trade_fill_debit_base".equals(tt)) {
                    hasBase = true;
                }
                if ("trade_fill_credit_quote".equals(tt) || "trade_fill_debit_quote".equals(tt)) {
                    hasQuote = true;
                }

                if (hasBase && hasQuote) {
                    break;
                }
            }

            if (!(hasBase && hasQuote)) {
                incomplete.add(tradeId);
            }
        }

        if (incomplete.isEmpty()) {
            return defaultCursor;
        }

        int oldestIncompleteIndex = -1;
        for (int i = batch.size() - 1; i >= 0; i--) {
            ApiAccountTxn t = batch.get(i);
            if (t == null) {
                continue;
            }
            String tradeId = t.getTradeId();
            if (tradeId != null && incomplete.contains(tradeId)) {
                oldestIncompleteIndex = i;
            }
        }

        if (oldestIncompleteIndex < 0) {
            return defaultCursor;
        }

        ApiAccountTxn oldestIncomplete = batch.get(oldestIncompleteIndex);
        String cursor = oldestIncomplete.getId();

        return (cursor == null || cursor.isBlank()) ? defaultCursor : cursor;
    }

    private ApiGetAccountTxnsOutput getAccountTransactionsWithRetry(int limit, String before) {
        int attempt = 0;
        int maxAttempts = 6;
        long baseDelayMs = 500;

        while (true) {
            try {
                sleepBetweenRequests();
                return accountService.getAccountTransactions(limit, before);
            } catch (IOException e) {
                attempt++;

                final long retryAfterMs = retryAfterMillis(e);
                if (retryAfterMs > 0) {
                    // The server told us how long to wait, so waiting less is just a second rejection. This does
                    // not consume the attempt budget: it is a scheduled wait, not a failed guess.
                    attempt--;
                    sleepQuietly(Math.min(retryAfterMs, MAX_RETRY_AFTER.toMillis()));
                    continue;
                }

                if (attempt >= maxAttempts) {
                    throw new RuntimeException(
                        "Failed to fetch account transactions after " + attempt + " attempts (before=" + before + ")",
                        e
                    );
                }

                long delay = computeBackoff(baseDelayMs, attempt);

                delay += (long) (Math.random() * 250);

                try {
                    Thread.sleep(delay);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(ie);
                }
            }
        }
    }

    /**
     * DASE rate-limits by request rate, and the paging loop below used to fire its pages back to back with no
     * pause at all - up to 200 of them. MEASURED 2026-10-12 with a live key: the first page returns 100 records,
     * a burst then earns HTTP 429, and the retry budget (6 attempts, about 25 s in total) expires before the
     * limit window does, so the whole synchronization dies. Because the pacing is ours and not the customer's,
     * it failed for every connection at once - which is exactly what Sentry reported on 2026-10-09
     * ("fails for 2 of 2 connections ... a connector-wide failure, not one user's", issues 152500372 /
     * 152510896). After a 20 s pause a single request succeeded again, so neither the credentials nor the API
     * were ever at fault.
     * <p>
     * The sibling BlockchainEthDownloader has had this throttle since it was written; DASE simply never got one.
     */
    private static final Duration MIN_TIME_BETWEEN_REQUESTS = Duration.ofMillis(350);

    /** Cap on an honoured {@code Retry-After}, so a hostile or mistaken header cannot park a sync for hours. */
    private static final Duration MAX_RETRY_AFTER = Duration.ofSeconds(60);

    private long lastRequestAtMs;

    private void sleepBetweenRequests() {
        final long waitMs = lastRequestAtMs + MIN_TIME_BETWEEN_REQUESTS.toMillis() - System.currentTimeMillis();
        if (waitMs > 0) {
            sleepQuietly(waitMs);
        }
        lastRequestAtMs = System.currentTimeMillis();
    }

    /**
     * @return the server's requested wait in milliseconds, or 0 when this is not a 429 or carries no usable
     *         {@code Retry-After}. Only the delta-seconds form is read; the HTTP-date form is rare in practice
     *         and guessing at a malformed one would be worse than falling back to the normal backoff.
     */
    private static long retryAfterMillis(IOException e) {
        if (!(e instanceof HttpStatusIOException)) {
            return 0;
        }
        final HttpStatusIOException httpError = (HttpStatusIOException) e;
        if (httpError.getHttpStatusCode() != 429) {
            return 0;
        }
        final var headers = httpError.getResponseHeaders();
        if (headers == null) {
            return 0;
        }
        for (var entry : headers.entrySet()) {
            if (entry.getKey() == null || !"retry-after".equalsIgnoreCase(entry.getKey())) {
                continue;
            }
            final var values = entry.getValue();
            if (values == null || values.isEmpty()) {
                continue;
            }
            try {
                final long seconds = Long.parseLong(values.get(0).trim());
                return seconds > 0 ? seconds * 1000L : 0;
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return 0;
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(ie);
        }
    }

    private long computeBackoff(long baseMs, int attempt) {
        long delay = baseMs * (1L << Math.min(attempt, 5)); // 0.5s → 1s → 2s → 4s → 8s → cap
        return Math.min(delay, 10_000);
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return null;
    }
}