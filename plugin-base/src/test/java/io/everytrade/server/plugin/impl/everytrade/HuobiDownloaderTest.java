package io.everytrade.server.plugin.impl.everytrade;

import io.everytrade.server.test.mock.HuobiExchangeMock;
import io.everytrade.server.test.mock.HuobiTradeServiceMock;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.marketdata.Trades;
import org.knowm.xchange.dto.trade.UserTrade;
import org.knowm.xchange.dto.trade.UserTrades;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.util.Collections.emptyList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HuobiDownloaderTest {

    private static UserTrade userTrade0;
    private static UserTrade userTrade1;
    private static UserTrade userTrade2;
    private static UserTrade userTrade3;
    private static UserTrade userTradeOldest;

    @BeforeAll
    static void prepareUserTrades() {
        final Instant now = Instant.now();
        userTrade3 = HuobiTestUtils.createUserTrade("3", Date.from(now), CurrencyPair.LTC_USD);
        userTrade2 = HuobiTestUtils.createUserTrade("2", Date.from(now.minusSeconds(1)), CurrencyPair.LTC_USD);
        userTrade1 = HuobiTestUtils.createUserTrade("1", Date.from(now.minusSeconds(2)), CurrencyPair.LTC_USD);
        userTrade0 = HuobiTestUtils.createUserTrade("0", Date.from(now.minusSeconds(3)), CurrencyPair.LTC_BTC);
        userTradeOldest = HuobiTestUtils.createUserTrade(
            "0", Date.from(now.minus(HuobiDownloadState.MAX_TRANSACTION_HISTORY_PERIOD)), CurrencyPair.LTC_USD
        );
    }

    @Test
    void downloadNoNewTxsStartEvenDaysAgo()  {
        final List<UserTrade> userTrades = List.of();
        final HuobiTradeServiceMock tradeService = new HuobiTradeServiceMock(userTrades);
        Map<String, HuobiDownloadState> state = HuobiDownloadState.deserializeState(
            String.format("LTC/USD=%s:::", LocalDate.now(ZoneOffset.UTC).minusDays(2))
        );
        final HuobiDownloader huobiDownloader = new HuobiDownloader(new HuobiExchangeMock(
            new UserTrades(userTrades, Trades.TradeSortType.SortByTimestamp), emptyList()));
        final List<UserTrade> expected = List.of();
        final List<UserTrade> downloaded = huobiDownloader.downloadTrades("LTC/USD", state);
        downloaded.sort(HuobiTestUtils::compareDesc);
        assertTrue(HuobiTestUtils.checkAll(expected, downloaded));
        final String expectedTx = "LTC/USD="+LocalDate.now(ZoneOffset.UTC).minusDays(2)+":::|ltcusd="+state.get("ltcusd");
        final String actual = HuobiDownloadState.serializeState(state);
        assertEquals(expectedTx, actual);
    }

    @Test
    void downloadNoNewTxsStartOddDaysAgo()  {
        final List<UserTrade> userTrades = List.of();
        final String lastTx = String.format("LTC/USD=%s:::", LocalDate.now(ZoneOffset.UTC).minusDays(3));
        Map<String, HuobiDownloadState> state = HuobiDownloadState.deserializeState(lastTx);
        final HuobiDownloader huobiDownloader = new HuobiDownloader(new HuobiExchangeMock(
            new UserTrades(userTrades, Trades.TradeSortType.SortByTimestamp), emptyList()));
        final List<UserTrade> expected = List.of();
        final List<UserTrade> downloaded = huobiDownloader.downloadTrades("LTC/USD", state);
        downloaded.sort(HuobiTestUtils::compareDesc);
        assertTrue(HuobiTestUtils.checkAll(expected, downloaded));
        final String expectedTx = "LTC/USD="+LocalDate.now(ZoneOffset.UTC).minusDays(3)+":::|ltcusd="+state.get("ltcusd");
        final String actual = HuobiDownloadState.serializeState(state);
        assertEquals(expectedTx, actual);
    }

    @Test
    void downloadWithLastId()  {
        final List<UserTrade> userTrades = List.of(userTrade3, userTrade2);
        Map<String, HuobiDownloadState> state = HuobiDownloadState.deserializeState(
            String.format("LTC/USD=%s:1::", LocalDate.now(ZoneOffset.UTC))
        );
        final HuobiDownloader huobiDownloader = new HuobiDownloader(new HuobiExchangeMock(
            new UserTrades(userTrades, Trades.TradeSortType.SortByTimestamp), emptyList()));
        final List<UserTrade> expected = List.of(userTrade3, userTrade2);
        final List<UserTrade> downloaded = huobiDownloader.downloadTrades("LTC/USD", state);
        downloaded.sort(HuobiTestUtils::compareDesc);
        assertTrue(HuobiTestUtils.checkAll(expected, downloaded));
        final String expectedTx = "LTC/USD="+LocalDate.now(ZoneOffset.UTC)+":1::|ltcusd=" + state.get("ltcusd");
        final String actual = HuobiDownloadState.serializeState(state);
        assertEquals(expectedTx, actual);
    }

    @Test
    void downloadWithGap()  {
        final List<UserTrade> userTrades = List.of(userTrade1);
        Map<String, HuobiDownloadState> state = HuobiDownloadState.deserializeState(
            String.format("LTC/USD=%s::2:3", LocalDate.now(ZoneOffset.UTC))
        );
        final HuobiDownloader huobiDownloader = new HuobiDownloader(new HuobiExchangeMock(
            new UserTrades(userTrades, Trades.TradeSortType.SortByTimestamp), emptyList()));
        final List<UserTrade> expected = List.of(userTrade1);
        final List<UserTrade> downloaded = huobiDownloader.downloadTrades("LTC/USD", state);
        downloaded.sort(HuobiTestUtils::compareDesc);
        assertTrue(HuobiTestUtils.checkAll(expected, downloaded));
        final String expectedTx = "LTC/USD="+LocalDate.now(ZoneOffset.UTC)+"::2:3|ltcusd=" + state.get("ltcusd");
        final String actual = HuobiDownloadState.serializeState(state);
        assertEquals(expectedTx, actual);
    }

    @Test
    void downloadNoLastTxId()  {
        final List<UserTrade> userTrades = List.of(userTradeOldest);
        final HuobiDownloader huobiDownloader = new HuobiDownloader(new HuobiExchangeMock(
            new UserTrades(userTrades, Trades.TradeSortType.SortByTimestamp), emptyList()));
        final List<UserTrade> expected = List.copyOf(userTrades);
        final List<UserTrade> downloaded = huobiDownloader.downloadTrades("LTC/USD", new HashMap<>());
        downloaded.sort(HuobiTestUtils::compareDesc);
        assertTrue(HuobiTestUtils.checkAll(expected, downloaded));
    }

    @Test
    void downloadTwoPairs()  {
        final List<UserTrade> userTrades = List.of(userTrade3, userTrade2, userTrade1, userTrade0);
        final HuobiTradeServiceMock tradeService = new HuobiTradeServiceMock(userTrades);
        Map<String, HuobiDownloadState> state = HuobiDownloadState.deserializeState(
            String.format("LTC/USD=%1$s:3::|LTC/BTC=%1$s:0::", LocalDate.now(ZoneOffset.UTC).minusDays(2))
        );
        final HuobiDownloader huobiDownloader = new HuobiDownloader(new HuobiExchangeMock(
            new UserTrades(userTrades, Trades.TradeSortType.SortByTimestamp), emptyList()));
        final List<UserTrade> expected = tradeService.getUserTrades();
        final List<UserTrade> downloaded = huobiDownloader.downloadTrades("LTC/USD, LTC/BTC", state);
        downloaded.sort(HuobiTestUtils::compareDesc);
        assertTrue(HuobiTestUtils.checkAll(expected, downloaded));
        final String expectedTx = "LTC/USD="+LocalDate.now(ZoneOffset.UTC).minusDays(2)+":3::|" +
            "ltcusd="+state.get("ltcusd")+"|LTC/BTC="+LocalDate.now(ZoneOffset.UTC).minusDays(2)+":0::|ltcbtc="+state.get("ltcbtc");
        final String actual = HuobiDownloadState.serializeState(state);
        assertEquals(expectedTx, actual);
    }
}
