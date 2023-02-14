package io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean;

import io.everytrade.server.model.Currency;
import io.everytrade.server.model.TransactionType;
import io.everytrade.server.plugin.api.parser.FeeRebateImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.ImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.TransactionCluster;
import io.everytrade.server.test.TestUtils;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static io.everytrade.server.model.Currency.BNB;
import static io.everytrade.server.model.Currency.BTC;
import static io.everytrade.server.model.Currency.DOGE;
import static io.everytrade.server.model.Currency.ETH;
import static io.everytrade.server.model.Currency.SOL;
import static io.everytrade.server.model.Currency.USDT;
import static io.everytrade.server.model.TransactionType.BUY;
import static io.everytrade.server.model.TransactionType.REBATE;
import static io.everytrade.server.model.TransactionType.SELL;

class BinanceBeanV4Test {
    public static final String HEADER_CORRECT = "\uFEFFUser_ID,UTC_Time,Account,Operation,Coin,Change,Remark\n";

    @Test
    void testConvertSell() {
        final String row0 = "41438313,2022-01-01 10:56:29,Spot,Sell,BTC,76.65970000,\"\"\n";
        final String row1 = "41438313,2022-01-01 10:56:29,Spot,Fee,BNB,-0.00011168,\"\"\n";
        final String row2 = "41438313,2022-01-01 10:56:29,Spot,Sell,ETH,-13.00000000,\"\"\n";
        final String join = row0 + row1 + row2;

        final TransactionCluster actual = ParserTestUtils.getTransactionCluster(HEADER_CORRECT + join);

        final TransactionCluster expected = new TransactionCluster(
            new ImportedTransactionBean(
                null,
                Instant.parse("2022-01-01T10:56:29Z"),
                ETH,
                BTC,
                SELL,
                new BigDecimal("13.0000000000"),
                new BigDecimal("5.8969000000")
            ),
            List.of(
                new FeeRebateImportedTransactionBean(
                    null,
                    Instant.parse("2022-01-01T10:56:29Z"),
                    BNB,
                    BNB,
                    TransactionType.FEE,
                    new BigDecimal("0.00011168"),
                    BNB
                )
            )
        );
        TestUtils.testTxs( expected.getRelated().get(0),actual.getRelated().get(0));
        TestUtils.testTxs( expected.getMain(),actual.getMain());
    }

    @Test
    void testConvertSellFiatInBase() {
        final String row0 = "41438313,2022-01-01 10:56:29,Spot,Sell,EUR,11106.42,\"\"\n";
        final String row1 = "41438313,2022-01-01 10:56:29,Spot,Fee,EUR,-11.10642,\"\"\n";
        final String row2 = "41438313,2022-01-01 10:56:29,Spot,Sell,USDT,-9820,\"\"\n";
        final String join = row0 + row1 + row2;

        final TransactionCluster actual = ParserTestUtils.getTransactionCluster(HEADER_CORRECT + join);

        final TransactionCluster expected = new TransactionCluster(
            new ImportedTransactionBean(
                null,
                Instant.parse("2022-01-01T10:56:29Z"),
                Currency.USDT,
                Currency.EUR,
                SELL,
                new BigDecimal("9820.0000000000"),
                new BigDecimal("1.1310000000")
            ),
            List.of(
                new FeeRebateImportedTransactionBean(
                    null,
                    Instant.parse("2022-01-01T10:56:29Z"),
                    Currency.EUR,
                    Currency.EUR,
                    TransactionType.FEE,
                    new BigDecimal("11.10642"),
                    Currency.EUR
                )
            )
        );
        TestUtils.testTxs( expected.getRelated().get(0),actual.getRelated().get(0));
        TestUtils.testTxs( expected.getMain(),actual.getMain());
    }

    @Test
    void testCardCashback() {
        final String row0 = "\"38065325,2022-02-26 05:48:48,Card,Card Cashback,BNB,0.00066906,\"\"\"\"\"\n";
        final String row1 = "\"38065325,2022-02-26 05:48:48,Card,Card Cashback,BNB,0.00006234,\"\"\"\"\"\n";
        final String join = row0 + row1;

        final List<TransactionCluster> actual = ParserTestUtils.getTransactionClusters(HEADER_CORRECT + join);

        final TransactionCluster expected1 = new TransactionCluster(
            new ImportedTransactionBean(
                null,
                Instant.parse("2022-02-26T05:48:48Z"),
                BNB,
                BNB,
                REBATE,
                new BigDecimal("0.00066906"),
                null,
                "Card Cashback",
                null
            ),
            List.of()
        );
        final TransactionCluster expected2 = new TransactionCluster(
            new ImportedTransactionBean(
                null,
                Instant.parse("2022-02-26T05:48:48Z"),
                BNB,
                BNB,
                REBATE,
                new BigDecimal("0.00006234"),
                null,
                "Card Cashback",
                null
            ),
            List.of()
        );

        TestUtils.testTxs( expected1.getMain(),actual.get(0).getMain());
        TestUtils.testTxs( expected2.getMain(),actual.get(1).getMain());
    }

