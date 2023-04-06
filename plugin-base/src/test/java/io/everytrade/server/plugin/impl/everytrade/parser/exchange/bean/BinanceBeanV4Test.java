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

import static io.everytrade.server.model.Currency.ADA;
import static io.everytrade.server.model.Currency.ALGO;
import static io.everytrade.server.model.Currency.BNB;
import static io.everytrade.server.model.Currency.BTC;
import static io.everytrade.server.model.Currency.BUSD;
import static io.everytrade.server.model.Currency.CZK;
import static io.everytrade.server.model.Currency.DOGE;
import static io.everytrade.server.model.Currency.DOT;
import static io.everytrade.server.model.Currency.ETH;
import static io.everytrade.server.model.Currency.EUR;
import static io.everytrade.server.model.Currency.NEAR;
import static io.everytrade.server.model.Currency.REEF;
import static io.everytrade.server.model.Currency.RUNE;
import static io.everytrade.server.model.Currency.SHIB;
import static io.everytrade.server.model.Currency.SOL;
import static io.everytrade.server.model.Currency.USD;
import static io.everytrade.server.model.Currency.USDC;
import static io.everytrade.server.model.Currency.USDT;
import static io.everytrade.server.model.Currency.UST;
import static io.everytrade.server.model.TransactionType.BUY;
import static io.everytrade.server.model.TransactionType.DEPOSIT;
import static io.everytrade.server.model.TransactionType.EARNING;
import static io.everytrade.server.model.TransactionType.FEE;
import static io.everytrade.server.model.TransactionType.REBATE;
import static io.everytrade.server.model.TransactionType.REWARD;
import static io.everytrade.server.model.TransactionType.SELL;
import static io.everytrade.server.model.TransactionType.WITHDRAWAL;
import static io.everytrade.server.model.TransactionType.STAKE;
import static io.everytrade.server.model.TransactionType.STAKING_REWARD;
import static io.everytrade.server.model.TransactionType.UNSTAKE;
import static org.junit.jupiter.api.Assertions.assertEquals;

class BinanceBeanV4Test {
    public static final String HEADER_CORRECT = "\uFEFFUser_ID,UTC_Time,Account,Operation,Coin,Change,Remark\n";

    @Test
    void testBuyRelated() {
        String row0 = "63676019,2021-01-05 02:37:59,Spot,Transaction Related,EUR,-40.02168000,\n";
        String row1 = "63676019,2021-01-05 02:37:59,Spot,Buy,USDT,16.14000000,\n";
        String row2 = "63676019,2021-01-05 02:37:59,Spot,Buy,USDT,32.38000000,\n";
        String row3 = "63676019,2021-01-05 02:37:59,Spot,Transaction Related,EUR,-19.94904000,\n";
        String row4 = "63676019,2021-01-05 02:37:59,Spot,Buy,USDT,124.61000000,\n";
        String row5 = "63676019,2021-01-05 02:37:59,Spot,Transaction Related,EUR,-154.01796000,\n";
        String row6 = "63676019,2021-01-05 02:38:00,Spot,Buy,USDT,109.98000000,\n";
        String row7 = "63676019,2021-01-05 02:38:00,Spot,Buy,USDT,12.04000000,\n";
        String row8 = "63676019,2021-01-05 02:38:00,Spot,Buy,USDT,12.03000000,\n";
        String row9 = "63676019,2021-01-05 02:38:00,Spot,Transaction Related,EUR,-24.69528000,\n";
        String row10 = "63676019,2021-01-05 02:38:00,Spot,Transaction Related,EUR,-14.86908000,\n";
        String row11 = "63676019,2021-01-05 02:38:00,Spot,Transaction Related,EUR,-135.93528000,\n";
        String row12 = "63676019,2021-01-05 02:38:00,Spot,Buy,USDT,19.98000000,\n";
        String row13 = "63676019,2021-01-05 02:38:00,Spot,Transaction Related,EUR,-14.88144000,\n";
        String row14 = "63676019,2021-01-05 02:38:00,Spot,Fee,EUR,-10.0,\n";
        String row15 = "63676019,2021-01-05 02:38:00,Spot,Fee,EUR,-10.0,\n";

        final String join =
            row0 + row1 + row2 + row3 + row4 + row5 + row6 + row7 + row8 + row9 + row10 + row11 + row12 + row13 + row14 + row15;

        final TransactionCluster actual = ParserTestUtils.getTransactionCluster(HEADER_CORRECT + join);

        final TransactionCluster expected = new TransactionCluster(
            new ImportedTransactionBean(
                null,
                Instant.parse("2021-01-05T02:38:00Z"),
                USDT,
                EUR,
                BUY,
                new BigDecimal("327.1600000000"),
                new BigDecimal("1.2360000000")
            ),
            List.of(
                new FeeRebateImportedTransactionBean(
                    null,
                    Instant.parse("2021-01-05T02:38:00Z"),
                    EUR,
                    EUR,
                    FEE,
                    new BigDecimal("20.0"),
                    EUR
                )
            ));
        TestUtils.testTxs(expected.getRelated().get(0), actual.getRelated().get(0));
        TestUtils.testTxs(expected.getMain(), actual.getMain());
    }

