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
import java.util.List;

import static io.everytrade.server.model.Currency.AXL;
import static io.everytrade.server.model.Currency.BTC;
import static io.everytrade.server.model.Currency.CZK;
import static io.everytrade.server.model.Currency.EUR;
import static io.everytrade.server.model.Currency.SOL;
import static io.everytrade.server.model.TransactionType.AIRDROP;
import static io.everytrade.server.model.TransactionType.BUY;
import static io.everytrade.server.model.TransactionType.EARNING;
import static io.everytrade.server.model.TransactionType.FEE;
import static io.everytrade.server.model.TransactionType.FORK;
import static io.everytrade.server.model.TransactionType.INCOMING_PAYMENT;
import static io.everytrade.server.model.TransactionType.OUTGOING_PAYMENT;
import static io.everytrade.server.model.TransactionType.REBATE;
import static io.everytrade.server.model.TransactionType.REWARD;
import static io.everytrade.server.model.TransactionType.SELL;
import static io.everytrade.server.model.TransactionType.STAKE;
import static io.everytrade.server.model.TransactionType.STAKING_REWARD;
import static io.everytrade.server.model.TransactionType.UNSTAKE;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class EveryTradeBeanV3_2Test {
    private static final String HEADER_CORRECT =
        "UID;DATE;SYMBOL;ACTION;QUANTITY;UNIT_PRICE;VOLUME_QUOTE;FEE;FEE_CURRENCY;REBATE;REBATE_CURRENCY;ADDRESS_FROM;ADDRESS_TO;NOTE;" +
            "LABELS\n";
    private static final String HEADER_COMMA_SEPARATED = "UID,DATE,SYMBOL,ACTION,QUANTITY,UNIT_PRICE,VOLUME_QUOTE,FEE,FEE_CURRENCY," +
        "REBATE,REBATE_CURRENCY,ADDRESS_FROM,ADDRESS_TO,NOTE,LABELS\n";
    private static final String HEADER_V3_3 = "UID;DATE;SYMBOL;ACTION;QUANTITY;UNIT_PRICE;VOLUME_QUOTE;FEE;FEE_CURRENCY;" +
        "ADDRESS_FROM;ADDRESS_TO;NOTE;LABELS;PARTNER;REFERENCE\n";
    private static final String HEADER_EXCEL_FORMAT = "DATE;TYPE;SYMBOL;QUANTITY;QUANTITY_CURRENCY;UNIT_PRICE;UNIT_PRICE_CURRENCY;TOTAL;" +
        "TOTAL_CURRENCY;FEE;FEE_CURRENCY;SOURCE;ADDRESS;STATUS;NOTE;LABELS;REFERENCE;PARTNER;CREATED;UPDATED\n";

    @Test
    void testCorrectHeader() {
        try {
            ParserTestUtils.testParsing(HEADER_CORRECT);
        } catch (ParsingProcessException e) {
            fail("Unexpected exception has been thrown.");
        }
    }

    @Test
    void testNoUnitPrice() {
        final String row = "1;01.11.2024 00:00:00;BTC/EUR;BUY;5;;;;;;;;;;\n";
        final var actual = ParserTestUtils.getTransactionClusters(HEADER_CORRECT + row);
        final TransactionCluster expected = new TransactionCluster(
            new ImportedTransactionBean(
                "1",
                Instant.parse("2024-11-01T00:00:00Z"),
                BTC,
                EUR,
                BUY,
                new BigDecimal("5"),
                null,
                null,
                null,
                null
            ),
            List.of()
        );
        ParserTestUtils.checkEqual(expected, actual.get(0));
    }

    @Test
    void testPayments() {
        final String row = """
            17;08.01.2026 11:16:00;BTC;INCOMING PAYMENT;0,001;;;;BTC;xxxxxxx;;nnnnnn;Label1;name;Ref-1A2b-3C4d
            22;08.01.2026 11:21:00;BTC/EUR;OUTGOING PAYMENT;0,001;80000;80;0,;BTC;;xxxxxxx;nnnnnn;Label1:Label2;name;Ref-1A2b-3C4d
            """;
        final var actual = ParserTestUtils.getTransactionClusters(HEADER_V3_3 + row);
        final TransactionCluster expected = new TransactionCluster(
            new ImportedTransactionBean(
                "17",
                Instant.parse("2026-01-08T11:16:00Z"),
                BTC,
                BTC,
                INCOMING_PAYMENT,
                new BigDecimal("0.001"),
                null,
                "nnnnnn",
                "xxxxxxx",
                "Label1"
            ),
            List.of()
        );
        final TransactionCluster expected2 = new TransactionCluster(
            new ImportedTransactionBean(
                "22",
                Instant.parse("2026-01-08T11:21:00Z"),
                BTC,
                EUR,
                OUTGOING_PAYMENT,
                new BigDecimal("0.001"),
                new BigDecimal("80000.00000000000000000"),
                "nnnnnn",
                "xxxxxxx",
                "Label1:Label2"
            ),
            List.of()
        );
        ParserTestUtils.checkEqual(expected, actual.get(0));
        ParserTestUtils.checkEqual(expected2, actual.get(1));
    }

    @Test
    void testNewFormatV3() {
        final String row = "38319660;11.06.2025 00:00:00;BTC;REBATE;31;;;;;;;;;partneer;refee\n";
        final var actual = ParserTestUtils.getTransactionClusters(HEADER_V3_3 + row);
        final TransactionCluster expected = new TransactionCluster(
            new ImportedTransactionBean(
                "38319660",
                Instant.parse("2025-06-11T00:00:00Z"),
                BTC,
                BTC,
                REBATE,
                new BigDecimal("31"),
                null,
                null,
                null,
                null
            ),
            List.of()
        );
        ParserTestUtils.checkEqual(expected, actual.get(0));
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
                new BigDecimal("500000.00000000000000000"),
                "nnnnnn",
                null,
                "Label1"
            ),
            List.of()
        );
        ParserTestUtils.checkEqual(expected, actual);
    }

    @Test
    void testExcelHeaderFormat() {
        final String row = "04.05.2023 00:00:00;BUY;BTC/CZK;1;BTC;12;CZK;12;CZK;;;;\"testAdr;0x1234\";;;Error:Internal;" +
            "26.02.2025 13:46:20;04.03.2025 10:53:50\n";
        final TransactionCluster actual = ParserTestUtils.getTransactionCluster(HEADER_EXCEL_FORMAT + row);
        final TransactionCluster expected = new TransactionCluster(
            new ImportedTransactionBean(
                null,
                Instant.parse("2023-05-04T00:00:00Z"),
                BTC,
                CZK,
                BUY,
                new BigDecimal("1"),
                new BigDecimal("12.00000000000000000"),
                null,
                "testAdr;0x1234",
                "Error:Internal"
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
                new BigDecimal("500000.00000000000000000"),
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
                new BigDecimal("20000.00000000000000000"),
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
        final String row = "6;12.1.2022 14:06:00;BTC/EUR;SELL;0,06;20000;1200;;;;EUR;;;nnnnnn;Label1\n";
        final TransactionCluster actual = ParserTestUtils.getTransactionCluster(HEADER_CORRECT + row);
        final TransactionCluster expected = new TransactionCluster(
            new ImportedTransactionBean(
                "6",
                Instant.parse("2022-01-12T14:06:00Z"),
                BTC,
                EUR,
                SELL,
                new BigDecimal("0.06"),
                new BigDecimal("20000.00000000000000000"),
                "nnnnnn",
                null,
                "Label1"
            ),
            List.of()
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
        final String row = "5;05.12.2023 00:00:00;SOL;FEE;\"6,60E-06\";;;;;;" +
            ";7G7A9R894GcG1HyWtUmH4BCvknvsukL39N9E8NSHFhjH;3wjAoceD4w2P7Rf7ERDLyfTq61pFE7BAQM4Jdgjrhn24;;\n";
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
        assertTrue(parsingProblem.getMessage().contains("Unable to set value 'WITHDRAWZ' to method 'setAction'")
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

    @Test
    void testNewDateFormat() {
        var row = "1,31.12.2023,Axl,STAKE REWARD,12167.60559,,,0.0014,Axl,,,axelar1jv65s3grqf6v6jl3dp4t6c9t9rk99cd8r3j5z7," +
            "axelar1cwhkdnf59gp58637xfvwp57xlr9g26rhgp8p7d,,\n";
        var actual = ParserTestUtils.getTransactionCluster(HEADER_COMMA_SEPARATED + row);
        var expected = new TransactionCluster(
            ImportedTransactionBean.createDepositWithdrawal(
                "1",
                Instant.parse("2023-12-31T00:00:00Z"),
                AXL,
                AXL,
                STAKING_REWARD,
                new BigDecimal("12167.60559"),
                "axelar1jv65s3grqf6v6jl3dp4t6c9t9rk99cd8r3j5z7",
                null,
                null
            ),
            List.of(
                new FeeRebateImportedTransactionBean(
                    "1-fee",
                    Instant.parse("2023-12-31T00:00:00Z"),
                    AXL,
                    AXL,
                    FEE,
                    new BigDecimal("0.0014"),
                    AXL,
                    null,
                    "axelar1jv65s3grqf6v6jl3dp4t6c9t9rk99cd8r3j5z7",
                    null
                )
            )
        );
        ParserTestUtils.checkEqual(expected, actual);
    }

}