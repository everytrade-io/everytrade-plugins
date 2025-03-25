package io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean;

import io.everytrade.server.plugin.api.parser.ImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.TransactionCluster;
import io.everytrade.server.test.TestUtils;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static io.everytrade.server.model.Currency.AVAX;
import static io.everytrade.server.model.Currency.DOGE;
import static io.everytrade.server.model.Currency.KAS;
import static io.everytrade.server.model.Currency.USDT;
import static io.everytrade.server.model.TransactionType.BUY;
import static io.everytrade.server.model.TransactionType.SELL;

public class KuCoinBuySellTest {

    public static final String HEADER_BUY_SELL_V1 = "tradeCreatedAt;orderId;symbol;side;price;size;funds;fee;liquidity;feeCurrency;" +
        "orderType\n";
    public static final String HEADER_BUY_SELL_V2 = "UID,Account Type,Order ID,Symbol,Side,Order Type,Avg. Filled Price,Filled Amount," +
        "Filled Volume,Filled Volume (USDT),Filled Time(UTC+02:00),Fee,Maker/Taker,Fee Currency\n";
    public static final String HEADER_BUY_SELL_V3 = "UID,Account Type,Payment Account,Sell,Buy,Price,Time of Update(UTC+02:00),Status\n";

    @Test
    void testBuyHeaderV1() {
        final String row0 = "17.11.2021 10:21:26;;AVAX-USDT;buy;95,317;57,7348;5503,1079316;5,5031079316;taker;" +
            "USDT;limit\n";
        final var actual = ParserTestUtils.getParseResult(HEADER_BUY_SELL_V1 + row0);

        final TransactionCluster expected = new TransactionCluster(
            new ImportedTransactionBean(
                null,
                Instant.parse("2021-11-17T10:21:26Z"),
                AVAX,
                USDT,
                BUY,
                new BigDecimal("57.73480000000000000"),
                new BigDecimal("95.31700000000000000"),
                null,
                null
            ),
            List.of()
        );

        TestUtils.testTxs(expected.getMain(), actual.getTransactionClusters().get(0).getMain());
    }
    @Test
    void testBuyHeaderV2() {
        final String row0 = "****4228,mainAccount,,KAS-USDT,BUY,MARKET,0.14552,1548.3555,225.31669236,225" +
            ".31669236,2023-12-04 06:25:42,0.45063338472,TAKER,USDT\n";
        final var actual = ParserTestUtils.getParseResult(HEADER_BUY_SELL_V2 + row0);

        final TransactionCluster expected = new TransactionCluster(
            new ImportedTransactionBean(
                null,
                Instant.parse("2023-12-04T06:25:42Z"),
                KAS,
                USDT,
                BUY,
                new BigDecimal("1548.3555"),
                new BigDecimal("0.14552"),
                null,
                null
            ),
            List.of()
        );

        TestUtils.testTxs(expected.getMain(), actual.getTransactionClusters().get(0).getMain());
    }

    @Test
    void testSellHeaderV2() {
        final String row0 = "****4228,mainAccount,,DOGE-USDT,SELL,MARKET,0.09542,7634.811,728.51366562,728" +
            ".51366562,2023-12-07 01:16:13,0.72851366562,TAKER,USDT\n";
        final var actual = ParserTestUtils.getParseResult(HEADER_BUY_SELL_V2 + row0);

        final TransactionCluster expected = new TransactionCluster(
            new ImportedTransactionBean(
                null,
                Instant.parse("2023-12-07T01:16:13Z"),
                DOGE,
                USDT,
                SELL,
                new BigDecimal("7634.811"),
                new BigDecimal("0.09542"),
                null,
                null
            ),
            List.of()
        );

        TestUtils.testTxs(expected.getMain(), actual.getTransactionClusters().get(0).getMain());
    }

    @Test
    void testSellHeaderV3() {
        final String row0 = "****4228,mainAccount,TRADE,10285.1148 KAS,16272.9897 DOGE,1 KAS=1.5821884360493477 DOGE,2023-12-04 08:28:21," +
            "SUCCESS\n";
        final var actual = ParserTestUtils.getParseResult(HEADER_BUY_SELL_V3 + row0);

        final TransactionCluster expected = new TransactionCluster(
            new ImportedTransactionBean(
                null,
                Instant.parse("2023-12-04T08:28:21Z"),
                DOGE,
                KAS,
                BUY,
                new BigDecimal("16272.9897"),
                new BigDecimal("0.63203596816631675"),
                null,
                null
            ),
            List.of()
        );

        TestUtils.testTxs(expected.getMain(), actual.getTransactionClusters().get(0).getMain());
    }
}
