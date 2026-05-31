package io.everytrade.server.plugin.impl.everytrade;

import io.everytrade.server.plugin.api.parser.TransactionCluster;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.ExchangeFactory;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.coinbase.v2.CoinbaseExchange;
import org.knowm.xchange.coinbase.cdp.service.CoinbaseTradeServiceCDP;
import org.knowm.xchange.coinbase.v2.service.CoinbaseTradeHistoryParams;
import org.knowm.xchange.coinbase.v2.service.CoinbaseTradeService;
import org.knowm.xchange.coinbase.v3.dto.transactions.CoinbaseAdvancedTradeFills;
import org.knowm.xchange.coinbase.v3.dto.transactions.CoinbaseAdvancedTradeOrderFillsResponse;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * ETD-2134 debug harness. NOT a unit test - hits the live Coinbase Advanced Trade API with real
 * credentials. Disabled by default; run explicitly:
 *   ./gradlew :plugin-base:test --tests "*CoinbaseAdvancedTradeRawDebugTest.dumpDashFills"
 *
 * Goal: prove WHERE the reported fill is lost. We page fills two ways and compare:
 *   (1) GROUND TRUTH  - pure cursor pagination over a fixed window (the way the Coinbase API
 *       intends). Collects every fill regardless of the downloader's home-grown time-window logic.
 *   (2) download()    - the production CoinbaseDownloader path, via the connector.
 * If a DASH fill exists in (1) but not in (2) -> bug is in the downloader paging, not the API.
 *
 * Output is restricted to DASH and to aggregate counts to avoid dumping the whole account.
 */
@EnabledIfEnvironmentVariable(named = "CBDEBUG", matches = "true") // run with CBDEBUG=true
class CoinbaseAdvancedTradeRawDebugTest {

    private static final String API_KEY =
        "organizations/da01f20a-0571-4dd4-a2fb-7c378a4fe336/apiKeys/4fc0d35f-31f6-4f08-b3c4-96978435e464";

    // EC private key. Stored with real newlines; the CDP JWT signer expects a valid PEM.
    private static final String API_SECRET = String.join("\n",
        "-----BEGIN EC PRIVATE KEY-----",
        "MHcCAQEEIG6U91CAnCXI7rS4tzv5keNWOUj1BSBh1d6m+6tzLhthoAoGCCqGSM49",
        "AwEHoUQDQgAEL3J2kv9BrqjnCZRj7eAbmrPxapgJJ85ZzGI6bXcJ4+Yd8uJzo1YF",
        "seMhH5wGS1MFHQTjZ5D5rSzxETgwZiGCxg==",
        "-----END EC PRIVATE KEY-----",
        "");

    private static final String ASSET = "DASH";
    private static final int PAGE_LIMIT = 100;
    private static final int MAX_PAGES = 500; // safety cap

