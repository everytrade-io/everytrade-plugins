package io.everytrade.server.plugin.impl.everytrade;

import io.everytrade.server.plugin.api.connector.DownloadResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.account.FundingRecord;
import org.knowm.xchange.dto.marketdata.Trades;
import org.knowm.xchange.dto.trade.UserTrade;
import org.knowm.xchange.dto.trade.UserTrades;
import org.knowm.xchange.kucoin.KucoinAccountService;
import org.knowm.xchange.kucoin.KucoinTradeHistoryParams;
import org.knowm.xchange.kucoin.dto.response.DepositResponse;
import org.knowm.xchange.kucoin.dto.response.Pagination;
import org.knowm.xchange.kucoin.dto.response.WithdrawalResponse;
import org.knowm.xchange.service.trade.TradeService;
import org.knowm.xchange.service.trade.params.TradeHistoryParams;

import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Variant A of the KuCoin zero-width-window fix (5.2.306 base): a forward pass ends with the equal pair
 * {@code newLastStart == newLastEnd == pass top}, the next pass starts from it, and the state 5.2.306 left behind
 * (trapped, interrupted, 500x) is continued without losing a row.
 * <p>
 * The fakes answer like KuCoin behind XChange 5.1.0: fills and deposits/withdrawals are filtered by the requested
 * window (inclusive on both ends unless a test says otherwise) and come newest first. A request with
 * {@code startAt >= endAt} fails the test unless the fake is switched to lenient mode, in which it is answered like
 * any other window (this is how the 5.2.306 loops are observed). All dates (2030), ids and amounts are synthetic.
 */
class KuCoinDownloaderTrapFixTest {

    private static final long MINUTE = Duration.ofMinutes(1).toMillis();
    private static final long HOUR = Duration.ofHours(1).toMillis();
    private static final long DAY = Duration.ofDays(1).toMillis();
    private static final long WEEK = Duration.ofDays(7).toMillis();
    // computed exactly as KuCoinDownloader.EXCHANGE_START_DATE (JVM default zone)
    private static final long ES = new GregorianCalendar(2019, Calendar.MARCH, 18, 0, 0).getTimeInMillis();
    private static final long T0 = Instant.parse("2030-01-10T10:00:00Z").toEpochMilli();
    private static final String UNTOUCHED = seg(0, 0, 0, 0, 0);

    private FakeTradeService trades;
    private FakeFunding funding;
    private Exchange exchange;

    @BeforeEach
    void setUp() {
        trades = new FakeTradeService();
        funding = new FakeFunding();
        exchange = fakeExchange(trades, funding.accountService);
    }

    // ------------------------------------------------------------------ fresh connection and steady state

    /** Spec test 1: history, then pass 1, then a row made after pass 1 arrives exactly once, then one request each. */
    @Test
    void freshConnectionSecondForwardSyncDeliversNewRowsOnceAndThenSendsOneRequestPerStream() throws Exception {
        trades.add("t-h1", Instant.parse("2024-05-05T10:00:00Z").toEpochMilli());
        trades.add("t-h2", T0 - HOUR);
        funding.deposit(1, Instant.parse("2025-02-01T00:00:00Z").toEpochMilli());
        funding.withdrawal(2, Instant.parse("2026-03-01T00:00:00Z").toEpochMilli());

        // history needs two runs of 500 weekly requests from 2030 back to the exchange start
        List<String> history = new ArrayList<>();
        String state = null;
        long clock = T0;
        for (int run = 0; run < 5 && (state == null || !historyDone(state)); run++, clock += HOUR) {
            Sync s = sync(state, clock);
            history.addAll(s.all());
            state = s.state;
        }
        assertTrue(historyDone(state), state);
        assertEquals(sorted("t-h1", "t-h2", "d-1", "w-2"), sorted(history));

        // forward pass 1
        trades.add("t-n1", T0 + 2 * HOUR);
        funding.deposit(3, T0 + 2 * HOUR);
        long c1 = T0 + 3 * DAY;
        Sync pass1 = sync(state, c1);
        assertEquals(List.of("t-n1"), pass1.trades);
        assertEquals(List.of("d-3"), pass1.deposits);
        assertEquals(List.of(), pass1.withdrawals);
        assertForwardDone(pass1.state, c1);

        // rows created after pass 1 arrive in the next sync, exactly once
        trades.add("t-n2", c1 + HOUR);
        funding.withdrawal(4, c1 + HOUR);
        long c2 = c1 + 2 * DAY;
        Sync sync3 = sync(pass1.state, c2);
        assertEquals(List.of("t-n2"), sync3.trades);
        assertEquals(List.of(), sync3.deposits);
        assertEquals(List.of("w-4"), sync3.withdrawals);
        assertForwardDone(sync3.state, c2);

        // nothing new: one request per stream, nothing delivered
        long c3 = c2 + HOUR;
        Sync sync4 = sync(sync3.state, c3);
        assertEquals(List.of(), sync4.all());
        assertEquals(1, sync4.tradeRequests.size());
        assertEquals(1, sync4.depositRequests.size());
        assertEquals(1, sync4.withdrawalRequests.size());
        assertEquals(render(List.of(window(c2, c3))), render(sync4.tradeRequests));
        assertEquals(render(List.of(window(c2 - DAY, c3 - DAY))), render(sync4.depositRequests));
    }

