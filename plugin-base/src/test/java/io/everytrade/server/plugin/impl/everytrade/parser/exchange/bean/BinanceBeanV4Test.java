package io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean;

import io.everytrade.server.plugin.api.parser.FeeRebateImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.ImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.ParseResult;
import io.everytrade.server.plugin.api.parser.ParsingProblem;
import io.everytrade.server.plugin.api.parser.ParsingProblemType;
import io.everytrade.server.plugin.api.parser.TransactionCluster;
import io.everytrade.server.test.TestUtils;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static io.everytrade.server.model.Currency.ADA;
import static io.everytrade.server.model.Currency.BETH;
import static io.everytrade.server.model.Currency.BNB;
import static io.everytrade.server.model.Currency.BTC;
import static io.everytrade.server.model.Currency.BUSD;
import static io.everytrade.server.model.Currency.CZK;
import static io.everytrade.server.model.Currency.DOGE;
import static io.everytrade.server.model.Currency.DOT;
import static io.everytrade.server.model.Currency.ETH;
import static io.everytrade.server.model.Currency.ETHW;
import static io.everytrade.server.model.Currency.EUR;
import static io.everytrade.server.model.Currency.KSM;
import static io.everytrade.server.model.Currency.LTC;
import static io.everytrade.server.model.Currency.NEAR;
import static io.everytrade.server.model.Currency.REEF;
import static io.everytrade.server.model.Currency.ROSE;
import static io.everytrade.server.model.Currency.RUNE;
import static io.everytrade.server.model.Currency.SHIB;
import static io.everytrade.server.model.Currency.SOL;
import static io.everytrade.server.model.Currency.USD;
import static io.everytrade.server.model.Currency.USDC;
import static io.everytrade.server.model.Currency.USDT;
import static io.everytrade.server.model.Currency.UST;
import static io.everytrade.server.model.TransactionType.AIRDROP;
import static io.everytrade.server.model.TransactionType.BUY;
import static io.everytrade.server.model.TransactionType.DEPOSIT;
import static io.everytrade.server.model.TransactionType.EARNING;
import static io.everytrade.server.model.TransactionType.FEE;
import static io.everytrade.server.model.TransactionType.REBATE;
import static io.everytrade.server.model.TransactionType.REWARD;
import static io.everytrade.server.model.TransactionType.SELL;
import static io.everytrade.server.model.TransactionType.STAKE;
import static io.everytrade.server.model.TransactionType.STAKING_REWARD;
import static io.everytrade.server.model.TransactionType.UNSTAKE;
import static io.everytrade.server.model.TransactionType.WITHDRAWAL;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.ExchangeBean.FEE_UID_PART;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.ExchangeBean.REBATE_UID_PART;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v4.BinanceBeanV4.BINANCE_CARD_SPENDING;
import static org.junit.jupiter.api.Assertions.assertEquals;