    @Test
    void testCommissionRebate() {
        final String row0 = "70366274,2022-08-10 13:25:58,Spot,Commission Rebate,SOL,0.00182000,\"\"\n";
        final String row1 = "70366274,2022-08-10 13:25:58,Spot,Commission Rebate,SOL,0.00220000,\"\"\n";
        final String join = row0 + row1;

        final List<TransactionCluster> actual = ParserTestUtils.getTransactionClusters(HEADER_CORRECT + join);

        final TransactionCluster expected1 = new TransactionCluster(
            new ImportedTransactionBean(
                null,
                Instant.parse("2022-08-10T13:25:58Z"),
                SOL,
                SOL,
                REBATE,
                new BigDecimal("0.00182000"),
                null,
                "Commission Rebate",
                null
            ),
            List.of()
        );


        final TransactionCluster expected2 = new TransactionCluster(
            new ImportedTransactionBean(
                null,
                Instant.parse("2022-08-10T13:25:58Z"),
                SOL,
                SOL,
                REBATE,
                new BigDecimal("0.00220000"),
                null,
                "Commission Rebate",
                null
            ),
            List.of()
        );
        TestUtils.testTxs( expected1.getMain(),actual.get(0).getMain());
        TestUtils.testTxs( expected2.getMain(),actual.get(1).getMain());
    }

    @Test
    void testConvertBuy() {
        final String row0 = "41438313,2022-01-01 10:58:15,Spot,Buy,BTC,-5896.73580000,\"\"\n";
        final String row1 = "41438313,2022-01-01 10:58:15,Spot,Fee,BNB,-0.00859660,\"\"\n";
        final String row2 = "41438313,2022-01-01 10:58:15,Spot,Buy,ETH,4477.40000000,\"\"\n";
        final String join = row0 + row1 + row2;

        final TransactionCluster actual = ParserTestUtils.getTransactionCluster(HEADER_CORRECT + join);

        final TransactionCluster expected = new TransactionCluster(
            new ImportedTransactionBean(
                null,
                Instant.parse("2022-01-01T10:58:15Z"),
                ETH,
                BTC,
                BUY,
                new BigDecimal("4477.4000000000"),
                new BigDecimal("1.3170000000")
            ),
            List.of(
                new FeeRebateImportedTransactionBean(
                    null,
                    Instant.parse("2022-01-01T10:58:15Z"),
                    BNB,
                    BNB,
                    TransactionType.FEE,
                    new BigDecimal("0.00859660"),
                    BNB
                )
            )
        );
        TestUtils.testTxs( expected.getRelated().get(0),actual.getRelated().get(0));
        TestUtils.testTxs( expected.getMain(),actual.getMain());
    }

    @Test
    void testLargeOtcTradingBuy() {
        final String row0 = "42138160,2022-01-12 18:00:24,Spot,Large OTC trading,DOGE,481.58400000,\"\"\n";
        final String row1 = "42138160,2022-01-12 18:00:24,Spot,Large OTC trading,USDT,-3000.00000000,\"\"\n";
        final String join = row0 + row1;

        final TransactionCluster actual = ParserTestUtils.getTransactionCluster(HEADER_CORRECT + join);

        final TransactionCluster expected = new TransactionCluster(
            new ImportedTransactionBean(
                null,
                Instant.parse("2022-01-12T18:00:24Z"),
                DOGE,
                USDT,
                BUY,
                new BigDecimal("481.5840000000"),
                new BigDecimal("6.2294428386")
            ),
            List.of()
        );
        TestUtils.testTxs( expected.getMain(),actual.getMain());
    }

    @Test
    void testLargeOtcTradingSell() {
        final String row0 = "42138160,2022-01-12 18:00:24,Spot,Large OTC trading,DOGE,-3000.00000000,\"\"\n";
        final String row1 = "42138160,2022-01-12 18:00:24,Spot,Large OTC trading,USDT,481.58400000,\"\"\n";
        final String join = row0 + row1;

        final TransactionCluster actual = ParserTestUtils.getTransactionCluster(HEADER_CORRECT + join);

        final TransactionCluster expected = new TransactionCluster(
            new ImportedTransactionBean(
                null,
                Instant.parse("2022-01-12T18:00:24Z"),
                DOGE,
                USDT,
                SELL,
                new BigDecimal("3000.0000000000"),
                new BigDecimal("0.1605280000")
            ),
            List.of()
        );
        TestUtils.testTxs( expected.getMain(),actual.getMain());
    }

}