package io.everytrade.server.plugin.impl.everytrade;

import io.everytrade.server.plugin.api.parser.ParseResult;
import org.junit.jupiter.api.Test;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.dase.DaseExchange;
import org.knowm.xchange.dase.dto.account.ApiAccountTxn;
import org.knowm.xchange.dase.dto.account.ApiGetAccountTxnsOutput;
import org.knowm.xchange.dase.service.DaseAccountService;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Incremental synchronization of the DASE API connector across sync runs, against a fake DASE ledger that honours
 * the documented paging of {@code GET /v1/accounts/transactions}: rows come NEWEST FIRST, {@code before} returns
 * only rows strictly older than the given transaction id (exclusive) and {@code limit} is 1..5000. Every run gets
 * the state string the previous run serialized, exactly as the host hands {@code lastDownloadedTransactionUid}
 * back to the connector. Unless a test sets it, the clock of a run stands one minute after the newest row.
 * <p>
 * The first four tests were RED on the downloader of 5.2.304/5.2.306, which resumed each run from the OLDEST id it
 * had fetched and therefore never asked DASE for a row created after the first sync; a trade cut by the run cap lost
 * both of its legs. The tests below them were added after the review of 2026-10-14, each RED on the first version
 * of the incremental downloader (its javadoc says what it guards). The guards that were never red are marked so.
 * All ids and amounts are synthetic.
 */
class DaseDownloaderIncrementalSyncTest {

    private static final int MAX_RECORDS = 90_000;

    /**
     * The host recognises this failure by its opening sentence (ApiTxDownloader.DASE_LEGACY_STATE_MESSAGE_PREFIX in
     * the everytrade host) and reports it as a user action instead of a software error. Rewording it here without
     * changing the host would turn every such sync back into a Sentry ERROR, so the sentence is pinned.
     */
    @Test
    void legacyStateMessageKeepsTheSentenceTheHostMatchesOn() {
        assertTrue(DaseDownloader.LEGACY_STATE_MESSAGE.startsWith(
            "This DASE connection was last synchronized by an earlier version of the connector"),
            "host ApiTxDownloader.DASE_LEGACY_STATE_MESSAGE_PREFIX matches on this sentence");
    }

    @Test
    void secondRunReturnsExactlyTheRowsCreatedAfterTheFirstAndThirdRunReturnsNothing() {
        final FakeDase dase = new FakeDase();
        final Ledger ledger = new Ledger();
        dase.addNewer(ledger.events(170)); // 255 rows: three pages of 100

        final Run first = run(dase, null, 100, 200);
        assertExactly(dase.ids(), first);

        final List<ApiAccountTxn> newer = ledger.events(25);
        dase.addNewer(newer);
        final Run second = run(dase, first.state(), 100, 200);
        assertExactly(ids(newer), second);
        assertTradesWhole(second);

        final Run third = run(dase, second.state(), 100, 200);
        assertExactly(List.of(), third);
        assertEquals(second.state(), third.state(), "nothing new, nothing to remember");
    }

    /** A guard, never red. */
    @Test
    void emptyAccountImportsItsFirstRowsWhenTheyAppear() {
        final FakeDase dase = new FakeDase();
        final Ledger ledger = new Ledger();

        final Run first = run(dase, null, 100, 200);
        assertExactly(List.of(), first);

        final List<ApiAccountTxn> rows = ledger.events(4);
        dase.addNewer(rows);
        final Run second = run(dase, first.state(), 100, 200);
        assertExactly(ids(rows), second);

        assertExactly(List.of(), run(dase, second.state(), 100, 200));
    }

