package io.everytrade.server.plugin.impl.everytrade;

import org.knowm.xchange.Exchange;
import org.knowm.xchange.dase.dto.account.ApiAccountTxn;
import org.knowm.xchange.dase.dto.account.ApiGetAccountTxnsOutput;
import org.knowm.xchange.dase.service.DaseAccountService;
import si.mazi.rescu.HttpStatusIOException;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.LongSupplier;

/**
 * Downloads the DASE account ledger ({@code GET /v1/accounts/transactions}) incrementally, one sync run at a time.
 * <p>
 * DASE pages that ledger NEWEST FIRST and offers only a backward cursor: {@code before} returns the rows strictly
 * older than the given transaction id (DOCUMENTED at https://api.dase.com/docs: "Cursor for pagination to get
 * transactions before a specific transaction ID"; there is no {@code after}). So every run starts at the top
 * ({@code before = null}) and walks down until it meets the newest id the previous run delivered - the forward
 * watermark kept in {@link DaseDownloadState} - and then remembers the newest id it saw itself. Up to 5.2.306 the
 * downloader did the opposite: it resumed from the OLDEST id fetched, so after the first run it only ever asked for
 * rows older than everything already imported and never imported a transaction created after the first sync.
 * <p>
 * A run is capped ({@code maxPages} requests, at most {@code maxLimitTransactions} rows). When the cap cuts a walk,
 * the stretch below the last delivered row is remembered as a gap ({@link DaseDownloadState.Gap}) and resumed by the
 * next runs after their top walk, until it reaches the row it must stop at (or the end of the history for the first
 * backfill). So a history longer than one run completes over several runs and new rows still arrive meanwhile.
 * <p>
 * A trade (two {@code trade_fill_*} legs sharing a Trade ID) must reach the mapping whole - one leg alone is not
 * imported. Within a walk the legs straddling a page edge are simply both collected. Where a walk is cut by the
 * cap, the rows of a trade that is not complete in the walk are left out at the cut and the gap starts right above
 * them, so the next run fetches them together with the missing leg; if nothing else would be left, the walk
 * delivers nothing and its gap stays as it was (a run budget smaller than one trade - production's is 200 pages of
 * 100 rows - would then make no progress, which is safe but must not be configured). The top of the ledger is
 * guarded too: DASE does not document that both legs of a fill are written at once, so a trade of which the top walk
 * sees one leg written less than {@link #INCOMPLETE_TRADE_GRACE} ago is left for the next run - together with every
 * row above it, because the watermark can only sit below them all. After the grace the leg is delivered alone and
 * the mapping reports it as a trade with a missing leg, so a genuinely one-legged row cannot hold the sync back.
 * <p>
 * No row is ever delivered twice by the downloader: walks stop at an exact id, the cursor is exclusive and a row
 * met twice within one run (overlapping pages) is delivered once. Should the row a walk must stop at ever vanish
 * from the ledger, the walk goes on to the end of the history - rows are then delivered again, which the host's
 * duplicate check shows, rather than skipped without a trace. A page that cannot be paged past (no row on it has an
 * id) fails the run instead of being taken for the end of the history.
 * <p>
 * A state written by the downloader up to 5.2.306 that records imported rows fails every run with a request to
 * re-synchronize the connection: see {@link DaseDownloadState}.
 */
public class DaseDownloader {
    /** Rows per request: the documented default of the endpoint (it accepts 1..5000). */
    private static final int PAGE_SIZE = 100;

    /** Requests per sync run. */
    private static final int MAX_PAGES = 200;

    /**
     * How long a trade seen with one leg at the top of the ledger waits for its other leg. NOT MEASURED: the two legs
     * of a fill carry the same {@code created_at} in every export seen, so they are most likely written together and
     * this only covers a non-atomic write; ten minutes is far longer than one and shorter than the hourly sync.
     */
    static final Duration INCOMPLETE_TRADE_GRACE = Duration.ofMinutes(10);

    static final String LEGACY_STATE_MESSAGE = "This DASE connection was last synchronized by an earlier version "
        + "of the connector, which did not record the newest transaction it imported. New transactions cannot be "
        + "added without importing the earlier ones a second time, so please re-synchronize the connection "
        + "(Manual re-synchronization in the connection detail). It reloads all DASE transactions of this connection.";

    private final DaseAccountService accountService;
    private final DaseDownloadState state;
    private final int pageSize;
    private final int maxPages;
    private final Duration minTimeBetweenRequests;
    private final LongSupplier nowMillis;
    private final Sleeper sleeper;
    private final Duration maxTotalWait;