    /** Kept from the 5.2.307 suite: a history longer than one run continues over several runs, then new rows. */
    @Test
    void historyLongerThanOneRunFinishesOverSeveralRunsAndThenPicksUpNewRows() throws Exception {
        trades.add("t-2019", Instant.parse("2019-06-01T00:00:00Z").toEpochMilli());
        trades.add("t-2022", Instant.parse("2022-03-01T00:00:00Z").toEpochMilli());
        trades.add("t-2026", Instant.parse("2026-09-01T00:00:00Z").toEpochMilli());
        trades.add("t-last", T0 - HOUR);

        List<String> collected = new ArrayList<>();
        String state = null;
        int runs = 0;
        for (; runs < 30 && (state == null || !historyDone(state)); runs++) {
            Sync s = syncTrades(state, T0 + runs * HOUR, 50);
            collected.addAll(s.trades);
            state = s.state;
        }
        assertTrue(runs > 1, "history of ~565 weekly windows must not fit into one run of 50 requests");
        assertEquals(sorted("t-2019", "t-2022", "t-2026", "t-last"), sorted(collected));

        long now = T0 + runs * HOUR;
        trades.add("t-new", now - MINUTE);
        List<String> afterHistory = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            Sync s = syncTrades(state, now + i * HOUR, 50);
            afterHistory.addAll(s.trades);
            state = s.state;
        }
        assertEquals(List.of("t-new"), afterHistory);
    }

    /** Spec test 2: 5.2.306 state with history done and no forward pass yet. */
    @Test
    void historyDoneWithoutForwardPassDeliversEverythingAfterOldFirstAndEndsAtTheEqualPair() throws Exception {
        trades.add("t-old", T0 - HOUR);
        trades.add("t-new", T0 + 3 * WEEK - HOUR);
        long clock = T0 + 4 * WEEK;

        Sync s = syncTrades(state(seg(T0, ES, ES, 0, 0), UNTOUCHED, UNTOUCHED), clock);

        assertEquals(List.of("t-new"), s.trades);
        assertEquals(seg(T0, ES, ES, clock, clock), tradeSeg(s.state));
        assertNoZeroWidthAndContiguous(s.tradeRequests, T0, clock);

        Sync next = syncTrades(s.state, clock + HOUR);
        assertEquals(List.of(), next.trades);
        assertEquals(render(List.of(window(clock, clock + HOUR))), render(next.tradeRequests));
    }

    /** Spec test 3: characterization - a 5.2.306 mid-history state continues exactly as 5.2.306 does. */
    @Test
    void midHistoryStateContinuesBelowTheStoredWindowExactlyAs5_2_306() throws Exception {
        long w = Instant.parse("2027-01-01T00:00:00Z").toEpochMilli();
        trades.add("t-below", Instant.parse("2022-06-01T00:00:00Z").toEpochMilli());
        trades.add("t-already-fetched", Instant.parse("2028-06-01T00:00:00Z").toEpochMilli());
        trades.add("t-after-first-sync", T0 + DAY);

        Sync s = syncTrades(state(seg(T0, w + WEEK, w, 0, 0), UNTOUCHED, UNTOUCHED), T0 + 2 * DAY);

        assertEquals(List.of("t-below"), s.trades);
        List<long[]> expected = new ArrayList<>();
        for (long end = w; ; ) {
            long start = Math.max(end - WEEK, ES);
            expected.add(window(start, end));
            if (start == ES) {
                break;
            }
            end = start;
        }
        assertEquals(render(expected), render(s.tradeRequests));
        assertEquals(seg(T0, ES, ES, 0, 0), tradeSeg(s.state));
    }

    // ------------------------------------------------------------------ trapped 5.2.306 states

    /** Spec test 4: the production trap. The gap and the new rows are recovered once; the short head is re-sent. */
    @Test
    void trappedStateRecoversTheGapAndNewRowsOnceAndThenReadsNothingTwice() throws Exception {
        long now2 = T0 + HOUR;           // sync 2 delivered (T0, now2] and wrote (T0, now2)
        long tn = T0 + 59 * DAY;         // last trapped sync, it only asked for [T0, T0]
        trades.add("t-before-first-sync", T0 - DAY);
        trades.add("t-resent", T0 + 30 * MINUTE);    // delivered by sync 2 already: documented re-delivery
        trades.add("t-gap", T0 + 10 * DAY);          // missed by the trap
        trades.add("t-new", tn + HOUR);
        long clock = tn + 2 * HOUR;
        assertTrue(now2 < T0 + 10 * DAY);

        Sync s = syncTrades(state(seg(T0, ES, ES, tn, T0), UNTOUCHED, UNTOUCHED), clock);

        assertEquals(List.of("t-resent", "t-gap", "t-new"), s.trades);
        assertEquals(seg(T0, ES, ES, clock, clock), tradeSeg(s.state));
        assertNoZeroWidthAndContiguous(s.tradeRequests, T0, clock);
        assertEquals(9, s.tradeRequests.size(), "59 days and 2 hours are 9 weekly windows");

        Sync next = syncTrades(s.state, clock + HOUR);
        assertEquals(List.of(), next.trades);
        assertEquals(render(List.of(window(clock, clock + HOUR))), render(next.tradeRequests));
    }

    /** Spec test 5: a connection that had exactly two syncs looks like the trap and takes the same path. */
    @Test
    void exactlyTwoSyncsStateTakesTheTrappedPathAndContinues() throws Exception {
        long now2 = T0 + 2 * HOUR;
        trades.add("t-resent", T0 + HOUR);
        trades.add("t-new", now2 + HOUR);
        long clock = now2 + 3 * HOUR;

        Sync s = syncTrades(state(seg(T0, ES, ES, now2, T0), UNTOUCHED, UNTOUCHED), clock);

        assertEquals(List.of("t-resent", "t-new"), s.trades);
        assertEquals(render(List.of(window(T0, clock))), render(s.tradeRequests));
        assertEquals(seg(T0, ES, ES, clock, clock), tradeSeg(s.state));
    }

    /**
     * Cross-writer contract (review 2026-10-18, rollback from everytrade2): everytrade2 resolves the trap
     * {@code (T0, now2..Tn)} by the owner's D4-A rule and stores the equal pair {@code (Tn, Tn)} without re-reading
     * the gap. Once that pair is stored this plugin continues above {@code Tn} like any equal pair and can no longer
     * see the gap {@code (now2, Tn]}: such a connection must be on the O1 insert-if-absent backfill list before
     * everytrade2 takes over KuCoin. This test pins that the plugin re-reads nothing below an equal pair.
     */
    @Test
    void equalPairStoredByEverytrade2AfterTheTrapIsContinuedAboveItAndTheGapBelowIsNotReRead() throws Exception {
        long tn = T0 + 59 * DAY;
        trades.add("t-resent-never", T0 + 30 * MINUTE);
        trades.add("t-gap-left-to-backfill", T0 + 10 * DAY);
        trades.add("t-new", tn + HOUR);
        long clock = tn + 2 * HOUR;

        Sync s = syncTrades(state(seg(T0, ES, ES, tn, tn), UNTOUCHED, UNTOUCHED), clock);

        assertEquals(List.of("t-new"), s.trades);
        assertEquals(render(List.of(window(tn, clock))), render(s.tradeRequests));
        assertEquals(seg(tn, ES, ES, clock, clock), tradeSeg(s.state));
    }

    /** Spec test 6: the second sync within 7 days used to spend 499 identical requests per stream. */
    @Test
    void secondSyncWithinAWeekWithRowsSendsOneRequestPerStream() throws Exception {
        trades.allowZeroWidth = true;
        funding.allowZeroWidth = true;
        trades.add("t-1", T0 + HOUR);
        funding.deposit(1, T0 + HOUR);
        funding.withdrawal(2, T0 + 2 * HOUR);
        long clock = T0 + 2 * DAY;
        String funded = seg(T0 - DAY, ES, ES, 0, 0);

        Sync s = sync(state(seg(T0, ES, ES, 0, 0), funded, funded), clock);

        assertEquals(List.of("t-1"), s.trades);
        assertEquals(List.of("d-1"), s.deposits);
        assertEquals(List.of("w-2"), s.withdrawals);
        assertEquals(render(List.of(window(T0, clock))), render(s.tradeRequests));
        assertEquals(render(List.of(window(T0 - DAY, clock - DAY))), render(s.depositRequests));
        assertEquals(render(List.of(window(T0 - DAY, clock - DAY))), render(s.withdrawalRequests));
        assertForwardDone(s.state, clock);
    }

    /** Spec test 7: a fill exactly at oldFirst used to be delivered up to 500 times. */
    @Test
    void fillExactlyAtOldFirstIsDeliveredAtMostOnceInAForwardPass() throws Exception {
        trades.allowZeroWidth = true;
        trades.add("t-edge", T0);
        long clock = T0 + 3 * DAY;

        Sync s = syncTrades(state(seg(T0, ES, ES, 0, 0), UNTOUCHED, UNTOUCHED), clock);

        assertEquals(List.of("t-edge"), s.trades);
        assertEquals(1, s.tradeRequests.size());
        assertEquals(seg(T0, ES, ES, clock, clock), tradeSeg(s.state));
    }

    /** Spec test 13: the shape 5.2.306 leaves after its 500x loop, (oldFirst, oldFirst), runs one pass. */
    @Test
    void stateLeftByThe500xLoopRunsOnePassAndDoesNotLoop() throws Exception {
        trades.allowZeroWidth = true;
        trades.add("t-edge", T0);       // the row of the 500x loop; re-sent at most once (boundary millisecond)
        trades.add("t-new", T0 + DAY);
        long clock = T0 + 3 * DAY;

        Sync s = syncTrades(state(seg(T0, ES, ES, T0, T0), UNTOUCHED, UNTOUCHED), clock);

        assertEquals(List.of("t-edge", "t-new"), s.trades);
        assertEquals(render(List.of(window(T0, clock))), render(s.tradeRequests));
        assertEquals(seg(T0, ES, ES, clock, clock), tradeSeg(s.state));
    }

    /** Spec test 11: trapped deposits recover with the funding top (clock minus one day). */
    @Test
    void trappedDepositsRecoverTheGapWithTheOneDayFundingLag() throws Exception {
        long d0 = T0 - DAY;
        long dn = T0 + 58 * DAY;
        funding.deposit(1, d0 + 30 * MINUTE);   // delivered by sync 2 already: documented re-delivery
        funding.deposit(2, d0 + 10 * DAY);      // missed by the trap
        funding.deposit(3, dn + HOUR);          // new
        long clock = dn + DAY + 2 * HOUR;
        funding.deposit(4, clock - 12 * HOUR);  // inside the one-day lag: next sync
        String state = state(UNTOUCHED, seg(d0, ES, ES, dn, d0), UNTOUCHED);

        KuCoinDownloader run = downloader(state, clock, 500);
        assertEquals(List.of("d-1", "d-2", "d-3"), ids(run.downloadFundings(FundingRecord.Type.DEPOSIT)));
        assertEquals(seg(d0, ES, ES, clock - DAY, clock - DAY), depositSeg(run.serializeState()));
        assertNoZeroWidthAndContiguous(funding.requests("D"), d0, clock - DAY);

        long clock2 = clock + DAY;
        KuCoinDownloader next = downloader(run.serializeState(), clock2, 500);
        assertEquals(List.of("d-4"), ids(next.downloadFundings(FundingRecord.Type.DEPOSIT)));
        assertEquals(seg(clock - DAY, ES, ES, clock2 - DAY, clock2 - DAY), depositSeg(next.serializeState()));
    }

    /** Spec test 12: withdrawals keep arriving across three syncs. */
    @Test
    void withdrawalsSteadyStateAcrossThreeSyncs() throws Exception {
        long w0 = T0 - DAY;
        String state = state(UNTOUCHED, UNTOUCHED, seg(w0, ES, ES, 0, 0));
        List<String> delivered = new ArrayList<>();

        funding.withdrawal(1, T0 + HOUR);
        KuCoinDownloader a = downloader(state, T0 + 2 * DAY, 500);
        delivered.addAll(ids(a.downloadFundings(FundingRecord.Type.WITHDRAWAL)));

        funding.withdrawal(2, T0 + DAY + HOUR);
        KuCoinDownloader b = downloader(a.serializeState(), T0 + 3 * DAY, 500);
        delivered.addAll(ids(b.downloadFundings(FundingRecord.Type.WITHDRAWAL)));

        funding.withdrawal(3, T0 + 2 * DAY + HOUR);
        KuCoinDownloader c = downloader(b.serializeState(), T0 + 10 * DAY, 500);
        delivered.addAll(ids(c.downloadFundings(FundingRecord.Type.WITHDRAWAL)));

        KuCoinDownloader d = downloader(c.serializeState(), T0 + 10 * DAY + HOUR, 500);
        delivered.addAll(ids(d.downloadFundings(FundingRecord.Type.WITHDRAWAL)));

        assertEquals(List.of("w-1", "w-2", "w-3"), delivered);
        assertEquals(4, funding.requests("W").size(), "one request per sync");
        assertEquals(seg(T0 + 9 * DAY, ES, ES, T0 + 9 * DAY + HOUR, T0 + 9 * DAY + HOUR),
            withdrawalSeg(d.serializeState()));
    }

    // ------------------------------------------------------------------ interrupted passes and the request cap

    /** Spec test 8: a pass of the fixed code interrupted at s finishes [W, s] and stores its own top P. */
    @Test
    void interruptedFixedPassFinishesBelowItsLastWindowAndContinuesFromItsTop() throws Exception {
        long w = T0;
        long s = T0 + 3 * WEEK;
        long p = T0 + 5 * WEEK;
        trades.add("t-a", w + DAY);
        trades.add("t-b", s - HOUR);
        trades.add("t-already-delivered", s + DAY);
        trades.add("t-after-top", p + HOUR);
        long clock = p + DAY;

        Sync first = syncTrades(state(seg(w, ES, ES, p, s), UNTOUCHED, UNTOUCHED), clock);
        assertEquals(List.of("t-a", "t-b"), first.trades);
        assertNoZeroWidthAndContiguous(first.tradeRequests, w, s);
        assertEquals(seg(w, ES, ES, p, p), tradeSeg(first.state));

        long clock2 = clock + DAY;
        Sync second = syncTrades(first.state, clock2);
        assertEquals(List.of("t-after-top"), second.trades);
        assertEquals(render(List.of(window(p, clock2))), render(second.tradeRequests));
        assertEquals(seg(p, ES, ES, clock2, clock2), tradeSeg(second.state));
    }

    /** Spec test 9: the request cap stops a pass; the stored end is the pass top and the pass resumes losslessly. */
    @Test
    void requestCapMidPassStoresThePassTopAndResumesWithoutLossOrDuplicate() throws Exception {
        long w = T0;
        long clock1 = w + 30 * WEEK;
        trades.add("t-1", w + DAY);
        trades.add("t-2", w + 12 * WEEK + DAY);
        trades.add("t-3", w + 25 * WEEK + DAY);
        trades.add("t-4", w + 29 * WEEK + DAY);
        String state = state(seg(w - 10 * WEEK, ES, ES, w, w), UNTOUCHED, UNTOUCHED);
        List<String> delivered = new ArrayList<>();
        List<long[]> requests = new ArrayList<>();

        Sync run1 = syncTrades(state, clock1, 10);
        assertEquals(seg(w, ES, ES, clock1, w + 20 * WEEK), tradeSeg(run1.state), "newLastEnd must be the pass top");
        delivered.addAll(run1.trades);
        requests.addAll(run1.tradeRequests);

        Sync run2 = syncTrades(run1.state, clock1 + HOUR, 10);
        assertEquals(seg(w, ES, ES, clock1, w + 10 * WEEK), tradeSeg(run2.state));
        delivered.addAll(run2.trades);
        requests.addAll(run2.tradeRequests);

        Sync run3 = syncTrades(run2.state, clock1 + 2 * HOUR, 10);
        assertEquals(seg(w, ES, ES, clock1, clock1), tradeSeg(run3.state));
        delivered.addAll(run3.trades);
        requests.addAll(run3.tradeRequests);
        assertNoZeroWidthAndContiguous(requests, w, clock1);

        trades.add("t-5", clock1 + 30 * MINUTE);
        Sync run4 = syncTrades(run3.state, clock1 + 3 * HOUR, 10);
        assertEquals(render(List.of(window(clock1, clock1 + 3 * HOUR))), render(run4.tradeRequests));
        delivered.addAll(run4.trades);

        assertEquals(sorted("t-1", "t-2", "t-3", "t-4", "t-5"), sorted(delivered));
    }

    /** Spec test 10: a 5.2.306 pass interrupted by the cap stored the top of its last window, not its pass top. */
    @Test
    void interrupted5_2_306PassIsFinishedAndEverythingAfterItsLastWindowArrivesInTheNextSync() throws Exception {
        long w = T0;
        long s = T0 + 4 * WEEK;
        long e = s + WEEK;               // 5.2.306 stored newEnd of its last window
        long p = T0 + 8 * WEEK;          // the interrupted pass's top, not stored
        trades.add("t-a", w + DAY);
        trades.add("t-in-last-window", s + 2 * DAY);   // delivered by the interrupted run
        trades.add("t-c", e + DAY);                    // delivered by the interrupted run: documented re-delivery
        trades.add("t-d", p + DAY);
        long clock1 = p + 2 * DAY;

        Sync first = syncTrades(state(seg(w, ES, ES, e, s), UNTOUCHED, UNTOUCHED), clock1);
        assertEquals(List.of("t-a"), first.trades);
        assertEquals(seg(w, ES, ES, e, e), tradeSeg(first.state));

        long clock2 = clock1 + HOUR;
        Sync second = syncTrades(first.state, clock2);
        assertEquals(List.of("t-c", "t-d"), second.trades);
        assertNoZeroWidthAndContiguous(second.tradeRequests, e, clock2);
        assertEquals(seg(e, ES, ES, clock2, clock2), tradeSeg(second.state));
    }

    /** Spec test 14: a clock that is not after the watermark sends nothing and changes no pair. */
    @Test
    void clockNotAfterTheWatermarkSendsNoRequestAndKeepsThePairs() throws Exception {
        String funded = seg(T0 - 10 * WEEK, ES, ES, T0 - DAY, T0 - DAY);
        String state = state(seg(T0 - 10 * WEEK, ES, ES, T0, T0), funded, funded);
        trades.add("t-1", T0 + HOUR);

        for (long clock : new long[]{T0 - HOUR, T0}) {
            Sync s = sync(state, clock);
            assertEquals(List.of(), s.all());
            assertEquals(0, s.tradeRequests.size() + s.depositRequests.size() + s.withdrawalRequests.size());
            // the trade watermark T0 becomes oldFirst as on every 5.2.306 pass start; nothing else changes
            assertEquals(seg(T0, ES, ES, T0, T0), tradeSeg(s.state));
            assertEquals(funded, depositSeg(s.state));
            assertEquals(funded, withdrawalSeg(s.state));
        }

        Sync later = sync(state, T0 + DAY);
        assertEquals(List.of("t-1"), later.trades);
        assertEquals(render(List.of(window(T0, T0 + DAY))), render(later.tradeRequests));
    }

    // ------------------------------------------------------------------ window edges

    /** Spec test 15 ([) bounds): a row on a window edge or on the pass edge arrives exactly once. */
    @Test
    void boundaryRowsWithStartInclusiveBoundsArriveExactlyOnce() throws Exception {
        assertBoundaryRows(Bounds.START_INCLUSIVE, 1);
    }

    /** Spec test 15 ((] bounds): a row on a window edge or on the pass edge arrives exactly once. */
    @Test
    void boundaryRowsWithEndInclusiveBoundsArriveExactlyOnce() throws Exception {
        assertBoundaryRows(Bounds.END_INCLUSIVE, 1);
    }

    /**
     * Spec test 15 ([] bounds): never lost; a row exactly on an edge arrives twice. This is the boundary duplicate
     * 5.2.306 already has on every window edge (O42), and the fix adds no other kind of edge.
     */
    @Test
    void boundaryRowsWithBothBoundsInclusiveAreNeverLostAndDuplicatedOnlyOnTheEdge() throws Exception {
        assertBoundaryRows(Bounds.CLOSED, 2);
    }

    private void assertBoundaryRows(Bounds bounds, int edgeCopies) throws Exception {
        trades.bounds = bounds;
        long w = T0;
        long c1 = w + 3 * WEEK + HOUR;
        trades.add("t-inner-edge", c1 - WEEK);   // edge between two windows of one pass
        trades.add("t-pass-edge", c1);           // edge between two passes: the clock of pass 1
        trades.add("t-mid", w + 2 * DAY);
        String state = state(seg(w - WEEK, ES, ES, w, w), UNTOUCHED, UNTOUCHED);

        Sync s1 = syncTrades(state, c1);
        Sync s2 = syncTrades(s1.state, c1 + DAY);
        List<String> delivered = new ArrayList<>(s1.trades);
        delivered.addAll(s2.trades);

        assertEquals(1, Collections.frequency(delivered, "t-mid"), () -> delivered.toString());
        assertEquals(edgeCopies, Collections.frequency(delivered, "t-inner-edge"), () -> delivered.toString());
        assertEquals(edgeCopies, Collections.frequency(delivered, "t-pass-edge"), () -> delivered.toString());
    }

    // ------------------------------------------------------------------ state format

    /** Spec test 16: the written state keeps the 15-field format and round-trips. */
    @Test
    void writtenStateHasFifteenFieldsAndRoundTrips() {
        trades.add("t-1", T0 + HOUR);
        funding.deposit(1, T0 + HOUR);
        long clock = T0 + 3 * DAY;
        String funded = seg(T0 - DAY, ES, ES, 0, 0);

        DownloadResult result = new KuCoinDownloader(exchange, state(seg(T0, ES, ES, 0, 0), funded, funded), clock,
            500, ms -> { }).download();

        String written = result.getDownloadStateData();
        long[] fields = Arrays.stream(written.split("\\|")).mapToLong(Long::parseLong).toArray();
        assertEquals(15, fields.length, written);
        assertEquals(state(seg(T0, ES, ES, clock, clock), seg(T0 - DAY, ES, ES, clock - DAY, clock - DAY),
            seg(T0 - DAY, ES, ES, clock - DAY, clock - DAY)), written);
        assertEquals(written, downloader(written, clock, 500).serializeState());
        assertEquals(2, result.getParseResult().getTransactionClusters().size(),
            () -> "clusters " + result.getParseResult());
    }

    // ------------------------------------------------------------------ helpers

    private KuCoinDownloader downloader(String state, long clock, int maxRequests) {
        return new KuCoinDownloader(exchange, state, clock, maxRequests, ms -> { });
    }

    private Sync sync(String state, long clock) throws Exception {
        int t = trades.requests.size();
        int f = funding.log.size();
        KuCoinDownloader d = downloader(state, clock, 500);
        List<String> tr = tradeIds(d.downloadTrades());
        List<String> dep = ids(d.downloadFundings(FundingRecord.Type.DEPOSIT));
        List<String> wd = ids(d.downloadFundings(FundingRecord.Type.WITHDRAWAL));
        return new Sync(tr, dep, wd, d.serializeState(), new ArrayList<>(trades.requests.subList(t, trades.requests.size())),
            funding.requestsSince(f, "D"), funding.requestsSince(f, "W"));
    }

    private Sync syncTrades(String state, long clock) throws Exception {
        return syncTrades(state, clock, 500);
    }

    private Sync syncTrades(String state, long clock, int maxRequests) throws Exception {
        int t = trades.requests.size();
        KuCoinDownloader d = downloader(state, clock, maxRequests);
        List<String> tr = tradeIds(d.downloadTrades());
        return new Sync(tr, List.of(), List.of(), d.serializeState(),
            new ArrayList<>(trades.requests.subList(t, trades.requests.size())), List.of(), List.of());
    }

    private static final class Sync {
        final List<String> trades;
        final List<String> deposits;
        final List<String> withdrawals;
        final String state;
        final List<long[]> tradeRequests;
        final List<long[]> depositRequests;
        final List<long[]> withdrawalRequests;

        Sync(List<String> trades, List<String> deposits, List<String> withdrawals, String state,
             List<long[]> tradeRequests, List<long[]> depositRequests, List<long[]> withdrawalRequests) {
            this.trades = trades;
            this.deposits = deposits;
            this.withdrawals = withdrawals;
            this.state = state;
            this.tradeRequests = tradeRequests;
            this.depositRequests = depositRequests;
            this.withdrawalRequests = withdrawalRequests;
        }

        List<String> all() {
            List<String> all = new ArrayList<>(trades);
            all.addAll(deposits);
            all.addAll(withdrawals);
            return all;
        }
    }

    private static String seg(long oldFirst, long oldLastEnd, long oldLastStart, long newLastEnd, long newLastStart) {
        return oldFirst + "|" + oldLastEnd + "|" + oldLastStart + "|" + newLastEnd + "|" + newLastStart;
    }

    private static String state(String trades, String deposits, String withdrawals) {
        return trades + "|" + deposits + "|" + withdrawals;
    }

    private static String part(String state, int index) {
        String[] f = state.split("\\|");
        assertEquals(15, f.length, state);
        return String.join("|", Arrays.copyOfRange(f, index * 5, index * 5 + 5));
    }

    private static String tradeSeg(String state) {
        return part(state, 0);
    }

    private static String depositSeg(String state) {
        return part(state, 1);
    }

    private static String withdrawalSeg(String state) {
        return part(state, 2);
    }

    private static boolean historyDone(String state) {
        String[] f = state.split("\\|");
        for (int i = 0; i < 3; i++) {
            if (Long.parseLong(f[i * 5 + 1]) != ES || Long.parseLong(f[i * 5 + 2]) != ES) {
                return false;
            }
        }
        return true;
    }

    /** A completed forward pass stores the equal pair: the clock for fills, the clock minus one day for funding. */
    private static void assertForwardDone(String state, long clock) {
        String[] f = state.split("\\|");
        assertEquals(clock, Long.parseLong(f[3]), state);
        assertEquals(clock, Long.parseLong(f[4]), state);
        for (int i = 1; i < 3; i++) {
            assertEquals(clock - DAY, Long.parseLong(f[i * 5 + 3]), state);
            assertEquals(clock - DAY, Long.parseLong(f[i * 5 + 4]), state);
        }
    }

    /** The windows are non-empty, at most a week wide and tile [bottom, top] without a gap or an overlap. */
    private static void assertNoZeroWidthAndContiguous(List<long[]> requests, long bottom, long top) {
        List<long[]> sortedRequests = new ArrayList<>(requests);
        sortedRequests.sort(Comparator.comparingLong(r -> r[0]));
        long expectedStart = bottom;
        for (long[] r : sortedRequests) {
            assertTrue(r[0] < r[1] && r[1] - r[0] <= WEEK, () -> "bad window " + render(List.of(r)));
            assertEquals(expectedStart, r[0], () -> "gap or overlap in " + render(sortedRequests));
            expectedStart = r[1];
        }
        assertEquals(top, expectedStart, () -> "pass does not reach its top: " + render(sortedRequests));
    }

    private static long[] window(long start, long end) {
        return new long[]{start, end};
    }

    private static String render(List<long[]> windows) {
        return windows.stream().map(w -> "[" + Instant.ofEpochMilli(w[0]) + ", " + Instant.ofEpochMilli(w[1]) + "]")
            .collect(Collectors.joining(", "));
    }

    private static List<String> sorted(String... ids) {
        return Arrays.stream(ids).sorted().collect(toList());
    }

    private static List<String> sorted(List<String> ids) {
        return ids.stream().sorted().collect(toList());
    }

    private static List<String> tradeIds(List<UserTrade> trades) {
        return trades.stream()
            .sorted(Comparator.comparing(UserTrade::getTimestamp))
            .map(UserTrade::getId)
            .collect(toList());
    }

    private static List<String> ids(List<FundingRecord> records) {
        return records.stream()
            .sorted(Comparator.comparing(FundingRecord::getDate))
            .map(r -> (r.getType() == FundingRecord.Type.DEPOSIT ? "d-" : "w-") + r.getAmount().intValue())
            .collect(toList());
    }

    private static Exchange fakeExchange(TradeService tradeService, KucoinAccountService accountService) {
        return (Exchange) Proxy.newProxyInstance(
            KuCoinDownloaderTrapFixTest.class.getClassLoader(),
            new Class<?>[]{Exchange.class},
            (proxy, method, args) -> {
                switch (method.getName()) {
                    case "getTradeService":
                        return tradeService;
                    case "getAccountService":
                        return accountService;
                    default:
                        throw new UnsupportedOperationException(method.getName());
                }
            });
    }

    /** How the fake interprets [startAt, endAt]; KuCoin's real behaviour is unverified (O42). */
    enum Bounds {
        CLOSED, START_INCLUSIVE, END_INCLUSIVE;

        boolean contains(long t, long start, long end) {
            switch (this) {
                case START_INCLUSIVE:
                    return t >= start && t < end;
                case END_INCLUSIVE:
                    return t > start && t <= end;
                default:
                    return t >= start && t <= end;
            }
        }
    }

    private static void checkWindow(long start, long end, boolean allowZeroWidth) {
        if (end - start > WEEK) {
            throw new IllegalArgumentException("KuCoin allows at most one week per request");
        }
        if (!allowZeroWidth && start >= end) {
            throw new IllegalArgumentException("zero-width or inverted window ["
                + Instant.ofEpochMilli(start) + ", " + Instant.ofEpochMilli(end) + "]");
        }
    }

    /** GET /api/v1/fills behind XChange 5.1.0: rows of the window, newest first, one page of at most 500. */
    static final class FakeTradeService implements TradeService {
        final List<UserTrade> rows = new ArrayList<>();
        final List<long[]> requests = new ArrayList<>();
        Bounds bounds = Bounds.CLOSED;
        boolean allowZeroWidth;

        void add(String id, long timestamp) {
            rows.add(new UserTrade.Builder()
                .type(Order.OrderType.BID)
                .originalAmount(BigDecimal.ONE)
                .currencyPair(CurrencyPair.BTC_USDT)
                .price(BigDecimal.TEN)
                .timestamp(new Date(timestamp))
                .id(id)
                .orderId("o-" + id)
                .feeAmount(BigDecimal.ZERO)
                .feeCurrency(org.knowm.xchange.currency.Currency.USDT)
                .build());
        }

        @Override
        public TradeHistoryParams createTradeHistoryParams() {
            return new KucoinTradeHistoryParams();
        }

        @Override
        public UserTrades getTradeHistory(TradeHistoryParams params) {
            KucoinTradeHistoryParams p = (KucoinTradeHistoryParams) params;
            long start = p.getStartTime().getTime();
            long end = p.getEndTime().getTime();
            requests.add(window(start, end));
            checkWindow(start, end, allowZeroWidth);
            List<UserTrade> match = rows.stream()
                .filter(t -> bounds.contains(t.getTimestamp().getTime(), start, end))
                .sorted(Comparator.comparing(UserTrade::getTimestamp).reversed())
                .limit(500)
                .collect(Collectors.toList());
            return new UserTrades(match, 0, Trades.TradeSortType.SortByTimestamp, null);
        }
    }

    /**
     * Deposits and withdrawals behind XChange 5.1.0: the real {@code KucoinAccountService.getFundingHistory} calls
     * the raw list methods, which answer the first page (50 rows) of the window, newest first.
     */
    static final class FakeFunding {
        static final int DEFAULT_PAGE_SIZE = 50;
        final List<DepositResponse> deposits = new ArrayList<>();
        final List<WithdrawalResponse> withdrawals = new ArrayList<>();
        final List<Object[]> log = new ArrayList<>();
        final KucoinAccountService accountService = mock(KucoinAccountService.class);
        boolean allowZeroWidth;

        FakeFunding() {
            try {
                when(accountService.createFundingHistoryParams()).thenAnswer(i -> new KucoinTradeHistoryParams());
                when(accountService.getFundingHistory(any())).thenCallRealMethod();
                when(accountService.getDepositList(isNull(), isNull(), any(), any(), any(), any())).thenAnswer(i ->
                    page("D", deposits, DepositResponse::getCreatedAt, i.getArgument(2), i.getArgument(3)));
                when(accountService.getWithdrawalsList(isNull(), isNull(), any(), any(), any(), any())).thenAnswer(i ->
                    page("W", withdrawals, WithdrawalResponse::getCreatedAt, i.getArgument(2), i.getArgument(3)));
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }

        void deposit(int amount, long createdAt) {
            DepositResponse d = new DepositResponse();
            d.setAmount(BigDecimal.valueOf(amount));
            d.setFee(BigDecimal.ZERO);
            d.setCurrency("USDT");
            d.setAddress("synthetic-deposit-address");
            d.setStatus("SUCCESS");
            d.setCreatedAt(new Date(createdAt));
            deposits.add(d);
        }

        void withdrawal(int amount, long createdAt) {
            WithdrawalResponse w = new WithdrawalResponse();
            w.setId("w-" + amount);
            w.setAmount(BigDecimal.valueOf(amount));
            w.setFee(BigDecimal.ZERO);
            w.setCurrency("USDT");
            w.setAddress("synthetic-withdrawal-address");
            w.setStatus("SUCCESS");
            w.setCreatedAt(new Date(createdAt));
            withdrawals.add(w);
        }

        List<long[]> requests(String type) {
            return requestsSince(0, type);
        }

        List<long[]> requestsSince(int from, String type) {
            return log.subList(from, log.size()).stream()
                .filter(e -> e[0].equals(type))
                .map(e -> (long[]) e[1])
                .collect(toList());
        }

        private <T> Pagination<T> page(String type, List<T> all, Function<T, Date> time, Long startAt, Long endAt) {
            log.add(new Object[]{type, window(startAt, endAt)});
            checkWindow(startAt, endAt, allowZeroWidth);
            List<T> match = all.stream()
                .filter(r -> Bounds.CLOSED.contains(time.apply(r).getTime(), startAt, endAt))
                .sorted(Comparator.comparing(time).reversed())
                .limit(DEFAULT_PAGE_SIZE)
                .collect(Collectors.toList());
            return new Pagination<>(1, DEFAULT_PAGE_SIZE, (long) match.size(), new ArrayList<>(match));
        }
    }
}