    @Test
    void dumpDashFills() {
        var connector = new CoinbaseConnector(API_KEY, API_SECRET);
        // build our own exchange handle (connector.exchange is private) the same way the connector does
        final ExchangeSpecification exSpec = new CoinbaseExchange().getDefaultExchangeSpecification();
        exSpec.setApiKey(API_KEY);
        exSpec.setSecretKey(API_SECRET);
        Exchange exchange = ExchangeFactory.INSTANCE.createExchange(exSpec);

        // ---------- (1) GROUND TRUTH: pure cursor pagination, fixed window ----------
        List<CoinbaseAdvancedTradeFills> raw = pureCursorSweep(exchange);
        System.out.println("\n================ RAW API (pure cursor) ================");
        System.out.println("total fills returned by API: " + raw.size());
        printDash("RAW", raw);

        // ---------- (2) PRODUCTION PATH: CoinbaseDownloader.download(null) ----------
        var result = connector.getTransactions(null);
        var clusters = result.getParseResult().getTransactionClusters();
        System.out.println("\n================ download() parse result ================");
        System.out.println("total clusters: " + clusters.size()
            + ", parsing problems: " + result.getParseResult().getParsingProblems().size());
        long dashClusters = clusters.stream()
            .filter(c -> String.valueOf(c.getMain().getBase()).contains(ASSET))
            .count();
        System.out.println("DASH clusters from download(): " + dashClusters);
        clusters.stream()
            .filter(c -> String.valueOf(c.getMain().getBase()).contains(ASSET))
            .forEach(c -> System.out.println("  download DASH tx: uid=" + c.getMain().getUid()
                + " " + c.getMain().getAction()
                + " vol=" + c.getMain().getVolume()
                + " executed=" + c.getMain().getExecuted()));

        // surface parsing problems (show ROW content) and flag anything DASH / the missing 7.2 fill
        result.getParseResult().getParsingProblems().forEach(p -> {
            String row = String.valueOf(p.getRow());
            boolean dash = row.contains("DASH") || row.contains("812cf76c") || row.contains("7.2");
            System.out.println("  PARSING PROBLEM" + (dash ? " <<< DASH/7.2" : "")
                + " msg=" + p.getMessage() + " | row=" + row);
        });

        // ---------- (3) ISOLATE: replicate the downloader's end-narrowing + ms-truncation paging ----------
        List<CoinbaseAdvancedTradeFills> downloaderStyle = downloaderStyleSweep(exchange);
        System.out.println("\n================ downloader-style paging (end-narrowing + ms truncation) ================");
        System.out.println("total fills via downloader-style paging: " + downloaderStyle.size());
        boolean has72 = downloaderStyle.stream().anyMatch(f -> "812cf76c988853c5122199f4dae1d5a4dae275cbc838607ca0432faa56d7174c".equals(f.getEntryId()));
        boolean has134 = downloaderStyle.stream().anyMatch(f -> "33bcc44ff6180a4e93b525e7d90bc6d4766717adba73f33c2169b67ddd6a53e7".equals(f.getEntryId()));
        boolean has2018 = downloaderStyle.stream().anyMatch(f -> "74619ca8b99eb18edbfde2cbe53d805bea9501e7fc681f1acd5977e533b95259".equals(f.getEntryId()));
        System.out.println("  DASH fills present after downloader-style paging -> 1.34:" + has134 + " 2.018:" + has2018 + " 7.2:" + has72);
        printDash("DOWNLOADER-PAGING", downloaderStyle);
    }

    /** Replicates CoinbaseDownloader.downloadAdvancedTrade paging: narrows end to last fill's
     *  trade_time TRUNCATED TO MS, while also passing the cursor. Isolates paging-level loss. */
    private List<CoinbaseAdvancedTradeFills> downloaderStyleSweep(Exchange exchange) {
        var ts = exchange.getTradeService();
        List<CoinbaseAdvancedTradeFills> all = new ArrayList<>();
        Instant now = Instant.now();
        long partialEnd = 0;
        String cursor = null;
        int pages = 0;
        while (pages < MAX_PAGES) {
            CoinbaseTradeHistoryParams p = (CoinbaseTradeHistoryParams) ts.createTradeHistoryParams();
            p.setStartDatetime(Instant.ofEpochMilli(0));
            p.setEndDateTime(partialEnd == 0 ? now : Instant.ofEpochMilli(partialEnd));
            p.setLimit(PAGE_LIMIT);
            if (cursor != null && !cursor.equalsIgnoreCase("null")) {
                p.setCursor(cursor);
            }
            CoinbaseAdvancedTradeOrderFillsResponse resp;
            try {
                resp = ((CoinbaseTradeServiceCDP) ts).getAdvancedTradeOrderFillsRow(p);
            } catch (Exception e) {
                throw new IllegalStateException("downloader-style getFills failed page " + pages, e);
            }
            List<CoinbaseAdvancedTradeFills> block = resp.getFills();
            cursor = resp.getCursor();
            int size = block == null ? 0 : block.size();
            System.out.println("  dl-page " + (pages + 1) + ": +" + size
                + " end=" + p.getEndDateTime() + " cursor->" + cursor);
            if (size == 0) {
                break;
            } else if (size == PAGE_LIMIT) {
                String lastTradeTime = block.get(block.size() - 1).getTradeTime();
                partialEnd = truncateToMs(lastTradeTime);
                all.addAll(block);
            } else {
                all.addAll(block);
                break;
            }
            pages++;
        }
        return all;
    }

