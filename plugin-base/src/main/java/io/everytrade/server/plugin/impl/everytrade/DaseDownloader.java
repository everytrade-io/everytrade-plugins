package io.everytrade.server.plugin.impl.everytrade;

import io.everytrade.server.util.serialization.DownloadState;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.dase.dto.account.ApiAccountTxn;
import org.knowm.xchange.dase.dto.account.ApiGetAccountTxnsOutput;
import org.knowm.xchange.dase.service.DaseAccountService;

import java.io.IOException;
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
                return accountService.getAccountTransactions(limit, before);
            } catch (IOException e) {
                attempt++;

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