    @Test
    void testSellRelated() {
        String row0 = "63676019,2021-01-05 02:37:59,Spot,Transaction Related,EUR,40.02168000,\n";
        String row1 = "63676019,2021-01-05 02:37:59,Spot,Sell,USDT,-16.14000000,\n";
        String row2 = "63676019,2021-01-05 02:37:59,Spot,Sell,USDT,-32.38000000,\n";
        String row3 = "63676019,2021-01-05 02:37:59,Spot,Transaction Related,EUR,19.94904000,\n";
        String row4 = "63676019,2021-01-05 02:37:59,Spot,Sell,USDT,-124.61000000,\n";
        String row5 = "63676019,2021-01-05 02:37:59,Spot,Transaction Related,EUR,154.01796000,\n";
        String row6 = "63676019,2021-01-05 02:38:00,Spot,Sell,USDT,-109.98000000,\n";
        String row7 = "63676019,2021-01-05 02:38:00,Spot,Sell,USDT,-12.04000000,\n";
        String row8 = "63676019,2021-01-05 02:38:00,Spot,Sell,USDT,-12.03000000,\n";
        String row9 = "63676019,2021-01-05 02:38:00,Spot,Transaction Related,EUR,24.69528000,\n";
        String row10 = "63676019,2021-01-05 02:38:00,Spot,Transaction Related,EUR,14.86908000,\n";
        String row11 = "63676019,2021-01-05 02:38:00,Spot,Transaction Related,EUR,135.93528000,\n";
        String row12 = "63676019,2021-01-05 02:38:00,Spot,Sell,USDT,-19.98000000,\n";
        String row13 = "63676019,2021-01-05 02:38:00,Spot,Transaction Related,EUR,14.88144000,\n";
        String row14 = "63676019,2021-01-05 02:38:00,Spot,Fee,EUR,-10.0,\n";
        String row15 = "63676019,2021-01-05 02:38:00,Spot,Fee,EUR,-10.0,\n";

        final String join =
            row0 + row1 + row2 + row3 + row4 + row5 + row6 + row7 + row8 + row9 + row10 + row11 + row12 + row13 + row14 + row15;

        final TransactionCluster actual = ParserTestUtils.getTransactionCluster(HEADER_CORRECT + join);

        final TransactionCluster expected = new TransactionCluster(
            new ImportedTransactionBean(
                null,
                Instant.parse("2021-01-05T02:38:00Z"),
                USDT,
                EUR,
                SELL,
                new BigDecimal("327.1600000000"),
                new BigDecimal("1.2360000000")
            ),
            List.of(
                new FeeRebateImportedTransactionBean(
                    null,
                    Instant.parse("2021-01-05T02:38:00Z"),
                    EUR,
                    EUR,
                    FEE,
                    new BigDecimal("20.0"),
                    EUR
                )
            ));
        TestUtils.testTxs(expected.getRelated().get(0), actual.getRelated().get(0));
        TestUtils.testTxs(expected.getMain(), actual.getMain());
    }

