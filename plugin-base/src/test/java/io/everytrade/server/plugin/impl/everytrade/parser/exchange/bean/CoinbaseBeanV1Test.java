package io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean;

import io.everytrade.server.model.Currency;
import io.everytrade.server.model.TransactionType;
import io.everytrade.server.plugin.api.parser.FeeRebateImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.ImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.ParseResult;
import io.everytrade.server.plugin.api.parser.ParsingProblem;
import io.everytrade.server.plugin.api.parser.TransactionCluster;
import io.everytrade.server.plugin.impl.everytrade.parser.EverytradeCsvMultiParser;
import io.everytrade.server.plugin.impl.everytrade.parser.exception.ParsingProcessException;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.ExchangeBean;
import io.everytrade.server.test.TestUtils;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static io.everytrade.server.model.Currency.BTC;
import static io.everytrade.server.model.Currency.CZK;
import static io.everytrade.server.model.Currency.EUR;
import static io.everytrade.server.model.Currency.GRT;
import static io.everytrade.server.model.Currency.USD;
import static io.everytrade.server.model.Currency.USDC;
import static io.everytrade.server.model.Currency.XLM;
import static io.everytrade.server.model.Currency.XTZ;
import static io.everytrade.server.model.TransactionType.BUY;
import static io.everytrade.server.model.TransactionType.DEPOSIT;
import static io.everytrade.server.model.TransactionType.EARNING;
import static io.everytrade.server.model.TransactionType.FEE;
import static io.everytrade.server.model.TransactionType.SELL;
import static io.everytrade.server.model.TransactionType.STAKING_REWARD;
import static io.everytrade.server.model.TransactionType.WITHDRAWAL;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.ExchangeBean.FEE_UID_PART;
import static java.util.Collections.emptyList;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class CoinbaseBeanV1Test {
    private static final String HEADER_CORRECT
        = "Timestamp,Transaction Type,Asset,Quantity Transacted,EUR Spot Price at Transaction,EUR Subtotal,EUR Total " +
        "(inclusive of fees),EUR Fees,Notes\n";
    private static final String HEADER_CORRECT_SEK
        = "Timestamp,Transaction Type,Asset,Quantity Transacted,SEK Spot Price at Transaction,SEK Subtotal,SEK Total " +
        "(inclusive of fees),SEK Fees,Notes\n";

    private static final String HEADER_CORRECT_SPOT
        = "Timestamp,Transaction Type,Asset,Quantity Transacted,Spot Price Currency,Spot Price at Transaction,Subtotal," +
        "Total (inclusive of fees),Fees,Notes\n";

    private static final String HEADER_CORRECT_SPOT_SPREAD
        = "Timestamp,Transaction Type,Asset,Quantity Transacted,Spot Price Currency,Spot Price at Transaction,Subtotal," +
        "Total (inclusive of fees and/or spread),Fees and/or Spread,Notes\n";

    @Test
    void testCorrectHeader() {
        try {
            ParserTestUtils.testParsing(HEADER_CORRECT);
        } catch (ParsingProcessException e) {
            fail("Unexpected exception has been thrown.");
        }
    }

    @Test
    void testCorrectHeaderUsd() {
        try {
            ParserTestUtils.testParsing(HEADER_CORRECT_SEK);
        } catch (ParsingProcessException e) {
            fail("Unexpected exception has been thrown.");
        }
    }

    @Test
    void testWrongHeader() {
        final String headerWrong
            = "Timestamp,Transaction Type,XXX,Quantity Transacted,EUR Spot Price at Transaction,EUR Subtotal,"
            + "EUR Total (inclusive of fees),EUR Fees,Notes\n";
        try {
            ParserTestUtils.testParsing(headerWrong);
            fail("No expected exception has been thrown.");
        } catch (ParsingProcessException ignored) {
        }
    }

    @Test
    void testCorrectParsingRawTransactionBuy() {
        var row = "2020-09-27T18:36:58Z,Buy,BTC,0.03182812,9287.38,295.60,300.00,4.40,Bought 0.03182812 BTC for € 300.00 EUR\n";
        final TransactionCluster actual = ParserTestUtils.getTransactionCluster(HEADER_CORRECT + row);
        final TransactionCluster expected = new TransactionCluster(
            new ImportedTransactionBean(
                null,
                Instant.parse("2020-09-27T18:36:58Z"),
                BTC,
                EUR,
                BUY,
                new BigDecimal("0.03182812000000000"),
                new BigDecimal("9287.38486596129460364")
            ),
            List.of(
                new FeeRebateImportedTransactionBean(
                    FEE_UID_PART,
                    Instant.parse("2020-09-27T18:36:58Z"),
                    EUR,
                    EUR,
                    FEE,
                    new BigDecimal("4.40"),
                    EUR
                )
            )
        );
        ParserTestUtils.checkEqual(expected, actual);
    }

    @Test
    void testCorrectParsingRawTransactionEarning() {
        var row = "2021-03-03T18:55:26Z,Coinbase Earn,XLM,4.674612,EUR,0.350000,1.64,1.64,0.00,Received 4.674612 XLM from Coinbase Earn\n";
        final TransactionCluster actual = ParserTestUtils.getTransactionCluster(HEADER_CORRECT_SPOT + row);
        final TransactionCluster expected = new TransactionCluster(
            new ImportedTransactionBean(
                null,
                Instant.parse("2021-03-03T18:55:26Z"),
                XLM,
                XLM,
                EARNING,
                new BigDecimal("4.67461200000000000"),
                new BigDecimal("0.35083125615559110"),
                "Coinbase Earn",
                null
            ),
            emptyList());
        ParserTestUtils.checkEqual(expected, actual);
    }

    @Test
    void testCorrectParsingRawTransactionEarningLearningReward() {
        var row = "2022-11-18T20:27:05Z,Learning Reward,GRT,16.68056713,EUR,0.060000,1.00,1.00,0.00,Received 16.68056713 GRT from " +
            "Coinbase as a learning reward\n";
        final TransactionCluster actual = ParserTestUtils.getTransactionCluster(HEADER_CORRECT_SPOT + row);
        final TransactionCluster expected = new TransactionCluster(
            new ImportedTransactionBean(
                null,
                Instant.parse("2022-11-18T20:27:05Z"),
                GRT,
                GRT,
                EARNING,
                new BigDecimal("16.68056713"),
                null,
                "Learning Reward",
                null
            ),
            emptyList());
        ParserTestUtils.checkEqual(expected, actual);
    }

    @Test
    void testCorrectParsingRawTransactionStakingReward() {
        var row = "2023-04-27 03:28:05 UTC,Staking Income,XTZ,0.000004,CZK,21.71,0.00,0.00,0,\n";
        final TransactionCluster actual = ParserTestUtils.getTransactionCluster(HEADER_CORRECT_SPOT + row);
        final TransactionCluster expected = new TransactionCluster(
            new ImportedTransactionBean(
                null,
                Instant.parse("2023-04-27T03:28:05Z"),
                XTZ,
                XTZ,
                STAKING_REWARD,
                new BigDecimal("0.0000040000"),
                null,
                "Staking Income",
                null
            ),
            emptyList());
        ParserTestUtils.checkEqual(expected, actual);
    }

    @Test
    void testSendAsWithdrawal() {
        var row0 = "2021-02-01T09:28:38Z,Send,XRP,40.002654,EUR,0.550000,\"\",\"\",\"\"," +
            "Sent 40.002654 XRP to rDsbeomae4FXwgQTJp9Rs64Qg9vDiTCdBv (11150057)";
        var row1 = "2021-02-01T09:28:38Z,Receive,XRP,40.002654,EUR,0.550000,\"\",\"\",\"\"," +
            "Received 40.002654 XRP";
        final TransactionCluster actual0 = ParserTestUtils.getTransactionCluster(HEADER_CORRECT_SPOT + row0);
        final TransactionCluster actual1 = ParserTestUtils.getTransactionCluster(HEADER_CORRECT_SPOT + row1);

        BigDecimal quantityTransacted = new BigDecimal("40.002654");
        Currency asset = Currency.fromCode("XRP");
        String note = "rDsbeomae4FXwgQTJp9Rs64Qg9vDiTCdBv";

        final TransactionCluster expected0 = new TransactionCluster(
            ImportedTransactionBean.createDepositWithdrawal(
                null,
                Instant.parse("2021-02-01T09:28:38Z"),
                asset,
                asset,
                WITHDRAWAL,
                quantityTransacted,
                note,
                "Send",
                null
            ),
            emptyList());

        final TransactionCluster expected1 = new TransactionCluster(
            ImportedTransactionBean.createDepositWithdrawal(
                null,
                Instant.parse("2021-02-01T09:28:38Z"),
                asset,
                asset,
                DEPOSIT,
                quantityTransacted,
                null,
                "Receive",
                null
            ),
            emptyList());

        ParserTestUtils.checkEqual(expected0, actual0);
        ParserTestUtils.checkEqual(expected1, actual1);
    }

    @Test
    void testCorrectParsingRawTransactionConvert() {
        var row = "2019-09-25T14:37:00Z,Convert,BTC,0.05413984,USD,194436.11," +
            "10415.01,10526.74,111.73,Converted 0.05413984 BTC to 451.212148 USDC\n";
        final TransactionCluster actual = ParserTestUtils.getTransactionCluster(HEADER_CORRECT_SPOT + row);
        BigDecimal quotePrice = new BigDecimal("0.05413984");
        BigDecimal basePrice = new BigDecimal("451.212148");
        BigDecimal unitPrice = quotePrice.divide(basePrice, 17, 3);
        final TransactionCluster expected = new TransactionCluster(
            new ImportedTransactionBean(
                null,
                Instant.parse("2019-09-25T14:37:00Z"),
                USDC,
                BTC,
                BUY,
                new BigDecimal("451.21214800000000000"),
                unitPrice,
                "Convert",
                null
            ),
            List.of(
                new FeeRebateImportedTransactionBean(
                    FEE_UID_PART,
                    Instant.parse("2019-09-25T14:37:00Z"),
                    USD,
                    USD,
                    FEE,
                    new BigDecimal("111.73"),
                    USD
                )));
        ParserTestUtils.checkEqual(expected, actual);
    }

    @Test
    void testCorrectParsingRawTransactionBuy2() {
        var header = "Timestamp,Transaction Type,Asset,Quantity Transacted,Spot Price Currency,Spot Price at Transaction,Subtotal,Total " +
            "(inclusive of fees),Fees,Notes\n";
        var row = "2017-12-22T08:28:33Z,Buy,BTC,0.01748102,CZK,283252.24,4951.54,5149.00,197.46,\"Bought 0.01748102 BTC for Kč5,149.00 " +
            "CZK\"\n";
        final TransactionCluster actual = ParserTestUtils.getTransactionCluster(header + row);
        final TransactionCluster expected = new TransactionCluster(
            new ImportedTransactionBean(
                null,
                Instant.parse("2017-12-22T08:28:33Z"),
                BTC,
                CZK,
                BUY,
                new BigDecimal("0.01748102000000000"),
                new BigDecimal("283252.35026331415443721")
            ),
            List.of(
                new FeeRebateImportedTransactionBean(
                    FEE_UID_PART,
                    Instant.parse("2017-12-22T08:28:33Z"),
                    CZK,
                    CZK,
                    FEE,
                    new BigDecimal("197.46"),
                    CZK
                )
            )
        );
        ParserTestUtils.checkEqual(expected, actual);
    }

    @Test
    void testCorrectParsingRawTransactionSell() {
        final String row = "2020-03-09T05:17:11Z,Sell,BTC,0.03517833,6831.48,240.32,236.74," +
            "3.58,Sold 0.03517833 BTC for €236.74 EUR";
        final TransactionCluster actual = ParserTestUtils.getTransactionCluster(HEADER_CORRECT + row);
        final TransactionCluster expected = new TransactionCluster(
            new ImportedTransactionBean(
                null,
                Instant.parse("2020-03-09T05:17:11Z"),
                BTC,
                EUR,
                SELL,
                new BigDecimal("0.03517833000000000"),
                new BigDecimal("6831.47835613572332740")
            ),
            List.of(
                new FeeRebateImportedTransactionBean(
                    FEE_UID_PART,
                    Instant.parse("2020-03-09T05:17:11Z"),
                    EUR,
                    EUR,
                    FEE,
                    new BigDecimal("3.58"),
                    EUR
                )
            )
        );
        ParserTestUtils.checkEqual(expected, actual);
    }

    @Test
    void testUnknownQuote() {
        final String row = "2020-03-09T05:17:11Z,Sell,BTC,0.03517833,6831.48,240.32,236.74," +
            "3.58,Sold 0.03517833 BTC for €236.74 XXX";
        final ParsingProblem parsingProblem = ParserTestUtils.getParsingProblem(HEADER_CORRECT + row);
        final String error = parsingProblem.getMessage();
        assertTrue(error.contains("XXX"));
    }

    @Test
    void testIgnoredTransactionType() {
        final String row = "2020-09-27T18:36:58Z,Sending,BTC,0.03182812,9287.38,295.60,300.00," +
            "4.40,Send 0,03182812 BTC for € 300,00 EUR\n";
        final ParsingProblem parsingProblem = ParserTestUtils.getParsingProblem(HEADER_CORRECT + row);
        final String error = parsingProblem.getMessage();
        assertTrue(error.contains(ExchangeBean.UNSUPPORTED_TRANSACTION_TYPE.concat("Sending")));
    }

    @Test
    void testCorrectParsingRawTransactionsWithQuotes() {
        final String row0 = "\"2020-05-15T14:05:30Z,Receive,BTC,0.001044,CZK,243359.07,\"\"\"\",\"\"\"\",\"\"\"\",Received" +
            " 0.001044 BTC from Coinbase Referral\"";
        final String row1 = "\"2020-05-15T20:04:01Z,Buy,BTC,0.01104395,CZK,241343.40,2665.38,2771.82,106.44,\"\"Bought 0.01104395 BTC " +
            "for Kč2,771.82 CZK\"\"\"";
        final String row2 = "\"2020-05-15T20:04:01Z,Send,BTC,0.01208795,CZK,222507.26,\"\"\"\",\"\"\"\",\"\"\"\",Sent 0.01208795 BTC to " +
            "1PCzuXqY6MkYqNvbrKareZPJQPX8XExzb7";

        final TransactionCluster actual0 = ParserTestUtils.getTransactionCluster(HEADER_CORRECT_SPOT_SPREAD + row0);
        final TransactionCluster actual1 = ParserTestUtils.getTransactionCluster(HEADER_CORRECT_SPOT_SPREAD + row1);
        final TransactionCluster actual2 = ParserTestUtils.getTransactionCluster(HEADER_CORRECT_SPOT_SPREAD + row2);

        final TransactionCluster expected0 = new TransactionCluster(
            ImportedTransactionBean.createDepositWithdrawal(
                null,
                Instant.parse("2020-05-15T14:05:30Z"),
                BTC,
                BTC,
                DEPOSIT,
                new BigDecimal("0.00104400000000000"),
                null,
                "Receive",
                null
            ),
            emptyList());

        final TransactionCluster expected1 = new TransactionCluster(
            new ImportedTransactionBean(
                null,
                Instant.parse("2020-05-15T20:04:01Z"),
                Currency.BTC,
                CZK,
                TransactionType.BUY,
                new BigDecimal("0.01104395000000000"),
                new BigDecimal("241342.99774989926611403"),
                null,
                null
            ),
            List.of(
                new FeeRebateImportedTransactionBean(
                    FEE_UID_PART,
                    Instant.parse("2020-05-15T20:04:01Z"),
                    CZK,
                    CZK,
                    TransactionType.FEE,
                    new BigDecimal("106.44"),
                    CZK
                )
            )
        );

        final TransactionCluster expected2 = new TransactionCluster(
            ImportedTransactionBean.createDepositWithdrawal(
                null,
                Instant.parse("2020-05-15T20:04:01Z"),
                BTC,
                BTC,
                WITHDRAWAL,
                new BigDecimal("0.01208795"),
                "1PCzuXqY6MkYqNvbrKareZPJQPX8XExzb7",
                "Send",
                null
            ),
            emptyList());

        ParserTestUtils.checkEqual(expected0, actual0);
        ParserTestUtils.checkEqual(expected1, actual1);
        ParserTestUtils.checkEqual(expected2, actual2);
    }

    @Test
    void testCoinbase() {
        var row = "2023-01-20T07:09:23Z,Send,BTC,0.01031941,CZK,19339.02,199.57,199.57,0," +
            "Sent 0.01031941 BTC to bc1ql83d5c4dwwj4k6z8km5v8chff7688xxxxxxxxx\n";
        var row1 = "2023-01-19T22:16:25Z,Buy,BTC,0.01031941,CZK,19569.92,4832.82,4905.81,72.99,Bought 0.01031941 BTC for 4905.81 CZK\n";
        final ParseResult actual = ParserTestUtils.getParseResult(HEADER_CORRECT_SPOT_SPREAD + row.concat(row1));

        final TransactionCluster expected = new TransactionCluster(
            new ImportedTransactionBean(
                null,
                Instant.parse("2023-01-20T07:09:23Z"),
                BTC,
                BTC,
                WITHDRAWAL,
                new BigDecimal("0.01031941000000000"),
                null,
                "Send",
                "bc1ql83d5c4dwwj4k6z8km5v8chff7688xxxxxxxxx",
                null
            ), List.of()
        );

        final TransactionCluster expected2 = new TransactionCluster(
            new ImportedTransactionBean(
                null,
                Instant.parse("2023-01-19T22:16:25Z"),
                BTC,
                CZK,
                BUY,
                new BigDecimal("0.01031941000000000"),
                new BigDecimal("468323.28592429218337095")
            ), List.of(
            new FeeRebateImportedTransactionBean(
                FEE_UID_PART,
                Instant.parse("2023-01-19T22:16:25Z"),
                CZK,
                CZK,
                FEE,
                new BigDecimal("72.99"),
                CZK
            )
        )
        );
        ParserTestUtils.checkEqual(expected, actual.getTransactionClusters().get(0));
        ParserTestUtils.checkEqual(expected2, actual.getTransactionClusters().get(1));
    }
}