    /**
     * A history longer than one run may fetch (here 7 pages of 10 = 70 rows) completes over several runs, while new
     * rows keep arriving - once fewer and once MORE than one run can take, so two unfetched stretches are pending at
     * the same time. Every row arrives exactly once, both legs of every trade in the same run, and once the history
     * is complete the next run still picks up new rows.
     */
    @Test
    void historyLongerThanTheRunCapCompletesOverSeveralRunsAndNewRowsStillArrive() {
        final FakeDase dase = new FakeDase();
        final Ledger ledger = new Ledger();
        dase.addNewer(ledger.events(400)); // 600 rows, newest first: trade, deposit, trade, deposit, ...
        assertEquals(dase.ledger.get(69).getTradeId(), dase.ledger.get(70).getTradeId(),
            "precondition: the run cap of 70 rows cuts a trade between its two legs");
        assertEquals(dase.ledger.get(9).getTradeId(), dase.ledger.get(10).getTradeId(),
            "precondition: a page boundary cuts a trade between its two legs");

        final List<Run> runs = new ArrayList<>();
        String state = null;
        int mostPendingStretches = 0;
        for (int i = 0; i < 100; i++) {
            if (i == 2) {
                dase.addNewer(ledger.events(10));
            }
            if (i == 4) {
                dase.addNewer(ledger.events(100)); // 150 rows: more than one run may fetch
            }
            final Run r = run(dase, state, 10, 7);
            assertTrue(r.rows().size() <= 70, "a run never exceeds its cap");
            assertTradesWhole(r);
            runs.add(r);
            state = r.state();
            mostPendingStretches = Math.max(mostPendingStretches, DaseDownloadState.from(state).getGaps().size());
            if (r.rows().isEmpty() && i > 4) {
                break;
            }
        }
        assertTrue(runs.size() > 5 && runs.get(runs.size() - 1).rows().isEmpty(), "the history completes");
        assertEquals(2, mostPendingStretches, "precondition: two unfetched stretches were pending at once");
        assertExactlyOnce(dase.ids(), runs);

        final List<ApiAccountTxn> later = ledger.events(3);
        dase.addNewer(later);
        final Run afterwards = run(dase, state, 10, 7);
        assertExactly(ids(later), afterwards);
        assertExactly(List.of(), run(dase, afterwards.state(), 10, 7));
    }

    /**
     * DASE does not document that both legs of a fill are written at once. Should a run see only one leg at the very
     * top of the ledger, that leg is left for the next run, which then delivers the trade whole - once.
     */
    @Test
    void tradeWhoseSecondLegAppearsOnlyAfterTheRunArrivesWholeInTheNextRun() {
        final FakeDase dase = new FakeDase();
        final Ledger ledger = new Ledger();
        dase.addNewer(ledger.events(9));
        final List<ApiAccountTxn> trade = ledger.trade();
        dase.addNewer(List.of(trade.get(1)));

        final Run first = run(dase, null, 100, 200);
        final List<String> withoutTheLoneLeg = new ArrayList<>(dase.ids());
        withoutTheLoneLeg.remove(trade.get(1).getId());
        assertExactly(withoutTheLoneLeg, first);

        dase.addNewer(List.of(trade.get(0)));
        final Run second = run(dase, first.state(), 100, 200);
        assertExactly(ids(trade), second);
        assertTradesWhole(second);

        assertExactly(List.of(), run(dase, second.state(), 100, 200));
    }