    /** Waits; injectable so tests do not sleep. */
    interface Sleeper {
        void sleep(long millis) throws InterruptedException;
    }

    public DaseDownloader(String downloadState, Exchange exchange) {
        this(downloadState, (DaseAccountService) exchange.getAccountService(), PAGE_SIZE, MAX_PAGES,
            MIN_TIME_BETWEEN_REQUESTS, System::currentTimeMillis);
    }

    /** For tests: a fake ledger, small pages, no pacing and a fixed clock. */
    DaseDownloader(String downloadState, DaseAccountService accountService, int pageSize, int maxPages,
                   Duration minTimeBetweenRequests, LongSupplier nowMillis) {
        this(downloadState, accountService, pageSize, maxPages, minTimeBetweenRequests, nowMillis, Thread::sleep);
    }

    /** For tests: as above, with a sleeper that does not sleep. */
    DaseDownloader(String downloadState, DaseAccountService accountService, int pageSize, int maxPages,
                   Duration minTimeBetweenRequests, LongSupplier nowMillis, Sleeper sleeper) {
        this(downloadState, accountService, pageSize, maxPages, minTimeBetweenRequests, nowMillis, sleeper,
            MAX_TOTAL_WAIT);
    }

    /** For tests: as above, with a smaller cap on all waits for one page. */
    DaseDownloader(String downloadState, DaseAccountService accountService, int pageSize, int maxPages,
                   Duration minTimeBetweenRequests, LongSupplier nowMillis, Sleeper sleeper,
                   Duration maxTotalWait) {
        this.accountService = accountService;
        this.state = DaseDownloadState.from(downloadState);
        this.pageSize = pageSize;
        this.maxPages = maxPages;
        this.minTimeBetweenRequests = minTimeBetweenRequests;
        this.nowMillis = nowMillis;
        this.sleeper = sleeper;
        this.maxTotalWait = maxTotalWait;
    }

    public String serializeState() {
        return state.serialize();
    }

