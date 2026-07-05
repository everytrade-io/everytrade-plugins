package io.everytrade.server.plugin.impl.everytrade;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.everytrade.server.model.CurrencyPair;
import io.everytrade.server.plugin.api.parser.ImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.TransactionCluster;
import io.everytrade.server.util.CoinMateDataUtil;

import org.junit.jupiter.api.Test;
import org.knowm.xchange.coinmate.dto.trade.CoinmateTransactionHistoryEntry;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static io.everytrade.server.model.Currency.BTC;
import static io.everytrade.server.model.Currency.USD;
import static io.everytrade.server.model.TransactionType.BUY;
import static io.everytrade.server.model.TransactionType.DEPOSIT;
import static io.everytrade.server.model.TransactionType.SELL;
import static io.everytrade.server.model.TransactionType.WITHDRAWAL;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoinmateConnectorTest {

    private static final CurrencyPair PAIR = new CurrencyPair(BTC, USD);
    private static final String ADDRESS = "addrs0";

    private List<CoinmateTransactionHistoryEntry> coinMateDummyData() throws JsonProcessingException {
        String jsonString = "[{\"transactionType\":\"INSTANT_SELL\",\"amount\":-0.39615682,\"priceCurrency\":\"EUR\",\"orderId\"" +
            ":1983035523,\"price\":71.69,\"fee\":0.05112086,\"feeCurrency\":\"EUR\",\"transactionId\":10166112,\"amountCurrency\"" +
            ":\"LTC\",\"status\":\"OK\",\"timestamp\":1670417533892},{\"transactionType\":\"BUY\",\"amount\":0.62896312,\"" +
            "priceCurrency\":\"EUR\",\"orderId\":1983058061,\"price\":73.24,\"fee\":0.08291746,\"feeCurrency\":\"EUR\",\"" +
            "transactionId\":10166142,\"amountCurrency\":\"LTC\",\"status\":\"OK\",\"timestamp\":1670419065734}," +
            "{\"transactionType\":\"WITHDRAWAL\",\"amount\":-0.62896312,\"orderId\":0,\"fee\":0.0004,\"description\"" +
            ":\"LTC: 44e48a621f208658ffa89a98516b35fa46e417b11df014fc5c24860f7fc24ba4\",\"feeCurrency\":\"LTC\",\"transactionId\"" +
            ":10166143,\"amountCurrency\":\"LTC\",\"status\":\"COMPLETED\",\"timestamp\":1670419072976}," +
            "{\"transactionType\":\"BALANCE_MOVE_CREDIT\",\"amount\":0.005,\"transactionId\":10166200,\"amountCurrency\"" +
            ":\"BTC\",\"description\":\"Balance move from account: Account B\",\"status\":\"OK\",\"timestamp\":1670419080000}]";
        return List.of(new ObjectMapper().readValue(jsonString, CoinmateTransactionHistoryEntry[].class));
    }

    /**
     * The exchange's original transaction type must survive the mapping to our TransactionType:
     * it is stored in the note whenever it differs from the mapped type (QUICK_BUY/INSTANT_SELL/
     * BALANCE_MOVE_* ...), matching the behaviour of the Coinmate CSV parser.
     */
    @Test
    void testBuySellDepositWithdrawal() throws JsonProcessingException {
        List<TransactionCluster> clusters = new XChangeConnectorParser().getCoinMateResult(coinMateDummyData())
            .getTransactionClusters();
        assertEquals(4, clusters.size());

        ImportedTransactionBean instantSell = mainByUid(clusters, "10166112");
        assertEquals(SELL, instantSell.getAction());
        assertEquals("INSTANT_SELL", instantSell.getNote());

        ImportedTransactionBean plainBuy = mainByUid(clusters, "10166142");
        assertEquals(BUY, plainBuy.getAction());
        assertNull(plainBuy.getNote(), "original type equal to the mapped type must not clutter the note");

        ImportedTransactionBean withdrawal = mainByUid(clusters, "10166143");
        assertEquals(WITHDRAWAL, withdrawal.getAction());
        assertNull(withdrawal.getNote());

        ImportedTransactionBean balanceMove = mainByUid(clusters, "10166200");
        assertEquals(DEPOSIT, balanceMove.getAction());
        assertEquals("BALANCE_MOVE_CREDIT", balanceMove.getNote());
    }

    private static ImportedTransactionBean mainByUid(List<TransactionCluster> clusters, String uid) {
        return clusters.stream()
            .map(TransactionCluster::getMain)
            .filter(main -> uid.equals(main.getUid()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("Missing transaction " + uid));
    }

    @Test
    void testMapCoinMateType() {
        assertEquals(BUY, CoinMateDataUtil.mapCoinMateType("BUY"));
        assertEquals(BUY, CoinMateDataUtil.mapCoinMateType("INSTANT_BUY"));
        assertEquals(BUY, CoinMateDataUtil.mapCoinMateType("MARKET_BUY"));
        assertEquals(SELL, CoinMateDataUtil.mapCoinMateType("MARKET_SELL"));
        assertEquals(DEPOSIT, CoinMateDataUtil.mapCoinMateType("BALANCE_MOVE_CREDIT"));
        assertEquals(WITHDRAWAL, CoinMateDataUtil.mapCoinMateType("BALANCE_MOVE_DEBIT"));
    }

    /* ------------ DownloadState (private) is exercised through reflection ------------ */

    private static Object deserializeState(String state) throws Exception {
        Class<?> cls = Class.forName("io.everytrade.server.plugin.impl.everytrade.CoinmateConnector$DownloadState");
        Method m = cls.getDeclaredMethod("deserialize", String.class);
        m.setAccessible(true);
        return m.invoke(null, state);
    }

    private static String serializeState(Object state) throws Exception {
        Method m = state.getClass().getDeclaredMethod("serialize");
        m.setAccessible(true);
        return (String) m.invoke(state);
    }

    private static long field(Object state, String name) throws Exception {
        Field f = state.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return (long) f.get(state);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Long> importedIds(Object state) throws Exception {
        Field f = state.getClass().getDeclaredField("importedIds");
        f.setAccessible(true);
        return (Map<String, Long>) f.get(state);
    }

    @Test
    void testDownloadStateEmpty() throws Exception {
        Object state = deserializeState(null);
        assertEquals(0L, field(state, "nextFrom"));
        assertEquals(0L, field(state, "rescanFloor"));
        assertTrue(importedIds(state).isEmpty());
        assertEquals("v3=0=0=", serializeState(state));
    }

    @Test
    void testDownloadStateLegacySingleLong() throws Exception {
        // plain-long state written by the previous connector version: ids were not tracked yet,
        // so the rescan floor must equal the watermark to avoid re-importing (host has no dedup)
        Object state = deserializeState("1700000000000");
        assertEquals(1700000000000L, field(state, "nextFrom"));
        assertEquals(1700000000000L, field(state, "rescanFloor"));
        assertTrue(importedIds(state).isEmpty());
    }

    @Test
    void testDownloadStateOldestTxIdTimestampFormat() throws Exception {
        // the oldest observed production format: <transactionId>=<timestamp>
        Object state = deserializeState("12345678=1650000000000");
        assertEquals(1650000000000L, field(state, "nextFrom"));
        assertEquals(1650000000000L, field(state, "rescanFloor"));
    }

    @Test
    void testDownloadStateAncientTransactionIdOnly() throws Exception {
        // some ancient states hold only a Coinmate transaction id - no usable timestamp exists,
        // so the connector falls back to a full download instead of resuming from ~1970
        Object state = deserializeState("7654321");
        assertEquals(0L, field(state, "nextFrom"));
        assertEquals(0L, field(state, "rescanFloor"));
    }

    @Test
    void testDownloadStateLegacyMultiField() throws Exception {
        Object state = deserializeState("=0=1690000000000=0=0");
        assertEquals(1690000000000L, field(state, "nextFrom"));
        assertEquals(1690000000000L, field(state, "rescanFloor"));

        Object sixPart = deserializeState("=0=0=1640000000000=0=1710000000000");
        assertEquals(1710000000000L, field(sixPart, "nextFrom"));
    }

    @Test
    void testDownloadStateV3RoundTrip() throws Exception {
        Object state = deserializeState("v3=1700000000000=1700000300000=abc:1700000100000,def:1700000200000");
        assertEquals(1700000300000L, field(state, "nextFrom"));
        assertEquals(1700000000000L, field(state, "rescanFloor"));
        assertEquals(Long.valueOf(1700000100000L), importedIds(state).get("abc"));
        assertEquals(Long.valueOf(1700000200000L), importedIds(state).get("def"));

        Object reparsed = deserializeState(serializeState(state));
        assertEquals(1700000300000L, field(reparsed, "nextFrom"));
        assertEquals(1700000000000L, field(reparsed, "rescanFloor"));
        assertEquals(importedIds(state), importedIds(reparsed));
    }

    @Test
    void testV3StateIsReadableByPreviousParserVersion() throws Exception {
        // plugin ROLLBACK safety: the previous plugin version parses the state by splitting on '='
        // and taking max(parts[2], parts[5]) as the resume timestamp; the v3 field order is designed
        // so that this yields exactly nextFrom (instead of 0 = full re-download = mass duplicates)
        Object state = deserializeState("v3=1700000000000=1700000300000=11111111:1700000100000,22222222:1700000200000");
        String serialized = serializeState(state);

        String[] parts = serialized.split("=");
        long legacyTxFrom = parts.length > 2 ? Long.parseLong(parts[2]) : 0L;
        long legacyHighest = 0L;
        if (parts.length > 5) {
            try {
                legacyHighest = Long.parseLong(parts[5]);
            } catch (NumberFormatException ignored) {
            }
        }
        assertEquals(1700000300000L, Math.max(legacyTxFrom, legacyHighest),
            "previous parser must resume at nextFrom after a rollback");
    }

    @Test
    void testDownloadStateRememberImportedDropsOldIds() throws Exception {
        long nextFrom = 1_700_000_000_000L;
        long window30d = 30L * 24 * 60 * 60 * 1000;
        Object state = deserializeState("v3=0=" + nextFrom
            + "=old:" + (nextFrom - window30d - 1) + ",recent:" + (nextFrom - 1000));
        Method m = state.getClass().getDeclaredMethod("rememberImported", Map.class);
        m.setAccessible(true);
        m.invoke(state, Map.of("fresh", nextFrom - 500));
        // "old" fell out of the rescan window (can never be re-downloaded again) and is dropped
        assertEquals(Map.of("recent", nextFrom - 1000, "fresh", nextFrom - 500), importedIds(state));
    }
}
