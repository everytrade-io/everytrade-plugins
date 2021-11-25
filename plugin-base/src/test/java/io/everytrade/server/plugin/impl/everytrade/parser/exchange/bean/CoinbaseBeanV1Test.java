package io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean;

import io.everytrade.server.model.Currency;
import io.everytrade.server.model.TransactionType;
import io.everytrade.server.plugin.api.parser.BuySellImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.FeeRebateImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.ParsingProblem;
import io.everytrade.server.plugin.api.parser.TransactionCluster;
import io.everytrade.server.plugin.impl.everytrade.parser.exception.ParsingProcessException;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.ExchangeBean;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class CoinbaseBeanV1Test {
    private static final String HEADER_CORRECT
        = "Timestamp,Transaction Type,Asset,Quantity Transacted,EUR Spot Price at Transaction,EUR Subtotal,EUR Total " +
        "(inclusive of fees),EUR Fees,Notes\n";
    private static final String HEADER_CORRECT_SEK
        = "Timestamp,Transaction Type,Asset,Quantity Transacted,SEK Spot Price at Transaction,SEK Subtotal,SEK Total " +
        "(inclusive of fees),SEK Fees,Notes\n";

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
            new BuySellImportedTransactionBean(
                null,
                Instant.parse("2020-09-27T18:36:58Z"),
                Currency.BTC,
                Currency.EUR,
                TransactionType.BUY,
                new BigDecimal("0.03182812"),
                new BigDecimal("9287.3848659613")
            ),
            List.of(
                new FeeRebateImportedTransactionBean(
                    null,
                    Instant.parse("2020-09-27T18:36:58Z"),
                    Currency.BTC,
                    Currency.EUR,
                    TransactionType.FEE,
                    new BigDecimal("4.40"),
                    Currency.EUR
                )
            )
        );
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
            new BuySellImportedTransactionBean(
                null,
                Instant.parse("2017-12-22T08:28:33Z"),
                Currency.BTC,
                Currency.CZK,
                TransactionType.BUY,
                new BigDecimal("0.01748102"),
                new BigDecimal("283252.3502633142")
            ),
            List.of(
                new FeeRebateImportedTransactionBean(
                    null,
                    Instant.parse("2017-12-22T08:28:33Z"),
                    Currency.BTC,
                    Currency.CZK,
                    TransactionType.FEE,
                    new BigDecimal("197.46"),
                    Currency.CZK
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
            new BuySellImportedTransactionBean(
                null,
                Instant.parse("2020-03-09T05:17:11Z"),
                Currency.BTC,
                Currency.EUR,
                TransactionType.SELL,
                new BigDecimal("0.03517833"),
                new BigDecimal("6831.4783561357")
            ),
            List.of(
                new FeeRebateImportedTransactionBean(
                    null,
                    Instant.parse("2020-03-09T05:17:11Z"),
                    Currency.BTC,
                    Currency.EUR,
                    TransactionType.FEE,
                    new BigDecimal("3.58"),
                    Currency.EUR
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
        final String row = "2020-09-27T18:36:58Z,Convert,BTC,0.03182812,9287.38,295.60,300.00," +
            "4.40,Bought 0,03182812 BTC for € 300,00 EUR\n";
        final ParsingProblem parsingProblem = ParserTestUtils.getParsingProblem(HEADER_CORRECT + row);
        final String error = parsingProblem.getMessage();
        assertTrue(error.contains(ExchangeBean.UNSUPPORTED_TRANSACTION_TYPE.concat("Convert")));
    }
}