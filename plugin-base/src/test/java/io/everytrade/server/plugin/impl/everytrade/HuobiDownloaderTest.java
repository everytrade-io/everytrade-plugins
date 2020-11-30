package io.everytrade.server.plugin.impl.everytrade;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.trade.UserTrade;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;

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
        final String lastTx = String.format("LTC/USD=%s:::", LocalDate.now(ZoneOffset.UTC).minusDays(2));
        final HuobiDownloader huobiDownloader = new HuobiDownloader(tradeService, lastTx);
        final List<UserTrade> expected = List.of();
        final List<UserTrade> downloaded = huobiDownloader.download("LTC/USD");
        downloaded.sort(HuobiTestUtils::compareDesc);
        assertTrue(HuobiTestUtils.checkAll(expected, downloaded));
        final String expectedTx = String.format("LTC/USD=%s:::", LocalDate.now(ZoneOffset.UTC));
        final String actual = huobiDownloader.getLastTransactionId();
        assertEquals(actual, expectedTx);
    }

    @Test
    void downloadNoNewTxsStartOddDaysAgo()  {
        final List<UserTrade> userTrades = List.of();
        final HuobiTradeServiceMock tradeService = new HuobiTradeServiceMock(userTrades);
        final String lastTx = String.format("LTC/USD=%s:::", LocalDate.now(ZoneOffset.UTC).minusDays(3));
        final HuobiDownloader huobiDownloader = new HuobiDownloader(tradeService, lastTx);
        final List<UserTrade> expected = List.of();
        final List<UserTrade> downloaded = huobiDownloader.download("LTC/USD");
        downloaded.sort(HuobiTestUtils::compareDesc);
        assertTrue(HuobiTestUtils.checkAll(expected, downloaded));
        final String expectedTx = String.format("LTC/USD=%s:::", LocalDate.now(ZoneOffset.UTC).minusDays(1));
        final String actual = huobiDownloader.getLastTransactionId();
        assertEquals(actual, expectedTx);
    }

    @Test
    void downloadWithLastId()  {
        final List<UserTrade> userTrades = List.of(userTrade3, userTrade2, userTrade1);
        final HuobiTradeServiceMock tradeService = new HuobiTradeServiceMock(userTrades);
        final HuobiDownloader huobiDownloader
            = new HuobiDownloader(tradeService, String.format("LTC/USD=%s:1::", LocalDate.now(ZoneOffset.UTC)));
        final List<UserTrade> expected = List.of(userTrade3, userTrade2);
        final List<UserTrade> downloaded = huobiDownloader.download("LTC/USD");
        downloaded.sort(HuobiTestUtils::compareDesc);
        assertTrue(HuobiTestUtils.checkAll(expected, downloaded));
        final String expectedTx = String.format("LTC/USD=%s:3::", LocalDate.now(ZoneOffset.UTC));
        final String actual = huobiDownloader.getLastTransactionId();
        assertEquals(actual, expectedTx);
    }

    @Test
    void downloadWithGap()  {
        final List<UserTrade> userTrades = List.of(userTrade3, userTrade2, userTrade1);
        final HuobiTradeServiceMock tradeService = new HuobiTradeServiceMock(userTrades);
        final HuobiDownloader huobiDownloader
            = new HuobiDownloader(tradeService, String.format("LTC/USD=%s::2:3", LocalDate.now(ZoneOffset.UTC)));
        final List<UserTrade> expected = List.of(userTrade1);
        final List<UserTrade> downloaded = huobiDownloader.download("LTC/USD");
        downloaded.sort(HuobiTestUtils::compareDesc);
        assertTrue(HuobiTestUtils.checkAll(expected, downloaded));
        final String expectedTx = String.format("LTC/USD=%1$s:3::", LocalDate.now(ZoneOffset.UTC));
        final String actual = huobiDownloader.getLastTransactionId();
        assertEquals(actual, expectedTx);
    }

    @Test
    void downloadNoLastTxId()  {
        final List<UserTrade> userTrades = List.of(userTradeOldest);
        final HuobiTradeServiceMock tradeService = new HuobiTradeServiceMock(userTrades);
        final HuobiDownloader huobiDownloader
            = new HuobiDownloader(tradeService, null);
        final List<UserTrade> expected = tradeService.getUserTrades();
        final List<UserTrade> downloaded = huobiDownloader.download("LTC/USD");
        downloaded.sort(HuobiTestUtils::compareDesc);
        assertTrue(HuobiTestUtils.checkAll(expected, downloaded));
    }

    @Test
    void downloadTwoPairs()  {
        final List<UserTrade> userTrades = List.of(userTrade3, userTrade2, userTrade1, userTrade0);
        final HuobiTradeServiceMock tradeService = new HuobiTradeServiceMock(userTrades);
        final HuobiDownloader huobiDownloader = new HuobiDownloader(
            tradeService,
            String.format("LTC/USD=%1$s:3::|LTC/BTC=%1$s:0::", LocalDate.now(ZoneOffset.UTC).minusDays(2))
        );
        final List<UserTrade> expected = tradeService.getUserTrades();
        final List<UserTrade> downloaded = huobiDownloader.download("LTC/USD, LTC/BTC");
        downloaded.sort(HuobiTestUtils::compareDesc);
        assertTrue(HuobiTestUtils.checkAll(expected, downloaded));
        final String expectedTx = String.format("LTC/USD=%1$s:3::|LTC/BTC=%1$s:0::", LocalDate.now(ZoneOffset.UTC));
        final String actual = huobiDownloader.getLastTransactionId();
        assertEquals(actual, expectedTx);
    }
}