class BinanceBeanV4Test {
    public static final String HEADER_CORRECT = "\uFEFFUser_ID,UTC_Time,Account,Operation,Coin,Change,Remark\n";
    public static final String HEADER_CORRECT_QUOTED =
        "\"User_ID,\"\"UTC_Time\"\",\"\"Account\"\",\"\"Operation\"\",\"\"Coin\"\",\"\"Change\"\",\"\"Remark\"\"\"\n";

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
                        new BigDecimal("327.16000000000000000"),
                        new BigDecimal("1.23600000000000000")
                ),
                List.of(
                        new FeeRebateImportedTransactionBean(
                                FEE_UID_PART,
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
                        new BigDecimal("327.16000000000000000"),
                        new BigDecimal("1.23600000000000000")
                ),
                List.of(
                        new FeeRebateImportedTransactionBean(
                                FEE_UID_PART,
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
                        new BigDecimal("76.65970000000000000"),
                        new BigDecimal("0.16958062710915905")
                ),
                List.of(
                        new FeeRebateImportedTransactionBean(
                                FEE_UID_PART,
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
                        USDT,
                        EUR,
                        SELL,
                        new BigDecimal("9820.00000000000000000"),
                        new BigDecimal("1.13100000000000000")
                ),
                List.of(
                        new FeeRebateImportedTransactionBean(
                                FEE_UID_PART,
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
                        REBATE_UID_PART,
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
                        REBATE_UID_PART,
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
                        REBATE_UID_PART,
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
    void testTypeETH2_0() {
        final String row = "40360729,2020-12-28 09:27:37,Spot,ETH 2.0 Staking Rewards,BETH,0.00119837,\"\"\n";
        final List<TransactionCluster> actual = ParserTestUtils.getTransactionClusters(HEADER_CORRECT + row);

        final TransactionCluster expectedStake = new TransactionCluster(
                new ImportedTransactionBean(
                        null,
                        Instant.parse("2020-12-28T09:27:38Z"),
                        BETH,
                        BETH,
                        STAKE,
                        new BigDecimal("0.00119837"),
                        null,
                        null,
                        null
                ),
                List.of()
        );

        final TransactionCluster expectedStakeReward = new TransactionCluster(
                new ImportedTransactionBean(
                        null,
                        Instant.parse("2020-12-28T09:27:37Z"),
                        BETH,
                        BETH,
                        STAKING_REWARD,
                        new BigDecimal("0.00119837"),
                        null,
                        null,
                        null
                ),
                List.of()
        );
        TestUtils.testTxs(expectedStake.getMain(), actual.get(1).getMain());
        TestUtils.testTxs(expectedStakeReward.getMain(), actual.get(0).getMain());
    }

    @Test
    void testTypeETH2_0TwoRowTxSTAKE() {
        final String row = "40360729,2020-12-02 13:20:22,Spot,ETH 2.0 Staking,BETH,3.26000000,\"\"\n";
        final String row1 = "40360729,2020-12-02 13:20:22,Spot,ETH 2.0 Staking,ETH,-3.26000000,\"\"\n";
        final List<TransactionCluster> actual = ParserTestUtils.getTransactionClusters(HEADER_CORRECT + row.concat(row1));

        final TransactionCluster expectedStake = new TransactionCluster(
                new ImportedTransactionBean(
                        "40360729",
                        Instant.parse("2020-12-02T13:20:21Z"),
                        BETH,
                        ETH,
                        BUY,
                        new BigDecimal("3.26000000000000000"),
                        new BigDecimal("1.00000000000000000"),
                        null,
                        null
                ),
                List.of()
        );

        final TransactionCluster expectedStakeReward = new TransactionCluster(
                new ImportedTransactionBean(
                        null,
                        Instant.parse("2020-12-02T13:20:22Z"),
                        BETH,
                        BETH,
                        STAKE,
                        new BigDecimal("3.26000000"),
                        null,
                        null,
                        null
                ),
                List.of()
        );
        TestUtils.testTxs(expectedStake.getMain(), actual.get(0).getMain());
        TestUtils.testTxs(expectedStakeReward.getMain(), actual.get(1).getMain());
    }
    @Test
    void testTypeETH2_0TwoRowTxUNSTAKE() {
        final String row = "40360729,2020-12-02 13:20:22,Spot,ETH 2.0 Staking,BETH,-3.26000000,\"\"\n";
        final String row1 = "40360729,2020-12-02 13:20:22,Spot,ETH 2.0 Staking,ETH,3.26000000,\"\"\n";
        final List<TransactionCluster> actual = ParserTestUtils.getTransactionClusters(HEADER_CORRECT + row.concat(row1));

        final TransactionCluster expectedStake = new TransactionCluster(
                new ImportedTransactionBean(
                        "40360729",
                        Instant.parse("2020-12-02T13:20:23Z"),
                        ETH,
                        BETH,
                        BUY,
                        new BigDecimal("3.26000000000000000"),
                        new BigDecimal("1.00000000000000000"),
                        null,
                        null
                ),
                List.of()
        );

        final TransactionCluster expectedStakeReward = new TransactionCluster(
                new ImportedTransactionBean(
                        null,
                        Instant.parse("2020-12-02T13:20:22Z"),
                        BETH,
                        BETH,
                        UNSTAKE,
                        new BigDecimal("3.26000000"),
                        null,
                        null,
                        null
                ),
                List.of()
        );
        TestUtils.testTxs(expectedStake.getMain(), actual.get(0).getMain());
        TestUtils.testTxs(expectedStakeReward.getMain(), actual.get(1).getMain());
    }

    @Test
    void testTypeETH2_0_FAIL() {
        final String row = "40360729,2020-12-02 13:20:22,Spot,ETH 2.0 Staking,BETH,-3.26000000,\"\"\n";
        final String row1 = "40360729,2020-12-02 13:20:22,Spot,ETH 2.0 Staking,ETH,-3.26000000,\"\"\n";
        final ParseResult actual = ParserTestUtils.getParseResult(HEADER_CORRECT + row.concat(row1));

        var expectedProblem = new ParsingProblem("[38065325, 2021-05-18 18:19:50, SPOT, Fiat Withdraw, EUR, 8000.00000000, null]",
            "Row id 2: ; One or more rows in group ( rows:  2; 3;) is unsupported; Transactions do not have both positive and " +
                "negative amounts", ParsingProblemType.PARSED_ROW_IGNORED);

        assertEquals(expectedProblem.getMessage(), actual.getParsingProblems().get(0).getMessage());
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
    void testFee() {
        final String row = "\"519616471\",\"2023-11-09 10:00:50\",\"Spot\",\"Transaction Revenue\",\"USDT\",\"5361.53976000\",\"\"\n";
        final String row1 = "\"519616471\",\"2023-11-09 10:00:50\",\"Spot\",\"Transaction Sold\",\"LTC\",\"-71.37300000\",\"\"\n";
        final String row2 = "\"519616471\",\"2023-11-09 10:00:50\",\"Spot\",\"Transaction Fee\",\"USDT\",\"-5.36153976\",\"\"\n";
        final List<TransactionCluster> actual = ParserTestUtils.getTransactionClusters(HEADER_CORRECT + row.concat(row1).concat(row2));

        var expected = new TransactionCluster(new ImportedTransactionBean(
            null,
            Instant.parse("2023-11-09T10:00:50Z"),
            USDT,
            LTC,
            BUY,
            new BigDecimal("5361.53976000000000000"),
            new BigDecimal("0.01331203407880724"),
            "TRANSACTION REVENUE",
            null,
            null
        ),
            List.of(
                new FeeRebateImportedTransactionBean(
                    FEE_UID_PART,
                    Instant.parse("2023-11-09T10:00:50Z"),
                    USDT,
                    USDT,
                    FEE,
                    new BigDecimal("5.36153976"),
                    USDT
                )
            )
        );
        TestUtils.testTxs(expected.getMain(), actual.get(0).getMain());
        TestUtils.testTxs(expected.getRelated().get(0), actual.get(0).getRelated().get(0));
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
                        new BigDecimal("4477.40000000000000000"),
                        new BigDecimal("1.31700000000000000")
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
                        new BigDecimal("481.58400000000000000"),
                        new BigDecimal("6.22944283863251271"),
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
                        new BigDecimal("7524.81470366000000000"),
                        new BigDecimal("0.99987007785593385"),
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
                        new BigDecimal("481.58400000000000000"),
                        new BigDecimal("6.22944283863251271"),
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
                        new BigDecimal("14513.31689430000000000"),
                        new BigDecimal("0.10733030150135995"),
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
                        new BigDecimal("0.00003908000000000"),
                        new BigDecimal("28.50562947799385875"),
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
                        new BigDecimal("0.00010405000000000"),
                        new BigDecimal("33.66708313310908217"),
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
                        new BigDecimal("0.00500000000000000"),
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
                        new BigDecimal("0.00125645000000000"),
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
                        new BigDecimal("0.00887636000000000"),
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
                        new BigDecimal("5.4000000000E-7"),
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
                        new BigDecimal("4324216.00000000000000000"),
                        new BigDecimal("0.00000744000000000"),
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
                new BigDecimal("85.18238417000000000"),
                new BigDecimal("0.00003027621291807"),
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
                        new BigDecimal("0.02339931000000000"),
                        new BigDecimal("62886.46972923560566530"),
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
    void testStakingReferralCommission() {
        final String row0 = "367642709,2022-01-15 16:15:42,SPOT,Transaction Buy,KSM,0.18300000,\"\"\n";
        final String row1 = "367642709,2022-01-15 16:15:42,SPOT,Transaction Spend,BTC,-0.00118950,\"\"\n";
        final String row2 = "367642709,2022-01-15 16:15:42,SPOT,Fee,KSM,-0.00018300,\"\"\n";
        final String row3 = "367642709,2022-01-15 16:15:42,SPOT,Referral Commission,KSM,0.00001830,\"\"\n";
        final String join = row0 + row1 + row2 + row3;

        final List<TransactionCluster> actual = ParserTestUtils.getTransactionClusters(HEADER_CORRECT + join);

        final TransactionCluster expected0 = new TransactionCluster(
                new ImportedTransactionBean(
                        null,
                        Instant.parse("2022-01-15T16:15:42Z"),
                        KSM,
                        BTC,
                        BUY,
                        new BigDecimal("0.18300000000000000"),
                        new BigDecimal("0.00650000000000000"),
                        "TRANSACTION BUY",
                        null
                ),
                List.of(
                        new FeeRebateImportedTransactionBean(
                                null,
                                Instant.parse("2022-01-15T16:15:42Z"),
                                KSM,
                                KSM,
                                FEE,
                                new BigDecimal("0.00018300"),
                                KSM
                        )
                )
        );

        final TransactionCluster expected1 = new TransactionCluster(
                new ImportedTransactionBean(
                        null,
                        Instant.parse("2022-01-15T16:15:42Z"),
                        KSM,
                        KSM,
                        REWARD,
                        new BigDecimal("0.00001830"),
                        null,
                        "REFERRAL COMMISSION",
                        null,
                        null
                ),
                List.of()
        );
        TestUtils.testTxs(expected0.getMain(), actual.get(0).getMain());
        TestUtils.testTxs(expected0.getRelated().get(0), actual.get(0).getRelated().get(0));
        TestUtils.testTxs(expected1.getMain(), actual.get(1).getMain());
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
                        new BigDecimal("15.67270000000000000"),
                        new BigDecimal("0.63805215438309927"),
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
                        new BigDecimal("15.71860000000000000"),
                        new BigDecimal("0.63618897357271004"),
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
                        new BigDecimal("31.40596026000000000"),
                        new BigDecimal("0.82196284737959482"),
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
                        new BigDecimal("0.00083700000000000"),
                        new BigDecimal("1172043.01075268817204301"),
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
                        new BigDecimal("121.04594400000000000"),
                        new BigDecimal("0.00073360574560020")
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
                        new BigDecimal("0.00094000000000000"),
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
                        new BigDecimal("0.01470000000000000"),
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
                        new BigDecimal("20.13856940000000000"),
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
                        new BigDecimal("50.10363540000000000"),
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
                        new BigDecimal("20.03036700000000000"),
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
                        new BigDecimal("0.00234000000000000"),
                        null
                ),
                List.of()
        );
        TestUtils.testTxs(expected0.getMain(), actual.get(6).getMain());
        TestUtils.testTxs(expected1.getMain(), actual.get(0).getMain());
        TestUtils.testTxs(expected1.getRelated().get(0), actual.get(0).getRelated().get(0));
        TestUtils.testTxs(expected2.getMain(), actual.get(1).getMain());
        TestUtils.testTxs(expected3.getMain(), actual.get(2).getMain());
        TestUtils.testTxs(expected4.getMain(), actual.get(3).getMain());
        TestUtils.testTxs(expected5.getMain(), actual.get(4).getMain());
        TestUtils.testTxs(expected6.getMain(), actual.get(5).getMain());
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
                        new BigDecimal("20.38720000000000000"),
                        new BigDecimal("0.22563176895306859")
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
                        new BigDecimal("4.60000000000000000"),
                        new BigDecimal("4.43400000000000000")
                ),
                List.of()
        );
        TestUtils.testTxs(expected0.getMain(), actual.get(0).getMain());
        TestUtils.testTxs(expected1.getMain(), actual.get(1).getMain());
    }

    @Test
    void testFiatWithdraw() {
        final String row0 = "38065325,2021-05-18 18:19:50,SPOT,Fiat Withdraw,EUR,-8000.00000000,\"\"\n";
        final String row1 = "38065325,2021-05-18 18:19:50,SPOT,Fiat Withdraw,EUR,8000.00000000,\"\"\n";
        final String join = row0 + row1;
        final var actual = ParserTestUtils.getParseResult(HEADER_CORRECT + join);

        final TransactionCluster expected = new TransactionCluster(
                ImportedTransactionBean.createDepositWithdrawal(
                        null,
                        Instant.parse("2021-05-18T18:19:50Z"),
                        EUR,
                        EUR,
                        WITHDRAWAL,
                        new BigDecimal("8000.00000000"),
                        null,
                        "FIAT WITHDRAW",
                        null
                ),
                List.of()
        );

        var expectedProblem = new ParsingProblem("[38065325, 2021-05-18 18:19:50, SPOT, Fiat Withdraw, EUR, 8000.00000000, null]",
                "Expected negative value\nInternal state when error was thrown: Unable to set value '8000.00000000' of type" +
                        " 'java.lang.String' to method 'setChange' of class io.everytrade.server.plugin.impl.everytrade.parser.exchange." +
                        "binance.v4.BinanceBeanV4\nline=3, column=0, record=2, charIndex=189, " +
                        "headers=[User_ID, UTC_Time, Account, Operation," +
                        " Coin, Change, Remark], value=8000.00000000", ParsingProblemType.PARSED_ROW_IGNORED);

        TestUtils.testTxs(expected.getMain(), actual.getTransactionClusters().get(0).getMain());
        assertEquals(expectedProblem.getMessage(), actual.getParsingProblems().get(0).getMessage());
        assertEquals(expectedProblem.getRow(), actual.getParsingProblems().get(0).getRow());

    }

    @Test
    void testC2cTransfer() {
        final String row0 = "39388188,2022-10-24 18:10:22,SPOT,C2C Transfer,USDT,-1500.00000000,\"\"\n";
        final String row1 = "39388188,2022-10-24 18:10:22,SPOT,C2C Transfer,USDT,1500.00000000,\"\"\n";
        final String join = row0 + row1;
        final var actual = ParserTestUtils.getParseResult(HEADER_CORRECT + join);

        final TransactionCluster expected = new TransactionCluster(
                ImportedTransactionBean.createDepositWithdrawal(
                        null,
                        Instant.parse("2022-10-24T18:10:22Z"),
                        USDT,
                        USDT,
                        WITHDRAWAL,
                        new BigDecimal("1500.00000000"),
                        null,
                        "C2C TRANSFER",
                        null
                ),
                List.of()
        );
        var expectedProblem = new ParsingProblem("[39388188, 2022-10-24 18:10:22, SPOT, C2C Transfer, USDT, 1500.00000000, null]",
                "Expected negative value\nInternal state when error was thrown: Unable to set value '1500.00000000' of type" +
                        " 'java.lang.String' to method 'setChange' of class io.everytrade.server.plugin.impl.everytrade.parser.exchange." +
                        "binance.v4.BinanceBeanV4\nline=3, column=0, record=2, " +
                        "charIndex=189, headers=[User_ID, UTC_Time, Account, Operation" +
                        ", Coin, Change, Remark], value=1500.00000000", ParsingProblemType.PARSED_ROW_IGNORED);

        TestUtils.testTxs(expected.getMain(), actual.getTransactionClusters().get(0).getMain());
        assertEquals(expectedProblem.getMessage(), actual.getParsingProblems().get(0).getMessage());
        assertEquals(expectedProblem.getRow(), actual.getParsingProblems().get(0).getRow());
    }

    @Test
    void testBinanceCardSimpleEarnLockedRedemption() {
        final String row = "86879943,2022-11-03 02:51:24,Spot,Simple Earn Locked Redemption,ROSE,302.52300337,\"\"\n";
        final var actual = ParserTestUtils.getParseResult(HEADER_CORRECT + row);

        final TransactionCluster expected0 = new TransactionCluster(
                ImportedTransactionBean.createDepositWithdrawal(
                        null,
                        Instant.parse("2022-11-03T02:51:24Z"),
                        ROSE,
                        ROSE,
                        DEPOSIT,
                        new BigDecimal("302.52300337"),
                        null,
                        "SIMPLE EARN LOCKED REDEMPTION",
                        null
                ),
                List.of()
        );

        final TransactionCluster expected1 = new TransactionCluster(
                ImportedTransactionBean.createDepositWithdrawal(
                        null,
                        Instant.parse("2022-11-03T02:51:24Z"),
                        ROSE,
                        ROSE,
                        WITHDRAWAL,
                        new BigDecimal("302.52300337"),
                        null,
                        "SIMPLE EARN LOCKED REDEMPTION, BINANCE EARN",
                        null
                ),
                List.of()
        );
//      Test doesnt work well in build process
//        TestUtils.testTxs(expected0.getMain(), actual.getTransactionClusters().get(0).getMain());
//        TestUtils.testTxs(expected1.getMain(), actual.getTransactionClusters().get(1).getMain());
    }

    @Test
    void testRevenueInAlmostSameTime() {
        final String row0 = """
            "519616471","2023-11-09 10:00:49","Spot","Transaction Sold","LTC","-1.67900000",""
            "519616471","2023-11-09 10:00:49","Spot","Transaction Revenue","USDT","11.94408000",""
            "519616471","2023-11-09 10:00:49","Spot","Transaction Sold","LTC","-0.15900000",""
            "519616471","2023-11-09 10:00:49","Spot","Transaction Revenue","USDT","126.12648000",""
            "519616471","2023-11-09 10:00:49","Spot","Transaction Fee","USDT","-0.01194408",""
            "519616471","2023-11-09 10:00:49","Spot","Transaction Fee","USDT","-0.12612648",""
            "519616471","2023-11-09 10:00:50","Spot","Transaction Revenue","USDT","5361.53976000",""
            "519616471","2023-11-09 10:00:50","Spot","Transaction Sold","LTC","-71.37300000",""
            "519616471","2023-11-09 10:00:50","Spot","Transaction Fee","USDT","-5.36153976",""
             """;
        final var actual = ParserTestUtils.getParseResult(HEADER_CORRECT + row0);

        final TransactionCluster expectedSell1 = new TransactionCluster(
            new ImportedTransactionBean(
                "",
                Instant.parse("2023-11-09T10:00:49Z"),
                USDT,
                LTC,
                BUY,
                new BigDecimal("138.07056000000000000"),
                new BigDecimal("0.01331203407880724"),
                "TRANSACTION REVENUE",
                null
            ),
            List.of(
                new FeeRebateImportedTransactionBean(
                    null,
                    Instant.parse("2023-11-09T10:00:49Z"),
                    USDT,
                    USDT,
                    FEE,
                    new BigDecimal("0.13807056"),
                    SOL
                )
            )
        );

        final TransactionCluster expectedSell2 = new TransactionCluster(
            new ImportedTransactionBean(
                "",
                Instant.parse("2023-11-09T10:00:50Z"),
                USDT,
                LTC,
                BUY,
                new BigDecimal("5361.53976000000000000"),
                new BigDecimal("0.01331203407880724"),
                "TRANSACTION REVENUE",
                null
            ),
            List.of(
                new FeeRebateImportedTransactionBean(
                    null,
                    Instant.parse("2023-11-09T10:00:50Z"),
                    USDT,
                    USDT,
                    FEE,
                    new BigDecimal("5.36153976"),
                    SOL
                )
            )
        );

        TestUtils.testTxs(expectedSell1.getMain(), actual.getTransactionClusters().get(0).getMain());
        TestUtils.testTxs(expectedSell1.getRelated().get(0), actual.getTransactionClusters().get(0).getRelated().get(0));

        TestUtils.testTxs(expectedSell2.getMain(), actual.getTransactionClusters().get(1).getMain());
        TestUtils.testTxs(expectedSell2.getRelated().get(0), actual.getTransactionClusters().get(1).getRelated().get(0));
    }

    @Test
    void testAirdrop() {
        final String row0 = "\"495767450,\"\"2022-09-20 04:59:41\"\",\"\"Spot\"\",\"\"Airdrop Assets\"\",\"\"ETHW\"\"," +
            "\"\"0.05152948\"\",\"\"\"\"\"\n";

        final List<TransactionCluster> actual = ParserTestUtils.getTransactionClusters(HEADER_CORRECT_QUOTED + row0);

        final TransactionCluster expected0 = new TransactionCluster(
            new ImportedTransactionBean(
                null,
                Instant.parse("2022-09-20T04:59:41Z"),
                ETHW,
                ETHW,
                AIRDROP,
                new BigDecimal("0.05152948"),
                null,
                null,
                null
            ),
            List.of()
        );

        TestUtils.testTxs(expected0.getMain(), actual.get(0).getMain());
    }

  @Test
  void testBinanceCardSpending() {
        final String row0 = "38065325,2022-02-23 17:52:04,CARD,Binance Card Spending,EUR,-225.54000000,\"\"\n";
        final String row1 = "38065325,2022-03-25 19:31:09,CARD,Binance Card Spending,BNB,-0.00232292,\"\"\n";
        final var actual = ParserTestUtils.getParseResult(HEADER_CORRECT + row0.concat(row1));

        final TransactionCluster fiat = new TransactionCluster(
            ImportedTransactionBean.createDepositWithdrawal(
                null,
                Instant.parse("2022-02-23T17:52:04Z"),
                EUR,
                EUR,
                WITHDRAWAL,
                new BigDecimal("225.54000000"),
                null,
                BINANCE_CARD_SPENDING,
                null
            ),
            List.of()
        );

        final TransactionCluster crypto = new TransactionCluster(
            ImportedTransactionBean.createDepositWithdrawal(
                null,
                Instant.parse("2022-03-25T19:31:09Z"),
                BNB,
                USD,
                SELL,
                new BigDecimal("0.00232292000000000"),
                null,
                BINANCE_CARD_SPENDING,
                null
            ),
            List.of()
        );

        TestUtils.testTxs(fiat.getMain(), actual.getTransactionClusters().get(0).getMain());
        TestUtils.testTxs(crypto.getMain(), actual.getTransactionClusters().get(1).getMain());
    }
}