    /**
     * The state written up to 5.2.306 holds only the OLDEST id fetched (in DEP and WDR); which transaction was the
     * newest one imported is not in it, and the host stores every cluster it gets without de-duplicating. Walking
     * the ledger from the top would book the whole imported history a second time, and guessing a stop point could
     * skip rows without a trace. So the run fails visibly, asks for a re-synchronization (which removes the
     * container's transactions and nulls the state on the host) and does not call DASE at all. After the
     * re-synchronization the history arrives once and the sync is incremental. RED on the first version of the
     * incremental downloader, which read such a state as empty and delivered all four rows of the reviewer's probe
     * again.
     */
    @Test
    void stateOfThePreviousDownloaderFailsVisiblyInsteadOfBookingTheHistoryTwice() {
        final FakeDase dase = new FakeDase();
        final Ledger ledger = new Ledger();
        dase.addNewer(ledger.events(60));
        final String oldest = dase.ledger.get(dase.ledger.size() - 1).getId();
        dase.addNewer(ledger.events(12)); // created after the old downloader's first run

        final DaseDownloader downloader = new DaseDownloader("TRD=|DEP=" + oldest + "|WDR=" + oldest, dase, 100,
            200, Duration.ZERO, dase::now);
        final IllegalStateException refused =
            assertThrows(IllegalStateException.class, () -> downloader.downloadAllTransactions(MAX_RECORDS));
        assertTrue(refused.getMessage().contains("re-synchronize the connection"), refused.getMessage());
        assertEquals(0, dase.requests, "DASE is not even asked");

        final Run resynchronized = run(dase, null, 100, 200);
        assertExactly(dase.ids(), resynchronized);
        final List<ApiAccountTxn> newer = ledger.events(2);
        dase.addNewer(newer);
        assertExactly(ids(newer), run(dase, resynchronized.state(), 100, 200));
    }

    /**
     * The previous downloader wrote {@code TRD=|DEP=|WDR=} after a run that fetched nothing, so nothing was
     * imported and the whole history is due. A guard, never red.
     */
    @Test
    void emptyStateOfThePreviousDownloaderImportsTheWholeHistory() {
        final FakeDase dase = new FakeDase();
        final Ledger ledger = new Ledger();
        dase.addNewer(ledger.events(30));

        final Run first = run(dase, "TRD=|DEP=|WDR=", 100, 200);
        assertExactly(dase.ids(), first);
        assertExactly(List.of(), run(dase, first.state(), 100, 200));
    }

    /**
     * When the cap cuts a RESUMED stretch between the two legs of a trade, the leg above the cut is left for the
     * next run, which delivers the trade whole. Here the run spends one page on the top of the ledger and has one
     * page of 10 rows left for the stretch, whose 10th row is the first leg of a trade. RED when the cut leg is
     * kept (mutation M5 of the review: {@code kept = w.rows()}), which the history test above does not catch
     * because all its resumed stretches happen to end between two events.
     */
    @Test
    void resumedStretchCutBetweenTheLegsOfATradeDeliversTheTradeWholeInTheNextRun() {
        final FakeDase dase = new FakeDase();
        final Ledger ledger = new Ledger();
        dase.addNewer(ledger.events(40)); // 60 rows, newest first: trade, deposit, trade, deposit, ...
        final List<ApiAccountTxn> rows = dase.ledger;
        assertEquals("deposit", rows.get(2).getTxnType(), "precondition: the stretch starts below a deposit");
        assertEquals(rows.get(12).getTradeId(), rows.get(13).getTradeId(),
            "precondition: the 10th row of the stretch is the first leg of a trade");

        final String pending = pendingStretch(rows.get(0).getId(), rows.get(2).getId());
        final Run first = run(dase, pending, 10, 2);
        assertExactly(ids(rows.subList(3, 12)), first);
        assertTradesWhole(first);
        assertEquals(List.of(new DaseDownloadState.Gap(rows.get(11).getId(), null)),
            DaseDownloadState.from(first.state()).getGaps(), "the stretch now starts right above the cut trade");

        final Run second = run(dase, first.state(), 10, 7);
        assertExactly(ids(rows.subList(12, rows.size())), second);
        assertTradesWhole(second);
        assertExactly(List.of(), run(dase, second.state(), 10, 7));
    }