    /**
     * The ledger rows not delivered by an earlier run - first the rows created since the last run, then (only while
     * an earlier run was cut by its cap) the rows of the oldest unfetched stretches - each row once, newest first.
     */
    public List<ApiAccountTxn> downloadAllTransactions(int maxLimitTransactions) {
        if (state.isLegacyWithImportedRows()) {
            throw new IllegalStateException(LEGACY_STATE_MESSAGE);
        }
        final List<ApiAccountTxn> result = new ArrayList<>();
        if (maxLimitTransactions <= 0) {
            return result;
        }
        final Budget budget = new Budget(maxPages, maxLimitTransactions);
        final Set<String> seen = new HashSet<>();
        final List<DaseDownloadState.Gap> gaps = new ArrayList<>();

        final String previousNewest = state.getNewestId();
        String newest = previousNewest;
        final Walk top = walk(null, previousNewest, budget, seen);
        List<ApiAccountTxn> topRows = top.rows();
        if (!top.complete()) {
            topRows = dropTrailingIncompleteTrades(topRows);
        }
        topRows = dropRecentIncompleteTrades(topRows, nowMillis.getAsLong() - INCOMPLETE_TRADE_GRACE.toMillis());
        if (!topRows.isEmpty()) {
            // The first row has an id (dropRecentIncompleteTrades), so has the last one of a cut walk
            // (dropTrailingIncompleteTrades).
            newest = topRows.get(0).getId();
            if (!top.complete()) {
                gaps.add(new DaseDownloadState.Gap(topRows.get(topRows.size() - 1).getId(), previousNewest));
            }
        }
        result.addAll(topRows);

        for (DaseDownloadState.Gap gap : state.getGaps()) {
            if (budget.exhausted()) {
                gaps.add(gap);
                continue;
            }
            final Walk w = walk(gap.before(), gap.stopId(), budget, seen);
            if (w.complete()) {
                result.addAll(w.rows());
                continue;
            }
            final List<ApiAccountTxn> kept = dropTrailingIncompleteTrades(w.rows());
            gaps.add(kept.isEmpty() ? gap : new DaseDownloadState.Gap(kept.get(kept.size() - 1).getId(), gap.stopId()));
            result.addAll(kept);
        }

        state.setNewestId(newest);
        state.setGaps(gaps);

        result.sort(
            Comparator.comparing(ApiAccountTxn::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                .reversed()
        );

        return result;
    }

    /** Rows of one walk, newest first, and whether it ended at its stop row or the end of the history. */
    private record Walk(List<ApiAccountTxn> rows, boolean complete) {
    }

    /** What is left of the run's cap. */
    private static final class Budget {
        private int pages;
        private int rows;

        Budget(int pages, int rows) {
            this.pages = pages;
            this.rows = rows;
        }

        boolean exhausted() {
            return pages <= 0 || rows <= 0;
        }
    }

    /**
     * Walks the ledger down from {@code before} (null = the top) and collects every row until the row whose id is
     * {@code stopId} (exclusive; null = to the end of the history) or until the run's budget is spent. A row already
     * collected in this run is skipped. The next page starts below the last row of the page that has an id; a page on
     * which no row has one cannot be paged past and fails the run.
     */
    private Walk walk(String before, String stopId, Budget budget, Set<String> seen) {
        final List<ApiAccountTxn> rows = new ArrayList<>();
        String cursor = before;
        while (true) {
            if (budget.exhausted()) {
                return new Walk(rows, false);
            }
            final ApiGetAccountTxnsOutput resp = getAccountTransactionsWithRetry(pageSize, cursor);
            budget.pages--;
            final List<ApiAccountTxn> txns = (resp == null) ? null : resp.getTransactions();
            if (txns == null || txns.isEmpty()) {
                return new Walk(rows, true);
            }
            for (ApiAccountTxn t : txns) {
                if (t == null) {
                    continue;
                }
                if (stopId != null && stopId.equals(t.getId())) {
                    return new Walk(rows, true);
                }
                if (budget.rows <= 0) {
                    return new Walk(rows, false);
                }
                if (!seen.add(seenKey(t))) {
                    continue;
                }
                rows.add(t);
                budget.rows--;
            }
            String next = null;
            for (int i = txns.size() - 1; i >= 0 && next == null; i--) {
                final ApiAccountTxn t = txns.get(i);
                next = t == null || isBlank(t.getId()) ? null : t.getId();
            }
            if (next == null) {
                throw new IllegalStateException("DASE returned a page of account transactions without a transaction "
                    + "id" + (cursor == null ? "" : " below transaction " + cursor) + ", so the older transactions "
                    + "cannot be fetched");
            }
            if (next.equals(cursor)) {
                // The page holds nothing below the cursor: the end of the history.
                return new Walk(rows, true);
            }
            cursor = next;
        }
    }

    /**
     * The id of a row, or - for a row without one, which the walk can meet again on the next page because paging
     * resumes from the last row WITH an id - everything the row says.
     */
    private static String seenKey(ApiAccountTxn t) {
        if (!isBlank(t.getId())) {
            return t.getId();
        }
        final BigDecimal amount = t.getAmount();
        return String.join("\u0000", "", t.getTxnType(), t.getCurrency(),
            amount == null ? null : amount.stripTrailingZeros().toPlainString(), String.valueOf(t.getCreatedAt()),
            t.getTradeId(), t.getFundingId());
    }

    /**
     * Drops every row from the top down to the oldest row of a trade that lacks its base or quote leg among
     * {@code rows} and has a leg written at or after {@code recentSince} (the other leg may not be written yet), and
     * then any rows without an id still at the top (the watermark must be an id). The rows above such a trade go
     * with it: the next run fetches everything above the watermark again.
     */
    private static List<ApiAccountTxn> dropRecentIncompleteTrades(List<ApiAccountTxn> rows, long recentSince) {
        final Set<String> incomplete = incompleteTrades(rows);
        final Set<String> recent = new HashSet<>();
        for (ApiAccountTxn t : rows) {
            if (incomplete.contains(t.getTradeId()) && t.getCreatedAt() >= recentSince) {
                recent.add(t.getTradeId());
            }
        }
        int from = 0;
        for (int i = 0; i < rows.size(); i++) {
            if (recent.contains(rows.get(i).getTradeId())) {
                from = i + 1;
            }
        }
        while (from < rows.size() && isBlank(rows.get(from).getId())) {
            from++;
        }
        return rows.subList(from, rows.size());
    }

    /**
     * Drops the rows at the bottom (where a cap cut the walk) that belong to a trade whose base or quote leg is not
     * among {@code rows}, or that have no id to resume from. Drops everything if nothing else would be left: the cut
     * stretch is then fetched again, whole, by a later run.
     */
    private static List<ApiAccountTxn> dropTrailingIncompleteTrades(List<ApiAccountTxn> rows) {
        final Set<String> incomplete = incompleteTrades(rows);
        int to = rows.size();
        while (to > 0 && (incomplete.contains(rows.get(to - 1).getTradeId()) || isBlank(rows.get(to - 1).getId()))) {
            to--;
        }
        return rows.subList(0, to);
    }

    /** Trade ids among {@code rows} that lack a base or a quote leg there. */
    private static Set<String> incompleteTrades(List<ApiAccountTxn> rows) {
        final Set<String> withBase = new HashSet<>();
        final Set<String> withQuote = new HashSet<>();
        final Set<String> all = new HashSet<>();
        for (ApiAccountTxn t : rows) {
            if (isBlank(t.getTradeId())) {
                continue;
            }
            all.add(t.getTradeId());
            final String type = t.getTxnType();
            if ("trade_fill_credit_base".equals(type) || "trade_fill_debit_base".equals(type)) {
                withBase.add(t.getTradeId());
            }
            if ("trade_fill_credit_quote".equals(type) || "trade_fill_debit_quote".equals(type)) {
                withQuote.add(t.getTradeId());
            }
        }
        all.removeIf(id -> withBase.contains(id) && withQuote.contains(id));
        return all;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /**
     * One page, retried on {@link IOException}: at most {@link #MAX_ATTEMPTS} requests in total. A 429 with a
     * {@code Retry-After} waits as the server asks and counts as an attempt like any other failure; a
     * {@code Retry-After} above {@link #MAX_RETRY_AFTER} ends the run instead of waiting, and so does a wait that
     * would take the waits of this page beyond {@link #MAX_TOTAL_WAIT}.
     */
    private ApiGetAccountTxnsOutput getAccountTransactionsWithRetry(int limit, String before) {
        int attempt = 0;
        long baseDelayMs = 500;
        long waitedMs = 0;

        while (true) {
            try {
                sleepBetweenRequests();
                return accountService.getAccountTransactions(limit, before);
            } catch (IOException e) {
                attempt++;
                if (attempt >= MAX_ATTEMPTS) {
                    throw new RuntimeException(
                        "Failed to fetch account transactions after " + attempt + " attempts (before=" + before + ")",
                        e
                    );
                }

                final long retryAfterMs = retryAfterMillis(e);
                final long delay;
                if (retryAfterMs > 0) {
                    // The server told us how long to wait, so waiting less is just a second rejection.
                    if (retryAfterMs > MAX_RETRY_AFTER.toMillis()) {
                        throw new RuntimeException("DASE asks to wait " + retryAfterMs / 1000 + " s before the next "
                            + "request, more than the cap of " + MAX_RETRY_AFTER.toSeconds() + " s (before=" + before
                            + ")", e);
                    }
                    delay = retryAfterMs;
                } else {
                    delay = computeBackoff(baseDelayMs, attempt) + (long) (Math.random() * 250);
                }
                if (waitedMs + delay > maxTotalWait.toMillis()) {
                    throw new RuntimeException("Failed to fetch account transactions after " + attempt + " attempts: "
                        + "waiting " + delay + " ms more would exceed " + maxTotalWait.toSeconds() + " s in total "
                        + "(before=" + before + ")", e);
                }
                waitedMs += delay;
                sleepQuietly(delay);
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

    /** Requests per page, the first one included. */
    private static final int MAX_ATTEMPTS = 6;

    /** Largest honoured {@code Retry-After}; a longer one ends the run, so a header cannot park a sync for hours. */
    private static final Duration MAX_RETRY_AFTER = Duration.ofSeconds(60);

    /**
     * Cap on all waits between the attempts for one page. With {@link #MAX_ATTEMPTS} and {@link #MAX_RETRY_AFTER}
     * as they are, the waits add up to at most 5 x 60 s, which equals this cap; it is a defensive bound that only
     * acts if one of those limits is raised.
     */
    private static final Duration MAX_TOTAL_WAIT = Duration.ofMinutes(5);

    private long lastRequestAtMs;

    private void sleepBetweenRequests() {
        final long waitMs = lastRequestAtMs + minTimeBetweenRequests.toMillis() - System.currentTimeMillis();
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

    private void sleepQuietly(long millis) {
        try {
            sleeper.sleep(millis);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(ie);
        }
    }

    private long computeBackoff(long baseMs, int attempt) {
        long delay = baseMs * (1L << Math.min(attempt, 5)); // 0.5s → 1s → 2s → 4s → 8s → cap
        return Math.min(delay, 10_000);
    }
}