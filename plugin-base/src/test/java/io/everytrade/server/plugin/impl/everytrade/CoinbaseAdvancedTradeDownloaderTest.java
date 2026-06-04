package io.everytrade.server.plugin.impl.everytrade;

import io.everytrade.server.plugin.api.parser.ParsingProblem;
import io.everytrade.server.test.mock.CoinbaseAdvancedTradeExchangeMock;
import io.everytrade.server.test.mock.CoinbaseAdvancedTradePagedExchangeMock;
import org.junit.jupiter.api.Test;
import org.knowm.xchange.coinbase.v3.dto.transactions.CoinbaseAdvancedTradeFills;
import org.knowm.xchange.dto.trade.UserTrade;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for ETD-2134 / ETS-4965 (comment 347666): a Coinbase Advanced Trade order filled in several
 * fills was importing incompletely - the largest fill was missing, corrupting balances/PnL.
 * <p>
 * Root cause (proven against the live API): the downloader paginated the fills endpoint by narrowing
 * the window end to the trade_time of the last fill on each page, TRUNCATED TO MILLISECONDS. Coinbase
 * trade_time has microsecond precision, so fills of one order sharing the same trade_time that fell
 * just past the truncated boundary were excluded by the next page's {@code end_sequence_timestamp} and
 * never downloaded. The fix paginates strictly by the API cursor over a fixed time window. See
 * {@link #pagedDownloadKeepsFillsSharingTradeTimeAcrossPageBoundary()}.
 * <p>
 * Independently, the downloader uses {@code entry_id} (unique per fill, with a fallback to
 * {@code trade_id}) as the {@link UserTrade} id so each fill maps to a unique host-side
 * {@code external_id} and cannot be collapsed as a duplicate - covered by the unit tests below.
 * <p>
 * All data here is synthetic - no real org id, e-mails, portfolio names or DB transactions are used.
 */
class CoinbaseAdvancedTradeDownloaderTest {

    private static final String PRODUCT_ID = "BTC-USD";
    // Several fills of one order may share a trade_id; entry_id is always unique per fill.
    private static final String SHARED_TRADE_ID = "trade-shared-1";

    /**
     * Builds a synthetic Coinbase Advanced Trade fill. Only the fields read by the downloader matter.
     */
    private static CoinbaseAdvancedTradeFills fill(String entryId, String tradeId, String side, String size, String price) {
        return fillAt(entryId, tradeId, side, size, price, "2024-01-02T03:04:05.123Z");
    }

    /** As {@link #fill} but with an explicit trade_time (microsecond precision allowed). */
    private static CoinbaseAdvancedTradeFills fillAt(String entryId, String tradeId, String side,
                                                     String size, String price, String tradeTime) {
        return new CoinbaseAdvancedTradeFills(
            entryId,                       // entry_id  (unique per fill)
            tradeId,                       // trade_id  (NOT necessarily unique per fill)
            "order-1",                     // order_id
            tradeTime,                     // trade_time
            "FILL",                        // trade_type
            new BigDecimal(price),         // price
            new BigDecimal(size),          // size
            new BigDecimal("0.01"),        // commission
            PRODUCT_ID,                    // product_id
            tradeTime,                     // sequence_timestamp
            "MAKER",                       // liquidity_indicator
            "false",                       // size_in_quote -> size is the base amount
            "synthetic-user",              // user_id
            side                           // side (BUY / SELL)
        );
    }

    @SuppressWarnings("unchecked")
    private static List<UserTrade> invokeCreateUserTrades(List<CoinbaseAdvancedTradeFills> fills,
                                                          List<ParsingProblem> parsingProblems) throws Exception {
        // createUserTradesFromAdvancedTrades is private; the mapping (entry_id vs trade_id) is the unit under test.
        var downloader = new CoinbaseDownloader(new CoinbaseAdvancedTradeExchangeMock(new ArrayList<>()));
        Method m = CoinbaseDownloader.class.getDeclaredMethod(
            "createUserTradesFromAdvancedTrades", List.class, List.class);
        m.setAccessible(true);
        return (List<UserTrade>) m.invoke(downloader, fills, parsingProblems);
    }

    // ---------------------------------------------------------------------------------------------
    // UNIT TEST - direct mapping of fills to UserTrades
    // ---------------------------------------------------------------------------------------------

    @Test
    void multipleFillsSharingTradeIdProduceUniqueIdsFromEntryId() throws Exception {
        // One order, three fills, all sharing the same trade_id but each with its own entry_id.
        List<CoinbaseAdvancedTradeFills> fills = List.of(
            fill("entry-A", SHARED_TRADE_ID, "BUY", "0.5", "40000"),
            fill("entry-B", SHARED_TRADE_ID, "BUY", "0.3", "40010"),
            fill("entry-C", SHARED_TRADE_ID, "BUY", "0.2", "40020")
        );
        List<ParsingProblem> problems = new ArrayList<>();

        List<UserTrade> trades = invokeCreateUserTrades(fills, problems);

        assertEquals(0, problems.size(), "No fill should be reported as a parsing problem");
        assertEquals(3, trades.size(), "Each fill must yield its own UserTrade");

        Set<String> ids = trades.stream().map(UserTrade::getId).collect(Collectors.toSet());
        assertEquals(3, ids.size(), "Each UserTrade id must be unique (taken from entry_id), nothing collapsed");
        assertTrue(ids.containsAll(Set.of("entry-A", "entry-B", "entry-C")),
            "UserTrade ids must come from entry_id, not the shared trade_id");
        assertTrue(trades.stream().noneMatch(t -> SHARED_TRADE_ID.equals(t.getId())),
            "When entry_id is present, the shared trade_id must NOT be used as the id");
    }

    @Test
    void fillWithoutEntryIdFallsBackToTradeId() throws Exception {
        // Fallback path: entry_id missing -> trade_id is used as the id.
        List<CoinbaseAdvancedTradeFills> fills = List.of(
            fill(null, "trade-fallback-empty-null", "SELL", "1.0", "41000"),
            fill("", "trade-fallback-empty-string", "SELL", "2.0", "41000")
        );
        List<ParsingProblem> problems = new ArrayList<>();

        List<UserTrade> trades = invokeCreateUserTrades(fills, problems);

        assertEquals(0, problems.size());
        assertEquals(2, trades.size());

        Set<String> ids = trades.stream().map(UserTrade::getId).collect(Collectors.toSet());
        assertEquals(Set.of("trade-fallback-empty-null", "trade-fallback-empty-string"), ids,
            "With entry_id missing/empty, the id must fall back to trade_id");
    }

    @Test
    void mixedFillsKeepEveryFillUnique() throws Exception {
        // Mixed batch: shared trade_id with entry_ids + one fallback fill. All must survive with unique ids.
        List<CoinbaseAdvancedTradeFills> fills = List.of(
            fill("entry-1", SHARED_TRADE_ID, "BUY", "0.10", "39000"),
            fill("entry-2", SHARED_TRADE_ID, "BUY", "0.20", "39000"),
            fill(null, "trade-standalone", "BUY", "0.30", "39000")
        );
        List<ParsingProblem> problems = new ArrayList<>();

        List<UserTrade> trades = invokeCreateUserTrades(fills, problems);

        assertEquals(0, problems.size());
        Set<String> ids = trades.stream().map(UserTrade::getId).collect(Collectors.toSet());
        assertEquals(3, ids.size(), "All three fills must remain distinct");
        assertEquals(Set.of("entry-1", "entry-2", "trade-standalone"), ids);
    }

    // ---------------------------------------------------------------------------------------------
    // INTEGRATION TEST - full download run through the mocked exchange and the host parser chain
    // (CoinbaseDownloader.download -> paging loop -> createUserTradesFromAdvancedTrades ->
    //  XChangeConnectorParser.getCoinbaseParseResult -> TransactionClusters)
    // ---------------------------------------------------------------------------------------------

    @Test
    void downloadImportsEveryFillAsSeparateTransaction() {
        // Two fills of the same order, sharing trade_id, distinct entry_id -> two transaction clusters.
        List<CoinbaseAdvancedTradeFills> fills = List.of(
            fill("entry-int-1", SHARED_TRADE_ID, "BUY", "0.5", "40000"),
            fill("entry-int-2", SHARED_TRADE_ID, "BUY", "0.5", "40000")
        );

        var downloader = new CoinbaseDownloader(new CoinbaseAdvancedTradeExchangeMock(fills));
        var result = downloader.download(null);

        assertEquals(0, result.getParseResult().getParsingProblems().size(),
            "No fill should be dropped or reported as a parsing problem");
        assertEquals(2, result.getParseResult().getTransactionClusters().size(),
            "Both fills must be imported as separate transactions (no duplicate-id collapse)");

        // The host external_id is derived from the cluster uid, which comes from UserTrade.getId() (entry_id).
        Set<String> uids = result.getParseResult().getTransactionClusters().stream()
            .map(c -> c.getMain().getUid())
            .collect(Collectors.toSet());
        assertEquals(2, uids.size(), "Each imported transaction must have a unique external_id (uid)");
        assertTrue(uids.containsAll(Set.of("entry-int-1", "entry-int-2")),
            "external_id (uid) must be the unique entry_id of each fill");
    }
    @Test
    void reproduceIssueTransaction() throws Exception {
        // Reproduces a real fill (anonymized shape):
        // UserTrade[type=ASK, originalAmount=0.08685, instrument=CBETH/USDC, price=3100,
        //   timestamp=Wed Jun 11 00:07:01 CEST 2025,
        //   id=91c87bda7e571da1055784eeb659fbc0f626747a5f99bfc4064c8c792cfecde3,
        //   orderId='31360a37-0274-4996-9b6a-2e749f10ae91', feeAmount=1.61541, feeCurrency='USDC',
        //   orderUserReference='e6de4bbc-0208-5293-98c7-1c2c846cd14f'

        // Product ID should be CBETH-USDC based on the instrument in description
        String productId = "CBETH-USDC";

        // Simulating the case where sizeInQuote is "true" (as seen in some market orders)
        // But Coinbase Advanced Trade API 'fills' endpoint 'size' is always base amount.
        CoinbaseAdvancedTradeFills fill = new CoinbaseAdvancedTradeFills(
            "91c87bda7e571da1055784eeb659fbc0f626747a5f99bfc4064c8c792cfecde3", // entry_id (matches id in description)
            "trade-id",
            "31360a37-0274-4996-9b6a-2e749f10ae91", // order_id
            "2025-06-11T00:07:01.000Z", // trade_time
            "FILL",
            new BigDecimal("3100"), // price
            new BigDecimal("0.08685"), // size
            new BigDecimal("1.61541"), // commission (feeAmount)
            productId,
            "2025-06-11T00:07:01.000Z",
            "TAKER",
            "true",
            "e6de4bbc-0208-5293-98c7-1c2c846cd14f", // user_id (using orderUserReference)
            "SELL" // ASK -> SELL
        );

        List<ParsingProblem> problems = new ArrayList<>();
        List<UserTrade> trades = invokeCreateUserTrades(List.of(fill), problems);

        assertEquals(0, problems.size(), "Should not have parsing problems");
        assertEquals(1, trades.size());
        UserTrade trade = trades.get(0);

        // Even with sizeInQuote=true, the 'size' field in fills should be base amount.
        assertEquals(new BigDecimal("0.08685"), trade.getOriginalAmount(), "Amount should match the size from API directly");
        assertEquals(new BigDecimal("3100"), trade.getPrice());
    }

    // ---------------------------------------------------------------------------------------------
    // REGRESSION TEST - the real ETS-4965 defect: fills sharing a trade_time across a page boundary
    // were dropped because the downloader narrowed the window end to a millisecond-truncated boundary.
    // The mock faithfully emulates end_sequence_timestamp filtering + cursor paging, so this test
    // FAILS against the old millisecond-narrowing paging and PASSES with the cursor-driven fix.
    // ---------------------------------------------------------------------------------------------

    @Test
    void pagedDownloadKeepsFillsSharingTradeTimeAcrossPageBoundary() {
        // Microsecond-precise trade_time of the multi-fill order (mirrors the real DASH order in ETS-4965).
        final String boundaryTime = "2025-11-04T10:33:21.097978Z";

        List<CoinbaseAdvancedTradeFills> fills = new ArrayList<>();
        // 99 NEWER fills (one full page minus one) so the page-1 boundary lands on the shared trade_time.
        for (int i = 0; i < 99; i++) {
            String t = String.format("2025-11-05T00:%02d:%02d.000000Z", i / 60, i % 60);
            fills.add(fillAt("newer-" + i, "trade-newer-" + i, "BUY", "0.10", "40000", t));
        }
        // Two fills of the SAME order at the SAME microsecond trade_time - one ends page 1, the other
        // begins page 2. The second one is exactly what went missing against the live API.
        fills.add(fillAt("boundary-keep", "trade-shared-boundary", "SELL", "1.34", "147.81", boundaryTime));
        fills.add(fillAt("boundary-LOST", "trade-shared-boundary", "SELL", "7.20", "147.84", boundaryTime));
        // 49 OLDER fills (reachable only via the cursor on page 2).
        for (int i = 0; i < 49; i++) {
            String t = String.format("2025-11-03T00:%02d:%02d.000000Z", i / 60, i % 60);
            fills.add(fillAt("older-" + i, "trade-older-" + i, "BUY", "0.10", "40000", t));
        }

        var downloader = new CoinbaseDownloader(new CoinbaseAdvancedTradePagedExchangeMock(fills));
        var result = downloader.download(null);

        Set<String> uids = result.getParseResult().getTransactionClusters().stream()
            .map(c -> c.getMain().getUid())
            .collect(Collectors.toSet());

        assertTrue(uids.contains("boundary-keep"),
            "The first fill at the shared trade_time must be imported");
        assertTrue(uids.contains("boundary-LOST"),
            "REGRESSION (ETS-4965): the second fill sharing the same microsecond trade_time across the "
                + "page boundary must NOT be dropped by the paging");
        assertEquals(150, result.getParseResult().getTransactionClusters().size(),
            "Every synthetic fill must be imported exactly once");
        assertEquals(0, result.getParseResult().getParsingProblems().size(),
            "No fill should be reported as a parsing problem");
    }

    // ---------------------------------------------------------------------------------------------
    // STATE / RESUME TESTS - the pagination fix must not break the persisted lastDownloadState
    // contract of existing containers. The state string FORMAT and the finalize semantics are
    // unchanged; these tests lock that down.
    // ---------------------------------------------------------------------------------------------

    private static final String WALLET_ID = "00000000-0000-0000-0000-000000000000"; // matches the mock wallet
    private static final String LEGACY_WALLET_PART = WALLET_ID + ":-:-:-:-:-:-";

    private static List<CoinbaseAdvancedTradeFills> threeFills() {
        return List.of(
            fillAt("s-1", "t-1", "BUY", "0.5", "40000", "2025-01-01T00:00:01.000000Z"),
            fillAt("s-2", "t-2", "SELL", "0.3", "41000", "2025-01-01T00:00:02.000000Z"),
            fillAt("s-3", "t-3", "BUY", "0.2", "42000", "2025-01-01T00:00:03.000000Z")
        );
    }

    @Test
    void resumeFromLegacyCompletedStateImportsAndDoesNotBreak() {
        // A legacy completed state produced by the OLD connector: partialStart>0, partialEnd=0,
        // completedLast=0, trailing-empty cursor. Must be readable and resume as an incremental download.
        String legacyCompletedState = LEGACY_WALLET_PART + "&1700000000000:0:0:";

        var downloader = new CoinbaseDownloader(new CoinbaseAdvancedTradePagedExchangeMock(threeFills()));
        var result = assertDoesNotThrow(() -> downloader.download(legacyCompletedState));

        assertEquals(0, result.getParseResult().getParsingProblems().size());
        assertEquals(3, result.getParseResult().getTransactionClusters().size(),
            "Resuming from a legacy completed state must still import the fills");
        assertNotNull(result.getDownloadStateData());
    }

    @Test
    void legacyStateWithoutAdvancedTradePartTriggersFullDownload() {
        // Pre-advanced-trade connector versions stored only the wallet part (no '&...'). The new code
        // must treat this as a brand-new advanced-trade download, not crash.
        var downloader = new CoinbaseDownloader(new CoinbaseAdvancedTradePagedExchangeMock(threeFills()));
        var result = assertDoesNotThrow(() -> downloader.download(LEGACY_WALLET_PART));

        assertEquals(0, result.getParseResult().getParsingProblems().size());
        assertEquals(3, result.getParseResult().getTransactionClusters().size(),
            "A state with no advanced-trade part must trigger a full advanced-trade download");
    }

    @Test
    void downloadStateRoundTripsAndKeepsTheSameFormat() {
        var downloader1 = new CoinbaseDownloader(new CoinbaseAdvancedTradePagedExchangeMock(threeFills()));
        var first = downloader1.download(null);

        String state = first.getDownloadStateData();
        assertNotNull(state);
        assertTrue(state.contains("&"), "State must contain the advanced-trade separator");

        // advanced-trade part: partialStart:partialEnd:completedLast:cursor
        String advancedPart = state.substring(state.indexOf('&') + 1);
        String[] adv = advancedPart.split(":");
        assertTrue(adv.length >= 3, "Advanced-trade state must keep its colon-separated shape");
        assertEquals("0", adv[1], "partialEnd must be reset to 0 after a completed drain");
        assertEquals("0", adv[2], "completedLast must be reset to 0 after a completed drain");

        // Feeding the produced state back in must resume cleanly (no exception, no lost data).
        var downloader2 = new CoinbaseDownloader(new CoinbaseAdvancedTradePagedExchangeMock(threeFills()));
        var second = assertDoesNotThrow(() -> downloader2.download(state));
        assertEquals(0, second.getParseResult().getParsingProblems().size());
        assertEquals(3, second.getParseResult().getTransactionClusters().size(),
            "Resuming from the freshly produced state must still import the fills");
    }
}