    /**
     * A run whose budget for a resumed stretch is smaller than one trade delivers nothing from it - neither a lone
     * leg nor a row it could not resume from - and keeps the stretch as it was for the next run. RED on the first
     * version of the incremental downloader, which then kept the lone leg and moved the stretch past it.
     */
    @Test
    void stretchCutSoShortThatOnlyALoneLegWouldRemainDeliversNothingAndKeepsTheStretch() {
        final FakeDase dase = new FakeDase();
        final Ledger ledger = new Ledger();
        dase.addNewer(ledger.events(40));
        final List<ApiAccountTxn> rows = dase.ledger;
        assertEquals(rows.get(3).getTradeId(), rows.get(4).getTradeId(),
            "precondition: the first row of the stretch is the first leg of a trade");

        final String pending = pendingStretch(rows.get(0).getId(), rows.get(2).getId());
        final Run first = run(dase, pending, 1, 2);
        assertExactly(List.of(), first);
        assertEquals(pending, first.state(), "the stretch is kept as it was");

        final Run second = run(dase, first.state(), 10, 7);
        assertExactly(ids(rows.subList(3, rows.size())), second);
        assertTradesWhole(second);
    }

    /** A guard of the state format: a stretch without a cursor to resume from cannot be recorded. */
    @Test
    void stretchWithoutACursorCannotBeRecorded() {
        assertThrows(IllegalArgumentException.class, () -> new DaseDownloadState.Gap(" ", null));
        assertThrows(IllegalArgumentException.class, () -> new DaseDownloadState.Gap(null, "tx-1"));
    }

    /**
     * Should DASE ever return the cursor row again at the top of the next page, every row is still delivered once.
     * RED when the run does not remember the ids it has already collected (mutation M9 of the review).
     */
    @Test
    void rowReturnedAgainOnTheNextPageIsDeliveredOnce() {
        final FakeDase dase = new FakeDase();
        dase.inclusiveCursor = true;
        final Ledger ledger = new Ledger();
        dase.addNewer(ledger.events(30)); // 45 rows

        final Run first = run(dase, null, 10, 200);
        assertExactly(dase.ids(), first);
        assertTradesWhole(first);

        final List<ApiAccountTxn> newer = ledger.events(4);
        dase.addNewer(newer);
        final Run second = run(dase, first.state(), 10, 200);
        assertExactly(ids(newer), second);
        assertExactly(List.of(), run(dase, second.state(), 10, 200));
    }

    /**
     * One leg of a fill is written, then another row lands above it before the second leg does. The lone leg is
     * not at the very top, yet it must wait - and with it every row above it, because the watermark of the run can
     * only sit below them all. The next run delivers the trade whole. RED on the first version of the incremental
     * downloader, which only held back incomplete trades contiguous with the very top: it delivered the lone leg,
     * moved past it, and the trade ended as two ignored "missing leg" rows in two runs (the reviewer's probe).
     */
    @Test
    void loneLegBelowANewerRowWaitsWithEverythingAboveItUntilItsPartnerArrives() {
        final FakeDase dase = new FakeDase();
        final Ledger ledger = new Ledger();
        final List<ApiAccountTxn> older = ledger.events(3);
        dase.addNewer(older);
        final List<ApiAccountTxn> trade = ledger.trade();
        dase.addNewer(List.of(trade.get(1)));
        final List<ApiAccountTxn> deposit = ledger.deposit();
        dase.addNewer(deposit);

        final Run first = run(dase, null, 100, 200);
        assertExactly(ids(older), first);

        dase.addNewer(List.of(trade.get(0)));
        final Run second = run(dase, first.state(), 100, 200);
        assertExactly(List.of(trade.get(0).getId(), deposit.get(0).getId(), trade.get(1).getId()), second);
        assertTradesWhole(second);
        assertExactly(List.of(), run(dase, second.state(), 100, 200));
    }

