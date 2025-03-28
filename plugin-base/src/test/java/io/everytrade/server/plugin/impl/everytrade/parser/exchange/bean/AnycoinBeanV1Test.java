package io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean;

import io.everytrade.server.plugin.api.parser.ImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.ParseResult;
import io.everytrade.server.plugin.api.parser.ParsingProblem;
import io.everytrade.server.plugin.api.parser.ParsingProblemType;
import io.everytrade.server.plugin.api.parser.TransactionCluster;
import io.everytrade.server.plugin.impl.everytrade.parser.EverytradeCsvMultiParser;
import io.everytrade.server.test.TestUtils;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static io.everytrade.server.model.Currency.ADA;
import static io.everytrade.server.model.Currency.ATOM;
import static io.everytrade.server.model.Currency.BTC;
import static io.everytrade.server.model.Currency.CZK;
import static io.everytrade.server.model.Currency.ETH;
import static io.everytrade.server.model.Currency.ETH2;
import static io.everytrade.server.model.Currency.SAND;
import static io.everytrade.server.model.Currency.SOL;
import static io.everytrade.server.model.TransactionType.BUY;
import static io.everytrade.server.model.TransactionType.DEPOSIT;
import static io.everytrade.server.model.TransactionType.SELL;
import static io.everytrade.server.model.TransactionType.STAKE;
import static io.everytrade.server.model.TransactionType.STAKING_REWARD;
import static io.everytrade.server.model.TransactionType.UNSTAKE;
import static io.everytrade.server.model.TransactionType.WITHDRAWAL;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class AnycoinBeanV1Test {

    public static final String HEADER_CORRECT = "Date,Type,Amount,Currency,Order ID\n";

    @Test
    void testBuy() {
        final String row0 = "2021-04-10T18:16:50.367Z,trade payment,-1000,CZK,113180\n";
        final String row1 = "2021-04-10T18:28:12.885Z,trade fill,0.00075667,BTC,113180\n";
        final List<TransactionCluster> actual = ParserTestUtils.getTransactionClusters(HEADER_CORRECT + row0.concat(row1));

        final TransactionCluster expected = new TransactionCluster(
                new ImportedTransactionBean(
                        "113180",
                        Instant.parse("2021-04-10T18:28:12.885Z"),
                        BTC,
                        CZK,
                        BUY,
                        new BigDecimal("0.00075667"),
                        new BigDecimal("1321580.08114501698230404"),
                        null,
                        null
                ),
                List.of()
        );

        TestUtils.testTxs(expected.getMain(), actual.get(0).getMain());
    }

    @Test
    void testSell() {
        final String row0 = "2021-09-10T08:46:44.616Z,trade payment,-52,ADA,258362\n";
        final String row1 = "2021-09-10T08:46:47.763Z,trade fill,2676,CZK,258362\n";
        final List<TransactionCluster> actual = ParserTestUtils.getTransactionClusters(HEADER_CORRECT + row0.concat(row1));

        final TransactionCluster expected = new TransactionCluster(
                new ImportedTransactionBean(
                        "258362",
                        Instant.parse("2021-09-10T08:46:47.763Z"),
                        ADA,
                        CZK,
                        SELL,
                        new BigDecimal("52"),
                        new BigDecimal("51.46153846153846154"),
                        null,
                        null
                ),
                List.of()
        );

        TestUtils.testTxs(expected.getMain(), actual.get(0).getMain());
    }

    @Test
    void testSell2() {
        final String row0 = "2021-11-24T03:02:35.826Z,trade payment,-177.63419113,SAND,315998\n";
        final String row1 = "2021-11-24T03:02:39.486Z,trade fill,24899.78,CZK,315998\n";
        final List<TransactionCluster> actual = ParserTestUtils.getTransactionClusters(HEADER_CORRECT + row0.concat(row1));

        final TransactionCluster expected = new TransactionCluster(
                new ImportedTransactionBean(
                        "315998",
                        Instant.parse("2021-11-24T03:02:39.486Z"),
                        SAND,
                        CZK,
                        SELL,
                        new BigDecimal("177.63419113"),
                        new BigDecimal("140.17447790654963420"),
                        null,
                        null
                ),
                List.of()
        );

        TestUtils.testTxs(expected.getMain(), actual.get(0).getMain());
    }

    @Test
    void testDeposit() {
        final String row0 = "2021-04-23T10:28:16.196Z,deposit,500,CZK\n";
        final List<TransactionCluster> actual = ParserTestUtils.getTransactionClusters(HEADER_CORRECT + row0);

        final TransactionCluster expected = new TransactionCluster(
                ImportedTransactionBean.createDepositWithdrawal(
                        null,
                        Instant.parse("2021-04-23T10:28:16.196Z"),
                        CZK,
                        CZK,
                        DEPOSIT,
                        new BigDecimal("500"),
                        null
                ),
                List.of()
        );

        TestUtils.testTxs(expected.getMain(), actual.get(0).getMain());
    }

    @Test
    void testWithdrawal() {
        final String row0 = "2022-01-06T11:51:27.489Z,withdrawal,-0.07070279,BTC,\n";
        final List<TransactionCluster> actual = ParserTestUtils.getTransactionClusters(HEADER_CORRECT + row0);

        final TransactionCluster expected = new TransactionCluster(
                ImportedTransactionBean.createDepositWithdrawal(
                        null,
                        Instant.parse("2022-01-06T11:51:27.489Z"),
                        BTC,
                        BTC,
                        WITHDRAWAL,
                        new BigDecimal("0.07070279"),
                        null
                ),
                List.of()
        );

        TestUtils.testTxs(expected.getMain(), actual.get(0).getMain());
    }

    @Test
    void testTradeRefund() {
        final String row0 = "2023-06-03T13:50:07.377Z,trade payment,-0.00076119,BTC,896136\n";
        final String row1 = "2023-06-04T05:38:11.798Z,trade refund,0.00076119,BTC,896136\n";
        ParseResult actual = ParserTestUtils.getParseResult(HEADER_CORRECT + row0.concat(row1));

        var expectedProblem = new ParsingProblem("line=3, 2023-06-04T05:38:11.798Z, trade refund, 0.00076119, BTC, 896136",
                "Ignored operation type: trade refund", ParsingProblemType.PARSED_ROW_IGNORED);

        assertEquals(expectedProblem.getMessage(), actual.getParsingProblems().get(0).getMessage());
    }

    @Test
    void testWithdrawal_Block_Unblock() {
        final String row0 = "2024-03-21T06:11:24.727Z,withdrawal_block,-0.01013927,BTC\n";
        final String row1 = "2024-03-21T07:21:59.752Z,withdrawal_unblock,0.01013927,BTC\n";
        final String row2 = "2024-03-21T07:21:59.862Z,withdrawal,-0.01013927,BTC\n";

        var actual = ParserTestUtils.getParseResult(HEADER_CORRECT + row0.concat(row1).concat(row2));

        final TransactionCluster expected = new TransactionCluster(
                ImportedTransactionBean.createDepositWithdrawal(
                        null,
                        Instant.parse("2024-03-21T07:21:59.862Z"),
                        BTC,
                        BTC,
                        WITHDRAWAL,
                        new BigDecimal("0.01013927"),
                        null
                ),
                List.of()
        );

        TestUtils.testTxs(expected.getMain(), actual.getTransactionClusters().get(0).getMain());

        var expectedProblem = new ParsingProblem("line=2, 2024-03-21T06:11:24.727Z, withdrawal_block, -0.01013927, BTC, null",
                "Ignored operation type: withdrawal_block", ParsingProblemType.PARSED_ROW_IGNORED);
        var expectedProblem1 = new ParsingProblem("line=3, 2024-03-21T07:21:59.752Z, withdrawal_unblock, 0.01013927, BTC, null",
                "Ignored operation type: withdrawal_unblock", ParsingProblemType.PARSED_ROW_IGNORED);

        assertEquals(expectedProblem.getMessage(), actual.getParsingProblems().get(0).getMessage());
        assertEquals(expectedProblem1.getMessage(), actual.getParsingProblems().get(1).getMessage());
    }

    @Test
    void testStake() {
        final String row0 = "2022-11-07T14:39:54.400Z,stake,-1.43092234,ATOM\n";
        final String row1 = "2022-11-07T14:45:15.967Z,stake,1.43092234,ATOM.S\n";
        var actual = ParserTestUtils.getParseResult(HEADER_CORRECT + row0.concat(row1));

        final TransactionCluster expected = new TransactionCluster(
                new ImportedTransactionBean(
                        null,
                        Instant.parse("2022-11-07T14:39:54.400Z"),
                        ATOM,
                        ATOM,
                        STAKE,
                        new BigDecimal("1.43092234"),
                        null,
                        null,
                        null
                ),
                List.of()
        );

        TestUtils.testTxs(expected.getMain(), actual.getTransactionClusters().get(0).getMain());
    }

    @Test
    void testUnstake() {
        final String row0 = "2022-11-27T07:25:31.020Z,unstake,-0.38908456,ATOM.S\n";
        final String row1 = "2022-11-27T07:29:33.918Z,unstake,0.38908456,ATOM\n";
        var actual = ParserTestUtils.getParseResult(HEADER_CORRECT + row0.concat(row1));

        final TransactionCluster expected = new TransactionCluster(
                new ImportedTransactionBean(
                        null,
                        Instant.parse("2022-11-27T07:25:31.020Z"),
                        ATOM,
                        ATOM,
                        UNSTAKE,
                        new BigDecimal("0.38908456"),
                        null,
                        null,
                        null
                ),
                List.of()
        );

        TestUtils.testTxs(expected.getMain(), actual.getTransactionClusters().get(0).getMain());
    }

    @Test
    void testStakeReward() {
        final String row0 = "2022-10-29T14:24:12.531Z,stake_reward,0.00334145,SOL.S\n";
        var actual = ParserTestUtils.getTransactionClusters(HEADER_CORRECT + row0);

        final TransactionCluster expectedStake = new TransactionCluster(
                new ImportedTransactionBean(
                        null,
                        Instant.parse("2022-10-29T14:24:12.531Z"),
                        SOL,
                        SOL,
                        STAKING_REWARD,
                        new BigDecimal("0.00334145"),
                        null,
                        null,
                        null
                ),
                List.of()
        );

        final TransactionCluster expectedStakeReward = new TransactionCluster(
                new ImportedTransactionBean(
                        null,
                        Instant.parse("2022-10-29T14:24:13.531Z"), // +1 second
                        SOL,
                        SOL,
                        STAKE,
                        new BigDecimal("0.00334145"),
                        null,
                        null,
                        null
                ),
                List.of()
        );


        TestUtils.testTxs(expectedStakeReward.getMain(), actual.get(0).getMain());
        TestUtils.testTxs(expectedStake.getMain(), actual.get(1).getMain());
    }

    @Test
    void testStakeETH2() {
        final String row0 = "2024-01-01T09:32:02.361Z,stake,-0.0105416,ETH,\n";
        final String row1 = "2024-01-01T09:32:49.003Z,stake,0.0105416,ETH2.S\n";
        final List<TransactionCluster> actual = ParserTestUtils.getTransactionClusters(HEADER_CORRECT + row0.concat(row1));

        final TransactionCluster expectedBuy = new TransactionCluster(
                new ImportedTransactionBean(
                        "",
                        Instant.parse("2024-01-01T09:32:02.361Z"),
                        ETH,
                        ETH,
                        BUY,
                        new BigDecimal("0.0105416"),
                        new BigDecimal("1.00000000000000000"),
                        null,
                        null
                ),
                List.of()
        );
        final TransactionCluster expectedStake = new TransactionCluster(
                new ImportedTransactionBean(
                        "",
                        Instant.parse("2024-01-01T09:32:49.003Z"),
                        ETH,
                        ETH,
                        STAKE,
                        new BigDecimal("0.0105416"),
                        null,
                        null,
                        null
                ),
                List.of()
        );

        TestUtils.testTxs(expectedBuy.getMain(), actual.get(0).getMain());
        TestUtils.testTxs(expectedStake.getMain(), actual.get(1).getMain());
    }

    @Test
    void testUnstakeETH2() {
        final String row0 = "2024-03-15T17:26:14.826Z,unstake,-0.21907795,ETH2.S\n";
        final String row1 = "2024-03-15T17:27:01.354Z,unstake,0.21907795,ETH\n";
        final List<TransactionCluster> actual = ParserTestUtils.getTransactionClusters(HEADER_CORRECT + row0.concat(row1));

        final TransactionCluster expectedBuy = new TransactionCluster(
                new ImportedTransactionBean(
                        "",
                        Instant.parse("2024-03-15T17:27:01.354Z"),
                        ETH,
                        ETH,
                        BUY,
                        new BigDecimal("0.21907795"),
                        new BigDecimal("1.00000000000000000"),
                        null,
                        null
                ),
                List.of()
        );
        final TransactionCluster expectedStake = new TransactionCluster(
                new ImportedTransactionBean(
                        "",
                        Instant.parse("2024-03-15T17:26:14.826Z"),
                        ETH,
                        ETH,
                        UNSTAKE,
                        new BigDecimal("0.21907795"),
                        null,
                        null,
                        null
                ),
                List.of()
        );

        TestUtils.testTxs(expectedBuy.getMain(), actual.get(0).getMain());
        TestUtils.testTxs(expectedStake.getMain(), actual.get(1).getMain());
    }

    @Test
    void testStakeRewardETH2() {
        final String row0 = "2024-01-04T01:07:53.536Z,stake_reward,0.000117,ETH2.S\n";
        var actual = ParserTestUtils.getTransactionClusters(HEADER_CORRECT + row0);

        final TransactionCluster expectedStakeReward = new TransactionCluster(
                new ImportedTransactionBean(
                        null,
                        Instant.parse("2024-01-04T01:07:53.536Z"),
                        ETH,
                        ETH,
                        STAKING_REWARD,
                        new BigDecimal("0.000117"),
                        null,
                        null,
                        null
                ),
                List.of()
        );

        final TransactionCluster expectedStake = new TransactionCluster(
                new ImportedTransactionBean(
                        null,
                        Instant.parse("2024-01-04T01:07:54.536Z"), // +1 second
                        ETH,
                        ETH,
                        STAKE,
                        new BigDecimal("0.000117"),
                        null,
                        null,
                        null
                ),
                List.of()
        );


        TestUtils.testTxs(expectedStake.getMain(), actual.get(0).getMain());
        TestUtils.testTxs(expectedStakeReward.getMain(), actual.get(1).getMain());
    }
}