    /** mirror of CoinbaseDownloader.cleanDateText + createDateFromText (ms precision) */
    private long truncateToMs(String textDate) {
        try {
            String t = textDate.substring(0, textDate.lastIndexOf(".") + 4).replace("Z", "") + "Z";
            var fmt = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
            fmt.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
            return fmt.parse(t).getTime();
        } catch (Exception e) {
            throw new RuntimeException("date parse failed: " + textDate, e);
        }
    }

    private List<CoinbaseAdvancedTradeFills> pureCursorSweep(Exchange exchange) {
        var ts = exchange.getTradeService();
        CoinbaseTradeHistoryParams p = (CoinbaseTradeHistoryParams) ts.createTradeHistoryParams();
        p.setStartDatetime(Instant.ofEpochMilli(0));
        p.setEndDateTime(Instant.now());
        p.setLimit(PAGE_LIMIT);

        List<CoinbaseAdvancedTradeFills> all = new ArrayList<>();
        String cursor = null;
        int pages = 0;
        do {
            p.setCursor(cursor);
            CoinbaseAdvancedTradeOrderFillsResponse resp;
            try {
                if (ts instanceof CoinbaseTradeServiceCDP cdp) {
                    resp = cdp.getAdvancedTradeOrderFillsRow(p);
                } else if (ts instanceof CoinbaseTradeService legacy) {
                    resp = legacy.getAdvancedTradeOrderFillsRow(p);
                } else {
                    throw new IllegalStateException("Unexpected trade service: " + ts.getClass());
                }
            } catch (Exception e) {
                throw new IllegalStateException("getFills failed on page " + pages, e);
            }
            List<CoinbaseAdvancedTradeFills> block = resp.getFills();
            if (block != null) {
                all.addAll(block);
            }
            cursor = resp.getCursor();
            pages++;
            System.out.println("  page " + pages + ": +" + (block == null ? 0 : block.size())
                + " fills, cursor=" + cursor);
        } while (cursor != null && !cursor.isEmpty() && !cursor.equalsIgnoreCase("null") && pages < MAX_PAGES);
        return all;
    }

    private void printDash(String tag, List<CoinbaseAdvancedTradeFills> fills) {
        // group DASH fills by orderId, then by tradeId, to expose shared trade_id across fills
        Map<String, List<CoinbaseAdvancedTradeFills>> byOrder = new LinkedHashMap<>();
        for (CoinbaseAdvancedTradeFills f : fills) {
            if (f.getProductId() != null && f.getProductId().contains(ASSET)) {
                byOrder.computeIfAbsent(f.getOrderId(), k -> new ArrayList<>()).add(f);
            }
        }
        System.out.println("[" + tag + "] DASH fills: "
            + byOrder.values().stream().mapToInt(List::size).sum()
            + " across " + byOrder.size() + " orders");
        for (var e : byOrder.entrySet()) {
            System.out.println("  order " + e.getKey() + " -> " + e.getValue().size() + " fills:");
            Map<String, Integer> tradeIdCounts = new TreeMap<>();
            for (CoinbaseAdvancedTradeFills f : e.getValue()) {
                tradeIdCounts.merge(String.valueOf(f.getTradeId()), 1, Integer::sum);
                System.out.println("    entry_id=" + f.getEntryId()
                    + " trade_id=" + f.getTradeId()
                    + " side=" + f.getSide()
                    + " size=" + f.getSize()
                    + " price=" + f.getPrice()
                    + " product=" + f.getProductId()
                    + " size_in_quote=" + f.getSizeInQuote()
                    + " trade_time=" + f.getTradeTime());
            }
            tradeIdCounts.entrySet().stream()
                .filter(t -> t.getValue() > 1)
                .forEach(t -> System.out.println("    >>> trade_id " + t.getKey()
                    + " SHARED by " + t.getValue() + " fills (collision source)"));
        }
    }
}