    /**
     * A leg whose partner has not appeared within {@link DaseDownloader#INCOMPLETE_TRADE_GRACE} is a genuinely
     * one-legged row: it is delivered (and reported by the mapping as a trade with a missing leg) instead of being
     * held while the account stays idle. RED on the first version of the incremental downloader, which held such a
     * leg at the top of an idle account for ever.
     */
    @Test
    void loneLegIsDeliveredOnceItsPartnerHasNotArrivedWithinTheGrace() {
        final FakeDase dase = new FakeDase();
        final Ledger ledger = new Ledger();
        final List<ApiAccountTxn> older = ledger.events(3);
        dase.addNewer(older);
        final ApiAccountTxn lone = ledger.trade().get(1);
        dase.addNewer(List.of(lone));

        final long writtenAt = lone.getCreatedAt();
        final Run first = run(dase, null, 100, 200, writtenAt + 60_000);
        assertExactly(ids(older), first);

        final long graceOver = writtenAt + DaseDownloader.INCOMPLETE_TRADE_GRACE.toMillis() + 60_000;
        final Run second = run(dase, first.state(), 100, 200, graceOver);
        assertExactly(List.of(lone.getId()), second);
        assertExactly(List.of(), run(dase, second.state(), 100, 200, graceOver));
    }

    /**
     * A row without an id at the end of a page must not end the history: the run pages on from the last row of the
     * page that has an id, and the row without one, which that page shows again, is still delivered once. RED on
     * the first version of the incremental downloader, which took the missing id for the end of the history and
     * never fetched the two older rows (the reviewer's probe).
     */
    @Test
    void rowWithoutIdAtTheEndOfAPageDoesNotEndTheHistory() {
        final FakeDase dase = new FakeDase();
        final ApiAccountTxn x1 = deposit("tx-x1", "funding-x1", 1_700_000_060_000L);
        final ApiAccountTxn x2 = deposit("tx-x2", "funding-x2", 1_700_000_120_000L);
        final ApiAccountTxn withoutId = deposit(null, "funding-x", 1_700_000_180_000L);
        final ApiAccountTxn x3 = deposit("tx-x3", "funding-x3", 1_700_000_240_000L);
        dase.addNewer(List.of(x3, withoutId, x2, x1));

        final Run first = run(dase, null, 2, 200);
        assertExactly(dase.ids(), first);
        assertExactly(List.of(), run(dase, first.state(), 2, 200));
    }

    /**
     * The watermark must be an id, so a row without one at the very top waits until a newer row with an id lands
     * above it, and is then delivered once. RED on the first version of the incremental downloader, which delivered
     * such a row, set the watermark below it and so delivered it again in every following run.
     */
    @Test
    void rowWithoutIdAtTheTopWaitsForANewerRowWithAnId() {
        final FakeDase dase = new FakeDase();
        final ApiAccountTxn x1 = deposit("tx-z1", "funding-z1", 1_700_000_060_000L);
        final ApiAccountTxn x2 = deposit("tx-z2", "funding-z2", 1_700_000_120_000L);
        final ApiAccountTxn withoutId = deposit(null, "funding-z", 1_700_000_180_000L);
        dase.addNewer(List.of(withoutId, x2, x1));

        final Run first = run(dase, null, 100, 200);
        assertExactly(ids(List.of(x2, x1)), first);
        final Run second = run(dase, first.state(), 100, 200);
        assertExactly(List.of(), second);

        final ApiAccountTxn x3 = deposit("tx-z3", "funding-z3", 1_700_000_240_000L);
        dase.addNewer(List.of(x3));
        final Run third = run(dase, second.state(), 100, 200);
        assertExactly(ids(List.of(x3, withoutId)), third);
        assertExactly(List.of(), run(dase, third.state(), 100, 200));
    }

    /**
     * A full page on which no row has an id cannot be paged past. The run fails visibly rather than ending the
     * history there and dropping every older row without a trace. RED on the first version of the incremental
     * downloader, which returned the rows above it as if the history had ended.
     */
    @Test
    void pageWithoutAnyIdFailsTheRunInsteadOfEndingTheHistory() {
        final FakeDase dase = new FakeDase();
        dase.addNewer(List.of(
            deposit("tx-y4", "funding-y4", 1_700_000_240_000L),
            deposit(null, "funding-y3", 1_700_000_180_000L),
            deposit(null, "funding-y2", 1_700_000_120_000L),
            deposit("tx-y1", "funding-y1", 1_700_000_060_000L)));

        final DaseDownloader downloader = new DaseDownloader(null, dase, 2, 200, Duration.ZERO, dase::now);
        final IllegalStateException refused =
            assertThrows(IllegalStateException.class, () -> downloader.downloadAllTransactions(MAX_RECORDS));
        assertTrue(refused.getMessage().contains("without a transaction id"), refused.getMessage());
    }

