package io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean;

import io.everytrade.server.model.TransactionType;
import io.everytrade.server.plugin.api.parser.FeeRebateImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.ImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.ParsingProblem;
import io.everytrade.server.plugin.api.parser.TransactionCluster;
import io.everytrade.server.plugin.impl.everytrade.parser.exception.ParsingProcessException;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.ExchangeBean;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static io.everytrade.server.model.Currency.BTC;
import static io.everytrade.server.model.Currency.CZK;
import static io.everytrade.server.model.Currency.EUR;
import static io.everytrade.server.model.Currency.SOL;
import static io.everytrade.server.model.TransactionType.AIRDROP;
import static io.everytrade.server.model.TransactionType.BUY;
import static io.everytrade.server.model.TransactionType.EARNING;
import static io.everytrade.server.model.TransactionType.FEE;
import static io.everytrade.server.model.TransactionType.FORK;
import static io.everytrade.server.model.TransactionType.REBATE;
import static io.everytrade.server.model.TransactionType.REWARD;
import static io.everytrade.server.model.TransactionType.SELL;
import static io.everytrade.server.model.TransactionType.STAKE;
import static io.everytrade.server.model.TransactionType.STAKING_REWARD;
import static io.everytrade.server.model.TransactionType.UNSTAKE;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.ExchangeBean.REBATE_UID_PART;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class EveryTradeBeanV3_2Test {
    private static final String HEADER_CORRECT =
        "UID;DATE;SYMBOL;ACTION;QUANTITY;UNIT_PRICE;VOLUME_QUOTE;FEE;FEE_CURRENCY;REBATE;REBATE_CURRENCY;ADDRESS_FROM;ADDRESS_TO;NOTE;" +
            "LABELS\n";

    @Test
    void testCorrectHeader() {
        try {
            ParserTestUtils.testParsing(HEADER_CORRECT);
        } catch (ParsingProcessException e) {
            fail("Unexpected exception has been thrown.");
        }
    }

    @Test
    void testWrongHeader() {
        final String headerWrong = "UID;DATE;SYMBOL;ACTION;QUANTY;PRICE;XFEE;FEE_CURRENCY;REBATE;REBATE_CURRENCY;ADDRESS_FROM;" +
            "ADDRESS_TO\n";
        try {
            ParserTestUtils.testParsing(headerWrong);
            fail("No expected exception has been thrown.");
        } catch (ParsingProcessException ignored) {
        }
    }

    @Test
    void testCorrectParsingRawTransactionBuy() {
        final String row = "1;12.1.2022 14:01:00;BTC/CZK;BUY;0,13;500000;65000;;;;;;;nnnnnn;Label1\n";
        final TransactionCluster actual = ParserTestUtils.getTransactionCluster(HEADER_CORRECT + row);
        final TransactionCluster expected = new TransactionCluster(
            new ImportedTransactionBean(
                "1",
                Instant.parse("2022-01-12T14:01:00Z"),
                BTC,
                CZK,
                BUY,
                new BigDecimal("0.13"),
                new BigDecimal("500000.00000000"),
                "nnnnnn",
                null,
                "Label1"
            ),
            List.of()
        );
        ParserTestUtils.checkEqual(expected, actual);
    }

    @Test
    void testCorrectParsingRawTransactionBuyWithFee() {
        final String row = "2;12.1.2022 14:02:00;BTC/CZK;BUY;0,13;500000;65000;140;CZK;;;;;nnnnnn;Label1\n";
        final TransactionCluster actual = ParserTestUtils.getTransactionCluster(HEADER_CORRECT + row);
        final TransactionCluster expected = new TransactionCluster(
            new ImportedTransactionBean(
                "2",
                Instant.parse("2022-01-12T14:02:00Z"),
                BTC,
                CZK,
                BUY,
                new BigDecimal("0.13"),
                new BigDecimal("500000.00000000"),
                "nnnnnn",
                null,
                "Label1"
            ),
            List.of(
                new FeeRebateImportedTransactionBean(
                    "2-fee",
                    Instant.parse("2022-01-12T14:02:00Z"),
                    CZK,
                    CZK,
                    FEE,
                    new BigDecimal("140"),
                    CZK,
                    null,
                    null,
                    "Label1"
                )
            )
        );
        ParserTestUtils.checkEqual(expected, actual);
    }

    @Test
    void testCorrectParsingRawTransactionSell() {
        final String row = "5;12.1.2022 14:05:00;BTC/EUR;SELL;0,06;20000;1200;;;;;;;nnnnnn;Label1\n";
        final TransactionCluster actual = ParserTestUtils.getTransactionCluster(HEADER_CORRECT + row);
        final TransactionCluster expected = new TransactionCluster(
            new ImportedTransactionBean(
                "5",
                Instant.parse("2022-01-12T14:05:00Z"),
                BTC,
                EUR,
                SELL,
                new BigDecimal("0.06"),
                new BigDecimal("20000.00000000"),
                "nnnnnn",
                null,
                "Label1"
            ),
            List.of()
        );
        ParserTestUtils.checkEqual(expected, actual);
    }

    @Test
    void testCorrectParsingRawTransactionSellWithRebate() {
        final String row = "6;12.1.2022 14:06:00;BTC/EUR;SELL;0,06;20000;1200;;;5.7;EUR;;;nnnnnn;Label1\n";
        List<ImportedTransactionBean> related = new ArrayList<>();
        related.add(new FeeRebateImportedTransactionBean(
            "6" + REBATE_UID_PART,
            Instant.parse("2022-01-12T14:06:00Z"),
            EUR,
            EUR,
            REBATE,
            new BigDecimal("5.7"),
            EUR,
            null,
            null,
            "Label1"
        ));
        final TransactionCluster actual = ParserTestUtils.getTransactionCluster(HEADER_CORRECT + row);
        final TransactionCluster expected = new TransactionCluster(
            new ImportedTransactionBean(
                "6",
                Instant.parse("2022-01-12T14:06:00Z"),
                BTC,
                EUR,
                SELL,
                new BigDecimal("0.06"),
                new BigDecimal("20000.00000000"),
                "nnnnnn",
                null,
                "Label1"
            ),
            related
        );
        ParserTestUtils.checkEqual(expected, actual);
    }

    @Test
    void testCorrectParsingRawTransactionFeeUnrelated() {
        final String row = "9;12.1.2022 14:09:00;CZK;FEE;;;;100;CZK;;;;;nnnnnn;Label1\n";
        final TransactionCluster actual = ParserTestUtils.getTransactionCluster(HEADER_CORRECT + row);
        final TransactionCluster expected = new TransactionCluster(
            new FeeRebateImportedTransactionBean(
                "9",
                Instant.parse("2022-01-12T14:09:00Z"),
                CZK,
                CZK,
                FEE,
                new BigDecimal("100"),
                CZK,
                "nnnnnn",
                null,
                "Label1"
            ),
            List.of()
        );
        ParserTestUtils.checkEqualUnrelated(expected, actual);
    }

    @Test
    void testCorrectParsingRawTransactionRebateUnrelated() {
        final String row = "11;12.1.2022 14:11:00;CZK;REBATE;;;;;;100;CZK;;;nnnnnn;Label1\n";
        final TransactionCluster actual = ParserTestUtils.getTransactionCluster(HEADER_CORRECT + row);
        final TransactionCluster expected = new TransactionCluster(
            new FeeRebateImportedTransactionBean(
                "11",
                Instant.parse("2022-01-12T14:11:00Z"),
                CZK,
                CZK,
                REBATE,
                new BigDecimal("100"),
                CZK,
                "nnnnnn",
                null,
                "Label1"
            ),
            List.of()
        );
        ParserTestUtils.checkEqualUnrelated(expected, actual);
    }

    @Test
    void testCorrectParsingRawTransactionFeeUnrelated2() {
        final String row = "5;05.12.2023 00:00:00;SOL;FEE;\"6,60E-06\";;;;;;;7G7A9R894GcG1HyWtUmH4BCvknvsukL39N9E8NSHFhjH;3wjAoceD4w2P7Rf7ERDLyfTq61pFE7BAQM4Jdgjrhn24;;\n";
        final TransactionCluster actual = ParserTestUtils.getTransactionCluster(HEADER_CORRECT + row);
        final TransactionCluster expected = new TransactionCluster(
            new FeeRebateImportedTransactionBean(
                "5",
                Instant.parse("2023-12-05T00:00:00Z"),
                SOL,
                SOL,
                FEE,
                new BigDecimal("0.00000660"),
                SOL,
                null,
                null,
                null
            ),
            List.of()
        );
        ParserTestUtils.checkEqualUnrelated(expected, actual);
    }

    @Test
    void testUnknownPair() {
        final String row = "1;12.1.2022 14:01:00;XXX/CZK;BUY;0,13;500000;65000;;;;;;;nnnnnn;Label1\n";
        final ParsingProblem parsingProblem = ParserTestUtils.getParsingProblem(HEADER_CORRECT + row);
        final String error = parsingProblem.getMessage();
        assertTrue(error.contains("XXX/CZK"));
    }

    @Test
    void testIgnoredTransactionType() {
        // test wrong unsupported tx type
        final String row = "1;12.1.2022 14:01:00;BTC/CZK;WITHDRAWZ;0,13;500000;65000;;;;;;;nnnnnn;Label1\n";
        final ParsingProblem parsingProblem = ParserTestUtils.getParsingProblem(HEADER_CORRECT + row);
        assertTrue(parsingProblem.getMessage().contains(ExchangeBean.UNSUPPORTED_TRANSACTION_TYPE.concat("WITHDRAWZ"))
        );
    }

    @Test
    void testCorrectParsingRawTransactionDeposit() {
        final String row = "14;12.1.2022 14:14:00;BTC;DEPOSIT;0,01;;;;;;;xxxxxxx;;nnnnnn;Label1\n";
        final TransactionCluster actual = ParserTestUtils.getTransactionCluster(HEADER_CORRECT + row);
        final TransactionCluster expected = new TransactionCluster(
            ImportedTransactionBean.createDepositWithdrawal(
                "14",
                Instant.parse("2022-01-12T14:14:00Z"),
                BTC,
                BTC,
                TransactionType.DEPOSIT,
                new BigDecimal("0.01"),
                "xxxxxxx",
                "nnnnnn",
                "Label1"
            ),
            List.of()
        );
        ParserTestUtils.checkEqual(expected, actual);
    }

    @Test
    void testCorrectParsingRawTransactionDepositWithFeeBase() {
        var row = "15;12.1.2022 14:15:00;CZK;DEPOSIT;90;;;10;CZK;;;xxxxxxx;;nnnnnn;Label1\n";
        var actual = ParserTestUtils.getTransactionCluster(HEADER_CORRECT + row);
        var expected = new TransactionCluster(
            ImportedTransactionBean.createDepositWithdrawal(
                "15",
                Instant.parse("2022-01-12T14:15:00Z"),
                CZK,
                CZK,
                TransactionType.DEPOSIT,
                new BigDecimal("90"),
                "xxxxxxx",
                "nnnnnn",
                "Label1"

            ),
            List.of(
                new FeeRebateImportedTransactionBean(
                    "15-fee",
                    Instant.parse("2022-01-12T14:15:00Z"),
                    CZK,
                    CZK,
                    FEE,
                    new BigDecimal("10"),
                    CZK,
                    null,
                    null,
                    "Label1"
                )
            )
        );
        ParserTestUtils.checkEqual(expected, actual);
    }

    @Test
    void testCorrectParsingRawTransactionWithdrawal() {
        final String row = "18;12.1.2022 14:18:00;CZK;WITHDRAWAL;90;;;;;;;;xxxxxxx;nnnnnn;Label1\n";
        final TransactionCluster actual = ParserTestUtils.getTransactionCluster(HEADER_CORRECT + row);
        final TransactionCluster expected = new TransactionCluster(
            ImportedTransactionBean.createDepositWithdrawal(
                "18",
                Instant.parse("2022-01-12T14:18:00Z"),
                CZK,
                CZK,
                TransactionType.WITHDRAWAL,
                new BigDecimal("90"),
                "xxxxxxx",
                "nnnnnn",
                "Label1"
            ),
            List.of()
        );
        ParserTestUtils.checkEqual(expected, actual);
    }


    @Test
    void testCorrectParsingOtherTransactionWithdrawalWithFee() {
        var row = "20;12.1.2022 14:20:00;CZK;WITHDRAWAL;100;;;10;CZK;;;;xxxxxxx;nnnnnn;Label1\n";
        var actual = ParserTestUtils.getTransactionCluster(HEADER_CORRECT + row);
        var expected = new TransactionCluster(
            ImportedTransactionBean.createDepositWithdrawal(
                "20",
                Instant.parse("2022-01-12T14:20:00Z"),
                CZK,
                CZK,
                TransactionType.WITHDRAWAL,
                new BigDecimal("100"),
                "xxxxxxx",
                "nnnnnn",
                "Label1"
            ),
            List.of(
                new FeeRebateImportedTransactionBean(
                    "20-fee",
                    Instant.parse("2022-01-12T14:20:00Z"),
                    CZK,
                    CZK,
                    FEE,
                    new BigDecimal("10"),
                    CZK,
                    null,
                    null,
                    "Label1"
                )
            )
        );
        ParserTestUtils.checkEqual(expected, actual);
    }

    @Test
    void testCorrectParsingRawTransactionStake() {
        final String row = "23;12.1.2022 14:23:00;BTC;STAKE;0,01;;;;;;;;;nnnnnn;Label1\n";
        final TransactionCluster actual = ParserTestUtils.getTransactionCluster(HEADER_CORRECT + row);
        final TransactionCluster expected = new TransactionCluster(
            ImportedTransactionBean.createDepositWithdrawal(
                "23",
                Instant.parse("2022-01-12T14:23:00Z"),
                BTC,
                BTC,
                STAKE,
                new BigDecimal("0.01"),
                null,
                "nnnnnn",
                "Label1"
            ),
            List.of()
        );
        ParserTestUtils.checkEqual(expected, actual);
    }

    @Test
    void testCorrectParsingRawTransactionUnstake() {
        final String row = "23;12.1.2022 14:23:00;BTC;UNSTAKE;0,01;;;;;;;;;nnnnnn;Label1\n";
        final TransactionCluster actual = ParserTestUtils.getTransactionCluster(HEADER_CORRECT + row);
        final TransactionCluster expected = new TransactionCluster(
            ImportedTransactionBean.createDepositWithdrawal(
                "23",
                Instant.parse("2022-01-12T14:23:00Z"),
                BTC,
                BTC,
                UNSTAKE,
                new BigDecimal("0.01"),
                null,
                "nnnnnn",
                "Label1"
            ),
            List.of()
        );
        ParserTestUtils.checkEqual(expected, actual);
    }

    @Test
    void testCorrectParsingRawTransactionStakeReward() {
        final String row = "23;12.1.2022 14:23:00;BTC;STAKE REWARD;0,01;;;;;;;;;nnnnnn;Label1\n";
        final TransactionCluster actual = ParserTestUtils.getTransactionCluster(HEADER_CORRECT + row);
        final TransactionCluster expected = new TransactionCluster(
            ImportedTransactionBean.createDepositWithdrawal(
                "23",
                Instant.parse("2022-01-12T14:23:00Z"),
                BTC,
                BTC,
                STAKING_REWARD,
                new BigDecimal("0.01"),
                null,
                "nnnnnn",
                "Label1"
            ),
            List.of()
        );
        ParserTestUtils.checkEqual(expected, actual);
    }

    @Test
    void testCorrectParsingRawTransactionReward() {
        final String row = "23;12.1.2022 14:23:00;BTC;REWARD;0,01;;;;;;;;;nnnnnn;Label1\n";
        final TransactionCluster actual = ParserTestUtils.getTransactionCluster(HEADER_CORRECT + row);
        final TransactionCluster expected = new TransactionCluster(
            ImportedTransactionBean.createDepositWithdrawal(
                "23",
                Instant.parse("2022-01-12T14:23:00Z"),
                BTC,
                BTC,
                REWARD,
                new BigDecimal("0.01"),
                null,
                "nnnnnn",
                "Label1"
            ),
            List.of()
        );
        ParserTestUtils.checkEqual(expected, actual);
    }

    @Test
    void testCorrectParsingRawTransactionAirdrop() {
        final String row = "23;12.1.2022 14:23:00;BTC;AIRDROP;0,01;;;;;;;;;nnnnnn;Label1\n";
        final TransactionCluster actual = ParserTestUtils.getTransactionCluster(HEADER_CORRECT + row);
        final TransactionCluster expected = new TransactionCluster(
            ImportedTransactionBean.createDepositWithdrawal(
                "23",
                Instant.parse("2022-01-12T14:23:00Z"),
                BTC,
                BTC,
                AIRDROP,
                new BigDecimal("0.01"),
                null,
                "nnnnnn",
                "Label1"
            ),
            List.of()
        );
        ParserTestUtils.checkEqual(expected, actual);
    }

    @Test
    void testCorrectParsingRawTransactionEarn() {
        final String row = "23;12.1.2022 14:23:00;BTC;EARN;0,01;;;;;;;;;nnnnnn;Label1\n";
        final TransactionCluster actual = ParserTestUtils.getTransactionCluster(HEADER_CORRECT + row);
        final TransactionCluster expected = new TransactionCluster(
            ImportedTransactionBean.createDepositWithdrawal(
                "23",
                Instant.parse("2022-01-12T14:23:00Z"),
                BTC,
                BTC,
                EARNING,
                new BigDecimal("0.01"),
                null,
                "nnnnnn",
                "Label1"
            ),
            List.of()
        );
        ParserTestUtils.checkEqual(expected, actual);
    }

    @Test
    void testCorrectParsingRawTransactionFork() {
        final String row = "23;12.1.2022 14:23:00;BTC;FORK;0,01;;;;;;;;;nnnnnn;Label1\n";
        final TransactionCluster actual = ParserTestUtils.getTransactionCluster(HEADER_CORRECT + row);
        final TransactionCluster expected = new TransactionCluster(
            ImportedTransactionBean.createDepositWithdrawal(
                "23",
                Instant.parse("2022-01-12T14:23:00Z"),
                BTC,
                BTC,
                FORK,
                new BigDecimal("0.01"),
                null,
                "nnnnnn",
                "Label1"
            ),
            List.of()
        );
        ParserTestUtils.checkEqual(expected, actual);
    }

    @Test
    void testCorrectParsingRawTransactionStakeWithFee() {
        var row = "24;12.1.2022 14:24:00;BTC;STAKE;0,01;;;0,001;BTC;;;;;nnnnnn;Label1\n";
        var actual = ParserTestUtils.getTransactionCluster(HEADER_CORRECT + row);
        var expected = new TransactionCluster(
            ImportedTransactionBean.createDepositWithdrawal(
                "24",
                Instant.parse("2022-01-12T14:24:00Z"),
                BTC,
                BTC,
                STAKE,
                new BigDecimal("0.01"),
                null,
                "nnnnnn",
                "Label1"
            ),
            List.of(
                new FeeRebateImportedTransactionBean(
                    "24-fee",
                    Instant.parse("2022-01-12T14:24:00Z"),
                    BTC,
                    BTC,
                    FEE,
                    new BigDecimal("0.001"),
                    BTC,
                    null,
                    null,
                    "Label1"
                )
            )
        );
        ParserTestUtils.checkEqual(expected, actual);
    }


    @Test
    void testCorrectParsingRawTransactionUnstakeWithFee() {
        var row = "24;12.1.2022 14:24:00;BTC;UNSTAKE;0,01;;;0,001;BTC;;;;;nnnnnn;Label1\n";
        var actual = ParserTestUtils.getTransactionCluster(HEADER_CORRECT + row);
        var expected = new TransactionCluster(
            ImportedTransactionBean.createDepositWithdrawal(
                "24",
                Instant.parse("2022-01-12T14:24:00Z"),
                BTC,
                BTC,
                UNSTAKE,
                new BigDecimal("0.01"),
                null,
                "nnnnnn",
                "Label1"
            ),
            List.of(
                new FeeRebateImportedTransactionBean(
                    "24-fee",
                    Instant.parse("2022-01-12T14:24:00Z"),
                    BTC,
                    BTC,
                    FEE,
                    new BigDecimal("0.001"),
                    BTC,
                    null,
                    null,
                    "Label1"
                )
            )
        );
        ParserTestUtils.checkEqual(expected, actual);
    }


    @Test
    void testCorrectParsingRawTransactionStakeRewardWithFee() {
        var row = "24;12.1.2022 14:24:00;BTC;STAKE REWARD;0,01;;;0,001;BTC;;;;;nnnnnn;Label1\n";
        var actual = ParserTestUtils.getTransactionCluster(HEADER_CORRECT + row);
        var expected = new TransactionCluster(
            ImportedTransactionBean.createDepositWithdrawal(
                "24",
                Instant.parse("2022-01-12T14:24:00Z"),
                BTC,
                BTC,
                STAKING_REWARD,
                new BigDecimal("0.01"),
                null,
                "nnnnnn",
                "Label1"
            ),
            List.of(
                new FeeRebateImportedTransactionBean(
                    "24-fee",
                    Instant.parse("2022-01-12T14:24:00Z"),
                    BTC,
                    BTC,
                    FEE,
                    new BigDecimal("0.001"),
                    BTC,
                    null,
                    null,
                    "Label1"
                )
            )
        );
        ParserTestUtils.checkEqual(expected, actual);
    }


    @Test
    void testCorrectParsingRawTransactionRewardWithFee() {
        var row = "24;12.1.2022 14:24:00;BTC;REWARD;0,01;;;0,001;BTC;;;;;nnnnnn;Label1\n";
        var actual = ParserTestUtils.getTransactionCluster(HEADER_CORRECT + row);
        var expected = new TransactionCluster(
            ImportedTransactionBean.createDepositWithdrawal(
                "24",
                Instant.parse("2022-01-12T14:24:00Z"),
                BTC,
                BTC,
                REWARD,
                new BigDecimal("0.01"),
                null,
                "nnnnnn",
                "Label1"
            ),
            List.of(
                new FeeRebateImportedTransactionBean(
                    "24-fee",
                    Instant.parse("2022-01-12T14:24:00Z"),
                    BTC,
                    BTC,
                    FEE,
                    new BigDecimal("0.001"),
                    BTC,
                    null,
                    null,
                    "Label1"
                )
            )
        );
        ParserTestUtils.checkEqual(expected, actual);
    }


    @Test
    void testCorrectParsingRawTransactionStakeAirDropWithFee() {
        var row = "24;12.1.2022 14:24:00;BTC;AIRDROP;0,01;;;0,001;BTC;;;;;nnnnnn;Label1\n";
        var actual = ParserTestUtils.getTransactionCluster(HEADER_CORRECT + row);
        var expected = new TransactionCluster(
            ImportedTransactionBean.createDepositWithdrawal(
                "24",
                Instant.parse("2022-01-12T14:24:00Z"),
                BTC,
                BTC,
                AIRDROP,
                new BigDecimal("0.01"),
                null,
                "nnnnnn",
                "Label1"
            ),
            List.of(
                new FeeRebateImportedTransactionBean(
                    "24-fee",
                    Instant.parse("2022-01-12T14:24:00Z"),
                    BTC,
                    BTC,
                    FEE,
                    new BigDecimal("0.001"),
                    BTC,
                    null,
                    null,
                    "Label1"
                )
            )
        );
        ParserTestUtils.checkEqual(expected, actual);
    }


    @Test
    void testCorrectParsingRawTransactionEarnWithFee() {
        var row = "24;12.1.2022 14:24:00;BTC;EARN;0,01;;;0,001;BTC;;;;;nnnnnn;Label1\n";
        var actual = ParserTestUtils.getTransactionCluster(HEADER_CORRECT + row);
        var expected = new TransactionCluster(
            ImportedTransactionBean.createDepositWithdrawal(
                "24",
                Instant.parse("2022-01-12T14:24:00Z"),
                BTC,
                BTC,
                EARNING,
                new BigDecimal("0.01"),
                null,
                "nnnnnn",
                "Label1"
            ),
            List.of(
                new FeeRebateImportedTransactionBean(
                    "24-fee",
                    Instant.parse("2022-01-12T14:24:00Z"),
                    BTC,
                    BTC,
                    FEE,
                    new BigDecimal("0.001"),
                    BTC,
                    null,
                    null,
                    "Label1"
                )
            )
        );
        ParserTestUtils.checkEqual(expected, actual);
    }


    @Test
    void testCorrectParsingRawTransactionForkWithFee() {
        var row = "24;12.1.2022 14:24:00;BTC;FORK;0,01;;;0,001;BTC;;;;;nnnnnn;Label1\n";
        var actual = ParserTestUtils.getTransactionCluster(HEADER_CORRECT + row);
        var expected = new TransactionCluster(
            ImportedTransactionBean.createDepositWithdrawal(
                "24",
                Instant.parse("2022-01-12T14:24:00Z"),
                BTC,
                BTC,
                FORK,
                new BigDecimal("0.01"),
                null,
                "nnnnnn",
                "Label1"
            ),
            List.of(
                new FeeRebateImportedTransactionBean(
                    "24-fee",
                    Instant.parse("2022-01-12T14:24:00Z"),
                    BTC,
                    BTC,
                    FEE,
                    new BigDecimal("0.001"),
                    BTC,
                    null,
                    null,
                    "Label1"
                )
            )
        );
        ParserTestUtils.checkEqual(expected, actual);
    }

}