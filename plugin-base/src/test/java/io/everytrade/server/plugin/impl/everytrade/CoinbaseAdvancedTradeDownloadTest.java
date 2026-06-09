package io.everytrade.server.plugin.impl.everytrade;

import io.everytrade.server.plugin.api.parser.TransactionCluster;
import io.everytrade.server.test.mock.CoinbaseAdvancedTradeCursorPagesMock;
import org.junit.jupiter.api.Test;
import org.knowm.xchange.coinbase.v3.dto.transactions.CoinbaseAdvancedTradeFills;
import org.knowm.xchange.coinbase.v3.dto.transactions.CoinbaseAdvancedTradeOrderFillsResponse;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static java.math.BigDecimal.ZERO;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Cursor-paging tests for {@link CoinbaseDownloader} driven by hand-built cursor pages
 * (no API key, no network). They lock in the ETD-2134 / ETS-4965 fix: the advanced-trade fills loop is
 * driven purely by the response cursor over a fixed window, so it drains every page (no under-fetch),
 * stops on an empty cursor, and is protected against a malformed, non-advancing cursor.
 */
class CoinbaseAdvancedTradeDownloadTest {

    // entryId is used as the host-side uid (the downloader keys UserTrade ids off entry_id).
    private static CoinbaseAdvancedTradeFills fill(String entryId, String side, String size, String price) {
        return new CoinbaseAdvancedTradeFills(
            entryId,                   // entry_id (-> unique uid)
            "trade-" + entryId,        // trade_id
            "order-1",                 // order_id
            "2024-01-01T10:00:00.000Z",// trade_time
            "FILL",                    // trade_type
            new BigDecimal(price),     // price
            new BigDecimal(size),      // size
            ZERO,                      // commission
            "BTC-USD",                 // product_id
            "2024-01-01T10:00:00.000Z",// sequence_timestamp
            "MAKER",                   // liquidity_indicator
            "false",                   // size_in_quote -> size is the base amount
            "synthetic-user",          // user_id
            side                       // side BUY/SELL
        );
    }

    private static CoinbaseAdvancedTradeOrderFillsResponse page(String cursor, CoinbaseAdvancedTradeFills... fills) {
        return new CoinbaseAdvancedTradeOrderFillsResponse(List.of(fills), cursor);
    }

    private static List<TransactionCluster> download(List<CoinbaseAdvancedTradeOrderFillsResponse> pages) {
        var connector = new CoinbaseConnector(new CoinbaseAdvancedTradeCursorPagesMock(pages));
        var result = connector.getTransactions(null);
        assertEquals(0, result.getParseResult().getParsingProblems().size(), "fills should parse cleanly");
        return result.getParseResult().getTransactionClusters();
    }

    @Test
    void drainsAllCursorPagesEvenWhenPagesAreSmallerThanRequestLimit() {
        // Each page carries fewer rows than the 100 request limit but a NON-empty cursor, except the
        // last. The old size-based logic stopped after the first short page; the cursor-based logic
        // must keep going until the cursor is empty and return every fill.
        var pages = List.of(
            page("cursor-1", fill("e1", "BUY", "0.10", "20000"), fill("e2", "BUY", "0.20", "20000")),
            page("cursor-2", fill("e3", "SELL", "0.30", "21000")),
            page("", fill("e4", "BUY", "0.40", "22000"))
        );

        var clusters = download(pages);

        assertEquals(4, clusters.size(), "all fills across all cursor pages must be imported");
        Set<String> uids = clusters.stream().map(c -> c.getMain().getUid()).collect(Collectors.toSet());
        assertEquals(4, uids.size(), "every imported fill must keep its own unique id (no duplicates, no loss)");
        assertTrue(uids.containsAll(Set.of("e1", "e2", "e3", "e4")));
    }

    @Test
    void stopsOnEmptyCursor() {
        var clusters = download(List.of(page("", fill("e1", "BUY", "0.10", "20000"))));
        assertEquals(1, clusters.size());
    }

    @Test
    void emptyFirstPageProducesNoTradesButValidState() {
        var connector = new CoinbaseConnector(new CoinbaseAdvancedTradeCursorPagesMock(List.of(page(""))));
        var result = connector.getTransactions(null);

        assertEquals(0, result.getParseResult().getTransactionClusters().size());
        assertNotNull(result.getDownloadStateData(), "a valid resume state must still be produced");
    }

    @Test
    void noProgressGuardStopsWhenCursorDoesNotAdvance() {
        // A malformed source that keeps returning the same non-empty cursor must not spin forever:
        // the guard breaks once the returned cursor equals the one just sent.
        var stuck = page("stuck", fill("e1", "BUY", "0.10", "20000"));
        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
            var clusters = download(List.of(stuck));
            assertTrue(clusters.size() <= 2, "loop must terminate after the cursor stops advancing");
        });
    }
}