    // ---------------------------------------------------------------- helpers

    private record Run(List<ApiAccountTxn> rows, String state) {
    }

    private static Run run(FakeDase dase, String state, int pageSize, int maxPages) {
        return run(dase, state, pageSize, maxPages, dase.now());
    }

    private static Run run(FakeDase dase, String state, int pageSize, int maxPages, long nowMillis) {
        final DaseDownloader downloader =
            new DaseDownloader(state, dase, pageSize, maxPages, Duration.ZERO, () -> nowMillis);
        final List<ApiAccountTxn> rows = downloader.downloadAllTransactions(MAX_RECORDS);
        return new Run(rows, downloader.serializeState());
    }

    /** The state of a run that delivered the ledger down to {@code before} and was then cut by its cap. */
    private static String pendingStretch(String newestId, String before) {
        final DaseDownloadState state = DaseDownloadState.from(null);
        state.setNewestId(newestId);
        state.setGaps(List.of(new DaseDownloadState.Gap(before, null)));
        return state.serialize();
    }

    private static ApiAccountTxn deposit(String id, String fundingId, long createdAt) {
        return new ApiAccountTxn(id, "CZK", "deposit", new BigDecimal("100"), createdAt, null, fundingId);
    }

    private static List<String> ids(List<ApiAccountTxn> rows) {
        return rows.stream().map(ApiAccountTxn::getId).toList();
    }

    /** Same rows, each once (order-insensitive; a row without an id counts as null). */
    private static void assertExactly(List<String> expectedIds, Run run) {
        final Comparator<String> order = Comparator.nullsFirst(Comparator.naturalOrder());
        assertEquals(expectedIds.stream().sorted(order).toList(), ids(run.rows()).stream().sorted(order).toList());
    }

    private static void assertExactlyOnce(List<String> expectedIds, List<Run> runs) {
        final Map<String, Integer> delivered = new HashMap<>();
        runs.forEach(r -> r.rows().forEach(t -> delivered.merge(t.getId(), 1, Integer::sum)));
        final Map<String, Integer> duplicates = delivered.entrySet().stream()
            .filter(e -> e.getValue() > 1)
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        assertEquals(Map.of(), duplicates, "rows delivered more than once");
        assertEquals(new HashSet<>(expectedIds), delivered.keySet(), "rows delivered");
    }

    /** Every trade in the run has both legs in it, and the shared mapping reports nothing and imports them all. */
    private static void assertTradesWhole(Run run) {
        final Map<String, Long> legs = run.rows().stream()
            .filter(t -> t.getTradeId() != null)
            .collect(Collectors.groupingBy(ApiAccountTxn::getTradeId, LinkedHashMap::new, Collectors.counting()));
        legs.forEach((trade, count) -> assertEquals(2L, count, "legs of " + trade + " in one run"));

        final ParseResult parsed = new XChangeConnectorParser().getDaseParseResult(run.rows());
        assertEquals(List.of(), parsed.getParsingProblems().stream().map(p -> p.getMessage()).toList());
        final long deposits = run.rows().stream().filter(t -> t.getTradeId() == null).count();
        assertEquals(legs.size() + deposits, parsed.getTransactionClusters().size());
    }

    /** Synthetic events, oldest first in time; each call returns its rows NEWEST FIRST, as DASE pages them. */
    private static final class Ledger {
        private int seq;
        private long time = 1_700_000_000_000L;

