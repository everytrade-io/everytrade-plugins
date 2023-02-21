package io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean;

import io.everytrade.server.model.Currency;
import io.everytrade.server.model.TransactionType;
import io.everytrade.server.plugin.api.parser.FeeRebateImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.ImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.ParseResult;
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
import static io.everytrade.server.model.Currency.EUR;
import static io.everytrade.server.model.Currency.RUNE;
import static io.everytrade.server.model.Currency.SOL;
import static io.everytrade.server.model.Currency.USDC;
import static io.everytrade.server.model.Currency.USDT;
import static io.everytrade.server.model.Currency.UST;
import static io.everytrade.server.model.TransactionType.BUY;
import static io.everytrade.server.model.TransactionType.REBATE;
import static io.everytrade.server.model.TransactionType.SELL;
import static org.junit.jupiter.api.Assertions.assertEquals;

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
                EUR,
                SELL,
                new BigDecimal("9820.0000000000"),
                new BigDecimal("1.1310000000")
            ),
            List.of(
                new FeeRebateImportedTransactionBean(
                    null,
                    Instant.parse("2022-01-01T10:56:29Z"),
                    EUR,
                    EUR,
                    TransactionType.FEE,
                    new BigDecimal("11.10642"),
                    EUR
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
                "CARD CASHBACK",
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
                "CARD CASHBACK",
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
                "COMMISSION REBATE",
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
                "COMMISSION REBATE",
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
                new BigDecimal("6.2294428386"),
                "LARGE OTC TRADING",
                null
            ),
            List.of()
        );
        TestUtils.testTxs( expected.getMain(),actual.getMain());
    }

    @Test
    void testLargeOtcTradingBuy1() {
        final String row0 = "70366274,2022-08-08 12:22:18,Spot,Large OTC Trading,USDC,7524.81470366,\"\"\n";
        final String row1 = "70366274,2022-08-08 12:22:18,Spot,Large OTC Trading,USDT,-7523.83706360,\"\"\n";
        final String join = row0 + row1;

        final TransactionCluster actual = ParserTestUtils.getTransactionCluster(HEADER_CORRECT + join);

        final TransactionCluster expected = new TransactionCluster(
            new ImportedTransactionBean(
                null,
                Instant.parse("2022-08-08T12:22:18Z"),
                USDC,
                USDT,
                BUY,
                new BigDecimal("7524.8147036600"),
                new BigDecimal("0.9998700779"),
                "LARGE OTC TRADING",
                null
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
                new BigDecimal("0.1605280000"),
                "LARGE OTC TRADING",
                null
            ),
            List.of()
        );
        TestUtils.testTxs( expected.getMain(),actual.getMain());
    }

    @Test
    void testLargeOtcTradingConvert() {
        final String row0 = "70366274,2022-04-06 23:15:18,Spot,Large OTC Trading,RUNE,-1557.71867805,\"\"\n";
        final String row1 = "70366274,2022-04-06 23:15:18,Spot,Large OTC Trading,UST,14513.31689430,\"\"\n";
        final String join = row0 + row1;

        final TransactionCluster actual = ParserTestUtils.getTransactionCluster(HEADER_CORRECT + join);

        final TransactionCluster expected = new TransactionCluster(
            new ImportedTransactionBean(
                null,
                Instant.parse("2022-04-06T23:15:18Z"),
                RUNE,
                UST,
                SELL,
                new BigDecimal("1557.7186780500"),
                new BigDecimal("9.3170333635"),
                "LARGE OTC TRADING",
                null
            ),
            List.of()
        );
        TestUtils.testTxs( expected.getMain(),actual.getMain());
    }

    @Test
    void testSmallAssetsExchangeBuy() {
        final String row0 = "40360729,2020-11-24 15:44:58,Spot,Small assets exchange BNB,EUR,-0.00111400,\"\"\n";
        final String row1 = "40360729,2020-11-24 15:44:58,Spot,Small assets exchange BNB,BNB,0.00003908,\"\"\n";
        final String row2 = "40360729,2020-11-24 15:44:58,Spot,Small assets exchange BNB,USDT,-0.00350306,\"\"\n";
        final String row3 = "40360729,2020-11-24 15:44:58,Spot,Small assets exchange BNB,BNB,0.00010405,\"\"\n";
        final String join = row0 + row1 + row2 + row3;

        final List<TransactionCluster> actual = ParserTestUtils.getTransactionClusters(HEADER_CORRECT + join);

        final TransactionCluster expected1 = new TransactionCluster(
            new ImportedTransactionBean(
                null,
                Instant.parse("2020-11-24T15:44:58Z"),
                BNB,
                EUR,
                BUY,
                new BigDecimal("0.0000390800"),
                new BigDecimal("28.5056294780"),
                "SMALL ASSETS EXCHANGE BNB",
                null
            ),
            List.of()
        );

        final TransactionCluster expected2 = new TransactionCluster(
            new ImportedTransactionBean(
                null,
                Instant.parse("2020-11-24T15:44:58Z"),
                BNB,
                USDT,
                BUY,
                new BigDecimal("0.0001040500"),
                new BigDecimal("33.6670831331"),
                "SMALL ASSETS EXCHANGE BNB",
                null
                ),
            List.of()
        );
        TestUtils.testTxs( expected1.getMain(),actual.get(0).getMain());
        TestUtils.testTxs( expected2.getMain(),actual.get(1).getMain());
    }

    @Test
    void testSmallAssetsExchangeBuyWrongInputs() {
        final String row0 = "40360729,2020-11-24 15:44:58,Spot,Small assets exchange BNB,EUR,-0.00111400,\"\"\n";
        final String row1 = "40360729,2020-11-24 15:44:58,Spot,Small assets exchange BNB,BNB,0.00003908,\"\"\n";
        final String row2 = "40360729,2020-11-24 15:44:58,Spot,Small assets exchange BNB,USDT,-0.00350306,\"\"\n";
        final String join = row0 + row1 + row2;

        final ParseResult actual = ParserTestUtils.getParseResult(HEADER_CORRECT + join);

        final TransactionCluster expected1 = new TransactionCluster(
            new ImportedTransactionBean(
                null,
                Instant.parse("2020-11-24T15:44:58Z"),
                BNB,
                EUR,
                BUY,
                new BigDecimal("0.0000390800"),
                new BigDecimal("28.5056294780"),
                "SMALL ASSETS EXCHANGE BNB",
                null
            ),
            List.of()
        );

        final TransactionCluster expected2 = new TransactionCluster(
            new ImportedTransactionBean(
                null,
                Instant.parse("2020-11-24T15:44:58Z"),
                BNB,
                USDT,
                BUY,
                new BigDecimal("0.0001040500"),
                new BigDecimal("33.6670831331"),
                "SMALL ASSETS EXCHANGE BNB",
                null
                ),
            List.of()
        );
        assertEquals( 3,actual.getParsingProblems().size());
        assertEquals( "PARSED_ROW_IGNORED", actual.getParsingProblems().get(0).getParsingProblemType().name());
    }




}