    @Test
    void testConvertSell() {
        final String row0 = "41438313,2022-01-01 10:56:29,Spot,Sell,ETH,76.65970000,\"\"\n";
        final String row1 = "41438313,2022-01-01 10:56:29,Spot,Fee,BNB,-0.00011168,\"\"\n";
        final String row2 = "41438313,2022-01-01 10:56:29,Spot,Sell,BTC,-13.00000000,\"\"\n";
        final String join = row0 + row1 + row2;

        final TransactionCluster actual = ParserTestUtils.getTransactionCluster(HEADER_CORRECT + join);

        final TransactionCluster expected = new TransactionCluster(
            new ImportedTransactionBean(
                null,
                Instant.parse("2022-01-01T10:56:29Z"),
                ETH,
                BTC,
                BUY,
                new BigDecimal("76.6597000000"),
                new BigDecimal("0.1695806271")
            ),
            List.of(
                new FeeRebateImportedTransactionBean(
                    null,
                    Instant.parse("2022-01-01T10:56:29Z"),
                    BNB,
                    BNB,
                    FEE,
                    new BigDecimal("0.00011168"),
                    BNB
                )
            )
        );
        TestUtils.testTxs(expected.getRelated().get(0), actual.getRelated().get(0));
        TestUtils.testTxs(expected.getMain(), actual.getMain());
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
                    FEE,
                    new BigDecimal("11.10642"),
                    EUR
                )
            )
        );
        TestUtils.testTxs(expected.getRelated().get(0), actual.getRelated().get(0));
        TestUtils.testTxs(expected.getMain(), actual.getMain());
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

        TestUtils.testTxs(expected1.getMain(), actual.get(0).getMain());
        TestUtils.testTxs(expected2.getMain(), actual.get(1).getMain());
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
        TestUtils.testTxs(expected1.getMain(), actual.get(0).getMain());
        TestUtils.testTxs(expected2.getMain(), actual.get(1).getMain());
    }

    @Test
    void testCashbackVoucherRebate() {
        final String row = "530683417,2022-11-09 03:02:17,Spot,Cashback Voucher,USDT,0.01626671,\"\"\n";
        final List<TransactionCluster> actual = ParserTestUtils.getTransactionClusters(HEADER_CORRECT + row);

        final TransactionCluster expected1 = new TransactionCluster(
            new ImportedTransactionBean(
                null,
                Instant.parse("2022-11-09T03:02:17Z"),
                USDT,
                USDT,
                REBATE,
                new BigDecimal("0.01626671"),
                null,
                "CASHBACK VOUCHER",
                null
            ),
            List.of()
        );
        TestUtils.testTxs(expected1.getMain(), actual.get(0).getMain());
    }

    @Test
    void testSimpleEarn0() {
        final String row = "86879943,2022-03-02 17:02:42,Earn,Simple Earn Flexible Subscription,LDUSDT,236.79617000,\"\"\n";
        final List<TransactionCluster> actual = ParserTestUtils.getTransactionClusters(HEADER_CORRECT + row);

        final TransactionCluster expected = new TransactionCluster(
            ImportedTransactionBean.createDepositWithdrawal(
                null,
                Instant.parse("2022-03-02T17:02:42Z"),
                USDT,
                USDT,
                DEPOSIT,
                new BigDecimal("236.79617000"),
                null,
                "SIMPLE EARN FLEXIBLE SUBSCRIPTION, LDUSDT",
                null
            ),
            List.of()
        );
        TestUtils.testTxs(expected.getMain(), actual.get(0).getMain());

    }

    @Test
    void testSimpleEarn1() {
        final String row = "86879943,2022-03-02 17:02:42,Earn,Simple Earn Flexible Subscription,USDT,-236.79617000,\"\"\n";
        final List<TransactionCluster> actual = ParserTestUtils.getTransactionClusters(HEADER_CORRECT + row);

        final TransactionCluster expected = new TransactionCluster(
            ImportedTransactionBean.createDepositWithdrawal(
                null,
                Instant.parse("2022-03-02T17:02:42Z"),
                USDT,
                USDT,
                WITHDRAWAL,
                new BigDecimal("236.79617000"),
                null,
                "SIMPLE EARN FLEXIBLE SUBSCRIPTION",
                null
            ),
            List.of()
        );
        TestUtils.testTxs(expected.getMain(), actual.get(0).getMain());
    }

    @Test
    void testSimpleEarn2() {
        final String row = "86879943,2022-03-31 16:36:00,Earn,Simple Earn Flexible Redemption,LDUSDT,-300.00000000,\"\"\n";
        final List<TransactionCluster> actual = ParserTestUtils.getTransactionClusters(HEADER_CORRECT + row);

        final TransactionCluster expected = new TransactionCluster(
            ImportedTransactionBean.createDepositWithdrawal(
                null,
                Instant.parse("2022-03-31T16:36:00Z"),
                USDT,
                USDT,
                WITHDRAWAL,
                new BigDecimal("300.00000000"),
                null,
                "SIMPLE EARN FLEXIBLE REDEMPTION, LDUSDT",
                null
            ),
            List.of()
        );
        TestUtils.testTxs(expected.getMain(), actual.get(0).getMain());
    }

    @Test
    void testSimpleEarn3() {
        final String row = "86879943,2022-03-31 16:36:00,Earn,Simple Earn Flexible Redemption,USDT,300.00000000,\"\"\n";
        final List<TransactionCluster> actual = ParserTestUtils.getTransactionClusters(HEADER_CORRECT + row);

        final TransactionCluster expected = new TransactionCluster(
            ImportedTransactionBean.createDepositWithdrawal(
                null,
                Instant.parse("2022-03-31T16:36:00Z"),
                USDT,
                USDT,
                DEPOSIT,
                new BigDecimal("300.00000000"),
                null,
                "SIMPLE EARN FLEXIBLE REDEMPTION",
                null
            ),
            List.of()
        );
        TestUtils.testTxs(expected.getMain(), actual.get(0).getMain());
    }

    @Test
    void testSimpleEarn4() {
        final String row = "40360729,2020-11-26 08:55:38,Spot,Savings distribution,LDBTC,0.01155980,\"\"\n";
        final List<TransactionCluster> actual = ParserTestUtils.getTransactionClusters(HEADER_CORRECT + row);

        final TransactionCluster expected = new TransactionCluster(
            ImportedTransactionBean.createDepositWithdrawal(
                null,
                Instant.parse("2020-11-26T08:55:38Z"),
                BTC,
                BTC,
                DEPOSIT,
                new BigDecimal("0.01155980"),
                null,
                "SAVINGS DISTRIBUTION, LDBTC",
                null
            ),
            List.of()
        );
        TestUtils.testTxs(expected.getMain(), actual.get(0).getMain());
    }

    @Test
    void testSimpleEarn5() {
        final String row = "40360729,2020-11-26 08:55:38,Spot,Savings distribution,BTC,-0.01155980,\"\"\n";
        final List<TransactionCluster> actual = ParserTestUtils.getTransactionClusters(HEADER_CORRECT + row);

        final TransactionCluster expected = new TransactionCluster(
            ImportedTransactionBean.createDepositWithdrawal(
                null,
                Instant.parse("2020-11-26T08:55:38Z"),
                BTC,
                BTC,
                WITHDRAWAL,
                new BigDecimal("0.01155980"),
                null,
                "SAVINGS DISTRIBUTION",
                null
            ),
            List.of()
        );
        TestUtils.testTxs(expected.getMain(), actual.get(0).getMain());
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
                    FEE,
                    new BigDecimal("0.00859660"),
                    BNB
                )
            )
        );
        TestUtils.testTxs(expected.getRelated().get(0), actual.getRelated().get(0));
        TestUtils.testTxs(expected.getMain(), actual.getMain());
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
        TestUtils.testTxs(expected.getMain(), actual.getMain());
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
        TestUtils.testTxs(expected.getMain(), actual.getMain());
    }

    @Test
    void testLargeOtcTradingSell() {
        final String row0 = "42138160,2022-01-12 18:00:24,Spot,Large OTC trading,USDT,-3000.00000000,\"\"\n";
        final String row1 = "42138160,2022-01-12 18:00:24,Spot,Large OTC trading,DOGE,481.58400000,\"\"\n";
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
        TestUtils.testTxs(expected.getMain(), actual.getMain());
    }

    @Test
    void testLargeOtcTradingConvert() {
        final String row0 = "70366274,2022-04-06 23:15:18,Spot,Large OTC Trading,UST,-1557.71867805,\"\"\n";
        final String row1 = "70366274,2022-04-06 23:15:18,Spot,Large OTC Trading,RUNE,14513.31689430,\"\"\n";
        final String join = row0 + row1;

        final TransactionCluster actual = ParserTestUtils.getTransactionCluster(HEADER_CORRECT + join);

        final TransactionCluster expected = new TransactionCluster(
            new ImportedTransactionBean(
                null,
                Instant.parse("2022-04-06T23:15:18Z"),
                RUNE,
                UST,
                BUY,
                new BigDecimal("14513.3168943000"),
                new BigDecimal("0.1073303015"),
                "LARGE OTC TRADING",
                null
            ),
            List.of()
        );
        TestUtils.testTxs(expected.getMain(), actual.getMain());
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
        TestUtils.testTxs(expected1.getMain(), actual.get(0).getMain());
        TestUtils.testTxs(expected2.getMain(), actual.get(1).getMain());
    }

    @Test
    void testSmallAssetsExchangeException() {
        final String row0 = "155380140,2021-07-20 16:24:10,Spot,Small Assets Exchange BNB,ADA,-0.00500000,\"\"\n";
        final String row1 = "155380140,2021-07-20 16:24:10,Spot,Small Assets Exchange BNB,BNB,0.00125645,\"\"\n";
        final String row2 = "155380140,2021-07-20 16:24:10,Spot,Small Assets Exchange BNB,USDT,-0.00887636,\"\"\n";
        final String row3 = "155380140,2021-07-20 16:24:10,Spot,Small Assets Exchange BNB,BTC,-5.4E-7,\"\"\n";
        final String row4 = "155380140,2021-07-20 16:24:10,Spot,Small Assets Exchange BNB,EUR,100,\"\"\n";
        final String row5 = "155380140,2021-07-20 16:24:10,Spot,Small Assets Exchange BNB,EUR,-100,\"\"\n";
        final String join = row0 + row1 + row2 + row3 + row4 + row5;

        final List<TransactionCluster> actual = ParserTestUtils.getTransactionClusters(HEADER_CORRECT + join);

        final TransactionCluster expected0 = new TransactionCluster(
            new ImportedTransactionBean(
                null,
                Instant.parse("2021-07-20T16:24:10Z"),
                ADA,
                USDT,
                SELL,
                new BigDecimal("0.0050000000"),
                null,
                "SMALL ASSETS EXCHANGE BNB",
                null
            ),
            List.of()
        );

        final TransactionCluster expected1 = new TransactionCluster(
            new ImportedTransactionBean(
                null,
                Instant.parse("2021-07-20T16:24:10Z"),
                BNB,
                USDT,
                BUY,
                new BigDecimal("0.0012564500"),
                null,
                "SMALL ASSETS EXCHANGE BNB",
                null
            ),
            List.of()
        );

        final TransactionCluster expected2 = new TransactionCluster(
            new ImportedTransactionBean(
                null,
                Instant.parse("2021-07-20T16:24:10Z"),
                USDT,
                USDT,
                SELL,
                new BigDecimal("0.0088763600"),
                null,
                "SMALL ASSETS EXCHANGE BNB",
                null
            ),
            List.of()
        );

        final TransactionCluster expected3 = new TransactionCluster(
            new ImportedTransactionBean(
                null,
                Instant.parse("2021-07-20T16:24:10Z"),
                BTC,
                USDT,
                SELL,
                new BigDecimal("5.400E-7"),
                null,
                "SMALL ASSETS EXCHANGE BNB",
                null
            ),
            List.of()
        );

        final TransactionCluster expected4 = new TransactionCluster(
            ImportedTransactionBean.createDepositWithdrawal(
                null,
                Instant.parse("2021-07-20T16:24:10Z"),
                EUR,
                EUR,
                DEPOSIT,
                new BigDecimal("100"),
                null,
                "SMALL ASSETS EXCHANGE BNB",
                null
            ),
            List.of()
        );

        final TransactionCluster expected5 = new TransactionCluster(
            ImportedTransactionBean.createDepositWithdrawal(
                null,
                Instant.parse("2021-07-20T16:24:10Z"),
                EUR,
                EUR,
                WITHDRAWAL,
                new BigDecimal("100"),
                null,
                "SMALL ASSETS EXCHANGE BNB",
                null
            ),
            List.of()
        );

        TestUtils.testTxs(expected0.getMain(), actual.get(0).getMain());
        TestUtils.testTxs(expected1.getMain(), actual.get(1).getMain());
        TestUtils.testTxs(expected2.getMain(), actual.get(2).getMain());
        TestUtils.testTxs(expected3.getMain(), actual.get(3).getMain());
        TestUtils.testTxs(expected4.getMain(), actual.get(4).getMain());
        TestUtils.testTxs(expected5.getMain(), actual.get(5).getMain());
    }

    @Test
    void testOperationTypeSimpleEarnFlexibleInterest0() {
        final String row = "40360729,2020-11-28 00:59:18,Earn,Simple Earn Flexible Interest,BTC,3.8E-7,\"\"\n";
        final List<TransactionCluster> actual = ParserTestUtils.getTransactionClusters(HEADER_CORRECT + row);

        final TransactionCluster expected = new TransactionCluster(
            new ImportedTransactionBean(
                null,
                Instant.parse("2020-11-28T00:59:18Z"),
                BTC,
                BTC,
                EARNING,
                new BigDecimal("0.00000038"),
                null,
                "SIMPLE EARN FLEXIBLE INTEREST",
                null,
                null
            ),
            List.of()
        );

        TestUtils.testTxs(expected.getMain(), actual.get(0).getMain());
    }

    @Test
    void testOperationTypeSimpleEarnFlexibleInterest1() {
        final String row = "40360729,2020-11-28 00:59:18,Earn,Simple Earn Flexible Interest,ETH,0.00011628,\"\"\n";
        final List<TransactionCluster> actual = ParserTestUtils.getTransactionClusters(HEADER_CORRECT + row);

        final TransactionCluster expected = new TransactionCluster(
            new ImportedTransactionBean(
                null,
                Instant.parse("2020-11-28T00:59:18Z"),
                ETH,
                ETH,
                EARNING,
                new BigDecimal("0.00011628"),
                null,
                "SIMPLE EARN FLEXIBLE INTEREST",
                null,
                null
            ),
            List.of()
        );
        TestUtils.testTxs(expected.getMain(), actual.get(0).getMain());
    }

    @Test
    void testOperationTypeSimpleEarnFlexibleInterest2() {
        final String row = "40360729,2020-11-29 00:59:53,Earn,Simple Earn Flexible Interest,BTC,3.8E-7,\"\"\n";
        final List<TransactionCluster> actual = ParserTestUtils.getTransactionClusters(HEADER_CORRECT + row);

        final TransactionCluster expected = new TransactionCluster(
            new ImportedTransactionBean(
                null,
                Instant.parse("2020-11-29T00:59:53Z"),
                BTC,
                BTC,
                EARNING,
                new BigDecimal("0.00000038"),
                null,
                "SIMPLE EARN FLEXIBLE INTEREST",
                null,
                null
            ),
            List.of()
        );
        TestUtils.testTxs(expected.getMain(), actual.get(0).getMain());
    }

    @Test
    void testTransactionBuy() {
        final String row0 = "155380140,2021-06-25 14:11:52,Spot,Fee,SHIB,-4324.22000000,\"\"\n";
        final String row1 = "155380140,2021-06-25 14:11:52,Spot,Transaction Buy,SHIB,4324216.00000000,\"\"\n";
        final String row2 = "155380140,2021-06-25 14:11:52,Spot,Transaction Spend,USDT,-32.17216704,\"\"\n";
        final String join = row0 + row1 + row2;

        final List<TransactionCluster> actual = ParserTestUtils.getTransactionClusters(HEADER_CORRECT + join);

        final TransactionCluster expected = new TransactionCluster(
            new ImportedTransactionBean(
                null,
                Instant.parse("2021-06-25T14:11:52Z"),
                SHIB,
                USDT,
                BUY,
                new BigDecimal("4324216.0000000000"),
                new BigDecimal("0.0000074400"),
                "TRANSACTION BUY",
                null,
                null
            ),
            List.of(
                new FeeRebateImportedTransactionBean(
                    null,
                    Instant.parse("2021-06-25T14:11:52Z"),
                    SHIB,
                    SHIB,
                    FEE,
                    new BigDecimal("4324.22000000"),
                    SHIB
                )
            )
        );

        TestUtils.testTxs(expected.getMain(), actual.get(0).getMain());
    }

    @Test
    void testTransactionRevenue() {
        final String row0 = "155380140,2021-07-12 19:28:23,Spot,Transaction Revenue,BUSD,85.18238417,\"\"\n";
        final String row1 = "155380140,2021-07-12 19:28:23,Spot,Transaction Sold,BTC,-0.00257900,\"\"\n";
        final String row2 = "155380140,2021-07-12 19:28:23,Spot,Fee,BUSD,-0.08518238,\"\"\n";
        final String join = row0 + row1 + row2;

        final List<TransactionCluster> actual = ParserTestUtils.getTransactionClusters(HEADER_CORRECT + join);

        final TransactionCluster expected = new TransactionCluster(
            new ImportedTransactionBean(
                null,
                Instant.parse("2021-07-12T19:28:23Z"),
                BUSD,
                BTC,
                BUY,
                new BigDecimal("85.1823841700"),
                new BigDecimal("0.0000302762"),
                "TRANSACTION REVENUE",
                null,
                null
            ),
            List.of(
                new FeeRebateImportedTransactionBean(
                    null,
                    Instant.parse("2021-07-12T19:28:23Z"),
                    BUSD,
                    BUSD,
                    FEE,
                    new BigDecimal("0.08518238"),
                    BUSD
                )
            )
        );

        TestUtils.testTxs(expected.getMain(), actual.get(0).getMain());
    }

    @Test
    void testCryptoBuy() {
        final String row0 = "155380140,2022-01-21 05:27:49,Spot,Buy Crypto,CZK,1471.50000000,\"\"\n";
        final String row1 = "155380140,2022-01-21 05:27:51,Spot,Buy Crypto,CZK,-1471.50000000,\"\"\n";
        final String row2 = "155380140,2022-01-21 05:27:51,Spot,Buy Crypto,ETH,0.02339931,\"\"\n";
        final String join = row0 + row1 + row2;

        final List<TransactionCluster> actual = ParserTestUtils.getTransactionClusters(HEADER_CORRECT + join);

        final TransactionCluster expected0 = new TransactionCluster(
            new ImportedTransactionBean(
                null,
                Instant.parse("2022-01-21T05:27:51Z"),
                ETH,
                CZK,
                BUY,
                new BigDecimal("0.0233993100"),
                new BigDecimal("62886.4697292356"),
                "BUY CRYPTO",
                null,
                null
            ),
            List.of()
        );

        final TransactionCluster expected1 = new TransactionCluster(
            ImportedTransactionBean.createDepositWithdrawal(
                null,
                Instant.parse("2022-01-21T05:27:49Z"),
                CZK,
                CZK,
                DEPOSIT,
                new BigDecimal("1471.50000000"),
                null,
                "BUY CRYPTO",
                null
            ),
            List.of()
        );
        TestUtils.testTxs(expected0.getMain(), actual.get(0).getMain());
        TestUtils.testTxs(expected1.getMain(), actual.get(1).getMain());
    }

    @Test
    void testStakingRewards() {
        final String row = "86879943,2022-01-01 00:48:07,Spot,Staking Rewards,DOT,0.00044214,\"\"\n";

        final List<TransactionCluster> actual = ParserTestUtils.getTransactionClusters(HEADER_CORRECT + row);

        final TransactionCluster expected = new TransactionCluster(
            new ImportedTransactionBean(
                null,
                Instant.parse("2022-01-01T00:48:07Z"),
                DOT,
                DOT,
                STAKING_REWARD,
                new BigDecimal("0.00044214"),
                null,
                "STAKING REWARDS",
                null,
                null
            ),
            List.of()
        );
        TestUtils.testTxs(expected.getMain(), actual.get(0).getMain());
    }

    @Test
    void testBnbVaultRewards() {
        final String row = "40360729,2020-12-27 02:36:24,Spot,BNB Vault Rewards,REEF,27.07578576,\n";

        final List<TransactionCluster> actual = ParserTestUtils.getTransactionClusters(HEADER_CORRECT + row);

        final TransactionCluster expected = new TransactionCluster(
            new ImportedTransactionBean(
                null,
                Instant.parse("2020-12-27T02:36:24Z"),
                REEF,
                REEF,
                REWARD,
                new BigDecimal("27.07578576"),
                null,
                "BNB VAULT REWARDS",
                null,
                null
            ),
            List.of()
        );
        TestUtils.testTxs(expected.getMain(), actual.get(0).getMain());
    }

    @Test
    void testSimpleEarnLockedRewards() {
        final String row = "86879943,2022-09-27 03:56:38,Spot,Simple Earn Locked Rewards,ADA,0.02587789,\"\"\n";

        final List<TransactionCluster> actual = ParserTestUtils.getTransactionClusters(HEADER_CORRECT + row);

        final TransactionCluster expected = new TransactionCluster(
            new ImportedTransactionBean(
                null,
                Instant.parse("2022-09-27T03:56:38Z"),
                ADA,
                ADA,
                EARNING,
                new BigDecimal("0.02587789"),
                null,
                "SIMPLE EARN LOCKED REWARDS",
                null,
                null
            ),
            List.of()
        );
        TestUtils.testTxs(expected.getMain(), actual.get(0).getMain());
    }

    @Test
    void testStakingRedemption() {
        final String row = "86879943,2022-01-04 01:08:57,Spot,Staking Redemption,DOT,1.40212298,\"\"\n";

        final List<TransactionCluster> actual = ParserTestUtils.getTransactionClusters(HEADER_CORRECT + row);

        final TransactionCluster expected = new TransactionCluster(
            new ImportedTransactionBean(
                null,
                Instant.parse("2022-01-04T01:08:57Z"),
                DOT,
                DOT,
                UNSTAKE,
                new BigDecimal("1.40212298"),
                null,
                "STAKING REDEMPTION",
                null,
                null
            ),
            List.of()
        );
        TestUtils.testTxs(expected.getMain(), actual.get(0).getMain());
    }

    @Test
    void testStakingPurchase() {
        final String row = "86879943,2022-01-04 21:17:14,Spot,Staking Purchase,ADA,-200.09109425,\"\"\n";

        final List<TransactionCluster> actual = ParserTestUtils.getTransactionClusters(HEADER_CORRECT + row);

        final TransactionCluster expected = new TransactionCluster(
            new ImportedTransactionBean(
                null,
                Instant.parse("2022-01-04T21:17:14Z"),
                ADA,
                ADA,
                STAKE,
                new BigDecimal("200.09109425"),
                null,
                "STAKING PURCHASE",
                null,
                null
            ),
            List.of()
        );
        TestUtils.testTxs(expected.getMain(), actual.get(0).getMain());
    }

    @Test
    void testBinanceConvert() {
        final String row0 = "132491447,2021-06-10 09:52:01,Spot,Binance Convert,ADA,-10.00000000,\"\"\n";
        final String row1 = "132491447,2021-06-10 09:52:01,Spot,Binance Convert,USDT,15.67270000,\"\"\n";
        final String row2 = "132491447,2021-06-10 09:54:06,Spot,Binance Convert,ADA,-10.00000000,\"\"\n";
        final String row3 = "132491447,2021-06-10 09:54:06,Spot,Binance Convert,USDT,15.71860000,\"\"\n";
        final String row4 = "132491447,2021-06-10 09:54:26,Spot,Binance Convert,EUR,25.81453252,\"\"\n";
        final String row5 = "132491447,2021-06-10 09:54:26,Spot,Binance Convert,USDT,-31.40596026,\"\"\n";

        final String join = row0 + row1 + row2 + row3 + row4 + row5;

        final List<TransactionCluster> actual = ParserTestUtils.getTransactionClusters(HEADER_CORRECT + join);

        final TransactionCluster expected0 = new TransactionCluster(
            new ImportedTransactionBean(
                null,
                Instant.parse("2021-06-10T09:52:01Z"),
                USDT,
                ADA,
                BUY,
                new BigDecimal("15.6727000000"),
                new BigDecimal("0.6380521544"),
                "BINANCE CONVERT",
                null,
                null
            ),
            List.of()
        );

        final TransactionCluster expected1 = new TransactionCluster(
            new ImportedTransactionBean(
                null,
                Instant.parse("2021-06-10T09:54:06Z"),
                USDT,
                ADA,
                BUY,
                new BigDecimal("15.7186000000"),
                new BigDecimal("0.6361889736"),
                "BINANCE CONVERT",
                null,
                null
            ),
            List.of()
        );

        final TransactionCluster expected2 = new TransactionCluster(
            new ImportedTransactionBean(
                null,
                Instant.parse("2021-06-10T09:54:26Z"),
                USDT,
                EUR,
                SELL,
                new BigDecimal("31.4059602600"),
                new BigDecimal("0.8219628474"),
                "BINANCE CONVERT",
                null,
                null
            ),
            List.of()
        );
        TestUtils.testTxs(expected0.getMain(), actual.get(0).getMain());
        TestUtils.testTxs(expected1.getMain(), actual.get(2).getMain());
        TestUtils.testTxs(expected2.getMain(), actual.get(1).getMain());
    }

    @Test
    void testTransactionRelated() {
        final String row0 = "155380140,2021-05-12 18:39:47,Spot,Transaction Related,BTC,0.00083700,\"\"\n";
        final String row1 = "155380140,2021-05-12 18:39:47,Spot,Deposit,CZK,981.00000000,\"\"\n";
        final String row2 = "155380140,2021-05-12 18:39:47,Spot,Transaction Related,CZK,-981.00000000,\"\"\n";
        final String join = row0 + row1 + row2;

        final List<TransactionCluster> actual = ParserTestUtils.getTransactionClusters(HEADER_CORRECT + join);

        final TransactionCluster expected0 = new TransactionCluster(
            new ImportedTransactionBean(
                null,
                Instant.parse("2021-05-12T18:39:47Z"),
                BTC,
                CZK,
                BUY,
                new BigDecimal("0.0008370000"),
                new BigDecimal("1172043.0107526882"),
                "TRANSACTION RELATED",
                null,
                null
            ),
            List.of()
        );

        final TransactionCluster expected1 = new TransactionCluster(
            new ImportedTransactionBean(
                null,
                Instant.parse("2021-05-12T18:39:47Z"),
                CZK,
                CZK,
                DEPOSIT,
                new BigDecimal("981.00000000"),
                null,
                null,
                null,
                null
            ),
            List.of()
        );

        TestUtils.testTxs(expected0.getMain(), actual.get(0).getMain());
        TestUtils.testTxs(expected1.getMain(), actual.get(1).getMain());
    }

    @Test
    void testConvertBuySellWithWrongTolerance() {
        final String row0 = "86879943,2022-07-17 11:14:21,Spot,Sell,BUSD,121.04594400,\"\"\n";
        final String row1 = "86879943,2022-07-17 11:14:21,Spot,Sell,ETH,-0.08880000,\"\"\n";

        final String row2 = "86879943,2022-07-17 11:14:22,Spot,Sell,BTC,-0.00094000,\"\"\n";
        final String row3 = "86879943,2022-07-17 11:14:22,Spot,Buy,ETH,0.01470000,\"\"\n";
        final String row4 = "86879943,2022-07-17 11:14:22,Spot,Sell,BUSD,20.13856940,\"\"\n";
        final String row5 = "86879943,2022-07-17 11:14:22,Spot,Buy,BUSD,-50.10363540,\"\"\n";
        final String row6 = "86879943,2022-07-17 11:14:22,Spot,Buy,BUSD,-20.03036700,\"\"\n";
        final String row7 = "86879943,2022-07-17 11:14:22,Spot,Fee,BNB,-0.00005936,\"\"\n";
        final String row8 = "86879943,2022-07-17 11:14:22,Spot,Buy,BTC,0.00234000,\"\"\n";
        final String join = row0 + row1 + row2 + row3 + row4 + row5 + row6 + row7 + row8;

        final List<TransactionCluster> actual = ParserTestUtils.getTransactionClusters(HEADER_CORRECT + join);

        final TransactionCluster expected0 = new TransactionCluster(
            new ImportedTransactionBean(
                null,
                Instant.parse("2022-07-17T11:14:21Z"),
                BUSD,
                ETH,
                BUY,
                new BigDecimal("121.0459440000"),
                new BigDecimal("0.0007336057")
            ),
            List.of()
        );

        final TransactionCluster expected1 = new TransactionCluster(
            new ImportedTransactionBean(
                null,
                Instant.parse("2022-07-17T11:14:22Z"),
                BTC,
                USDT,
                SELL,
                new BigDecimal("0.0009400000"),
                null
            ),
            List.of(
                new FeeRebateImportedTransactionBean(
                    null,
                    Instant.parse("2022-07-17T11:14:22Z"),
                    BNB,
                    BNB,
                    FEE,
                    new BigDecimal("0.00005936"),
                    BNB
                )
            )
        );
        final TransactionCluster expected2 = new TransactionCluster(
            new ImportedTransactionBean(
                null,
                Instant.parse("2022-07-17T11:14:22Z"),
                ETH,
                USDT,
                BUY,
                new BigDecimal("0.0147000000"),
                null
            ),
            List.of()
        );

        final TransactionCluster expected3 = new TransactionCluster(
            new ImportedTransactionBean(
                null,
                Instant.parse("2022-07-17T11:14:22Z"),
                BUSD,
                USDT,
                BUY,
                new BigDecimal("20.1385694000"),
                null
            ),
            List.of()
        );

        final TransactionCluster expected4 = new TransactionCluster(
            new ImportedTransactionBean(
                null,
                Instant.parse("2022-07-17T11:14:22Z"),
                BUSD,
                USDT,
                SELL,
                new BigDecimal("50.1036354000"),
                null
            ),
            List.of()
        );

        final TransactionCluster expected5 = new TransactionCluster(
            new ImportedTransactionBean(
                null,
                Instant.parse("2022-07-17T11:14:22Z"),
                BUSD,
                USDT,
                SELL,
                new BigDecimal("20.0303670000"),
                null
            ),
            List.of()
        );

        final TransactionCluster expected6 = new TransactionCluster(
            new ImportedTransactionBean(
                null,
                Instant.parse("2022-07-17T11:14:22Z"),
                BTC,
                USDT,
                BUY,
                new BigDecimal("0.0023400000"),
                null
            ),
            List.of()
        );
        TestUtils.testTxs( expected0.getMain(),actual.get(6).getMain());
        TestUtils.testTxs( expected1.getMain(),actual.get(0).getMain());
        TestUtils.testTxs( expected1.getRelated().get(0),actual.get(0).getRelated().get(0));
        TestUtils.testTxs( expected2.getMain(),actual.get(1).getMain());
        TestUtils.testTxs( expected3.getMain(),actual.get(2).getMain());
        TestUtils.testTxs( expected4.getMain(),actual.get(3).getMain());
        TestUtils.testTxs( expected5.getMain(),actual.get(4).getMain());
        TestUtils.testTxs( expected6.getMain(),actual.get(5).getMain());
    }


    @Test
    void testConvertBuySellWithWrongTolerance2() {
        final String row0 = "86879943,2022-07-19 16:29:34,Spot,Sell,BUSD,20.38720000,\"\"\n";
        final String row1 = "86879943,2022-07-19 16:29:34,Spot,Sell,NEAR,-4.60000000,\"\"\n";
        final String row2 = "86879943,2022-07-19 16:29:35,Spot,Buy,BUSD,-20.39640000,\"\"\n";
        final String row3 = "86879943,2022-07-19 16:29:35,Spot,Buy,NEAR,4.60000000,\"\"\n";
        final String join = row0 + row1 + row2 + row3;

        final List<TransactionCluster> actual = ParserTestUtils.getTransactionClusters(HEADER_CORRECT + join);

        final TransactionCluster expected0 = new TransactionCluster(
            new ImportedTransactionBean(
                null,
                Instant.parse("2022-07-19T16:29:34Z"),
                BUSD,
                NEAR,
                BUY,
                new BigDecimal("20.3872000000"),
                new BigDecimal("0.2256317690")
            ),
            List.of()
        );

        final TransactionCluster expected1 = new TransactionCluster(
            new ImportedTransactionBean(
                null,
                Instant.parse("2022-07-19T16:29:35Z"),
                NEAR,
                BUSD,
                BUY,
                new BigDecimal("4.6000000000"),
                new BigDecimal("4.4340000000")
            ),
            List.of()
        );
        TestUtils.testTxs( expected0.getMain(),actual.get(0).getMain());
        TestUtils.testTxs( expected1.getMain(),actual.get(1).getMain());
    }


}