        /** Alternating deposit (one row) and trade (two legs, same time and Trade ID), starting with a deposit. */
        List<ApiAccountTxn> events(int count) {
            final List<ApiAccountTxn> oldestFirst = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                time += 60_000;
                final int n = ++seq;
                if (n % 2 == 1) {
                    oldestFirst.add(new ApiAccountTxn("tx-" + n + "-d", "CZK", "deposit", new BigDecimal("100"),
                        time, null, "funding-" + n));
                } else {
                    oldestFirst.add(new ApiAccountTxn("tx-" + n + "-b", "ETH", "trade_fill_credit_base",
                        new BigDecimal("0.002"), time, "trade-" + n, null));
                    oldestFirst.add(new ApiAccountTxn("tx-" + n + "-q", "CZK", "trade_fill_debit_quote",
                        new BigDecimal("-100"), time, "trade-" + n, null));
                }
            }
            final List<ApiAccountTxn> newestFirst = new ArrayList<>(oldestFirst);
            java.util.Collections.reverse(newestFirst);
            return newestFirst;
        }

        /** One trade: its quote leg (index 0) and its base leg (index 1), newest first. */
        List<ApiAccountTxn> trade() {
            time += 60_000;
            final int n = ++seq;
            return List.of(
                new ApiAccountTxn("tx-" + n + "-q", "CZK", "trade_fill_debit_quote", new BigDecimal("-100"), time,
                    "trade-" + n, null),
                new ApiAccountTxn("tx-" + n + "-b", "ETH", "trade_fill_credit_base", new BigDecimal("0.002"), time,
                    "trade-" + n, null));
        }

        /** One deposit row. */
        List<ApiAccountTxn> deposit() {
            time += 60_000;
            final int n = ++seq;
            return List.of(DaseDownloaderIncrementalSyncTest.deposit("tx-" + n + "-d", "funding-" + n, time));
        }
    }

    /**
     * DASE ledger, newest first, paged with an exclusive backward {@code before} cursor - or, with
     * {@code inclusiveCursor}, one that returns the cursor row again at the top of the next page.
     */
    private static final class FakeDase extends DaseAccountService {
        final List<ApiAccountTxn> ledger = new ArrayList<>();
        boolean inclusiveCursor;
        int requests;

        FakeDase() {
            super(exchange());
        }

        void addNewer(List<ApiAccountTxn> newestFirst) {
            ledger.addAll(0, newestFirst);
        }

        List<String> ids() {
            return DaseDownloaderIncrementalSyncTest.ids(ledger);
        }

        /** One minute after the newest row. */
        long now() {
            return ledger.stream().mapToLong(ApiAccountTxn::getCreatedAt).max().orElse(1_700_000_000_000L) + 60_000;
        }

        @Override
        public ApiGetAccountTxnsOutput getAccountTransactions(Integer limit, String before) {
            requests++;
            if (limit == null || limit < 1 || limit > 5000) {
                throw new IllegalArgumentException("DASE limit must be 1..5000, got " + limit);
            }
            int from = 0;
            if (before != null) {
                final Set<Integer> at = new HashSet<>();
                for (int i = 0; i < ledger.size(); i++) {
                    if (before.equals(ledger.get(i).getId())) {
                        at.add(i);
                    }
                }
                if (at.size() != 1) {
                    throw new IllegalArgumentException("unknown before cursor " + before);
                }
                from = at.iterator().next() + (inclusiveCursor ? 0 : 1);
            }
            final int to = Math.min(ledger.size(), from + limit);
            return new ApiGetAccountTxnsOutput(new ArrayList<>(ledger.subList(Math.min(from, to), to)));
        }

        private static Exchange exchange() {
            final Exchange exchange = mock(Exchange.class);
            when(exchange.getExchangeSpecification()).thenReturn(new DaseExchange().getDefaultExchangeSpecification());
            return exchange;
        }
    }
}
