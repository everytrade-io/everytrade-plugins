package io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean;

import io.everytrade.server.model.Currency;
import io.everytrade.server.model.TransactionType;
import io.everytrade.server.plugin.api.parser.ConversionStatistic;
import io.everytrade.server.plugin.api.parser.ImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.RowError;
import io.everytrade.server.plugin.impl.everytrade.parser.exception.ParsingProcessException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.ExchangeBean.UNSUPPORTED_CURRENCY_PAIR;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean.BitfinexBeanV1.ILLEGAL_ZERO_VALUE_OF_AMOUNT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class BitfinexBeanV1Test {
    public static final String HEADER_CORRECT = "#,PAIR,AMOUNT,PRICE,FEE,FEE CURRENCY,DATE,ORDER ID\n";


    @Test
    void testWrongHeader() {
        final  String headerWrong = "#,PAIR,AMOUNT,PRICE,X,FEE CURRENCY,DATE,ORDER ID\n";
        try {
            ParserTestUtils.testParsing(headerWrong);
            fail("No expected exception has been thrown.");
        } catch (ParsingProcessException e) {
        }
    }

    @Test
    void testCorrectParsingRawTransactionBuy() {
        final   String row = "0,BTC/USD,0.01048537,9212.82428,-0.00002097,BTC,04-02-20 16:52:06,1\n";
        final ImportedTransactionBean txBeanParsed = ParserTestUtils.getTransactionBean(HEADER_CORRECT + row);
        final ImportedTransactionBean txBeanCorrect = new ImportedTransactionBean(
            "0",
            Instant.parse("2020-02-04T16:52:06Z"),
            Currency.BTC,
            Currency.USD,
            TransactionType.BUY,
            new BigDecimal("0.01046440"),
            new BigDecimal("9231.2862009082"),
            new BigDecimal("0")
        );
        ParserTestUtils.checkEqual(txBeanParsed, txBeanCorrect);
    }

    @Test
    void testCorrectParsingRawTransactionBuyWithIgnoredChars() {
        final String row = "0,BTC/USD,0.01048537$,9 212.82428$,-0.00002097,BTC,04-02-20 16:52:06,1\n";
        final ImportedTransactionBean txBeanParsed = ParserTestUtils.getTransactionBean(HEADER_CORRECT + row);
        final ImportedTransactionBean txBeanCorrect = new ImportedTransactionBean(
            "0",
            Instant.parse("2020-02-04T16:52:06Z"),
            Currency.BTC,
            Currency.USD,
            TransactionType.BUY,
            new BigDecimal("0.01046440"),
            new BigDecimal("9231.2862009082"),
            new BigDecimal("0")
        );
        ParserTestUtils.checkEqual(txBeanParsed, txBeanCorrect);
    }

    @Test
    void testCorrectParsingRawTransactionBuyDiffFee() {
        final String row = "0,BTC/USD,0.01048537,9212.82428,-0.00002097,USD,04-02-20 16:52:06,1\n";
        final ImportedTransactionBean txBeanParsed = ParserTestUtils.getTransactionBean(HEADER_CORRECT + row);
        final ImportedTransactionBean txBeanCorrect = new ImportedTransactionBean(
            "0",
            Instant.parse("2020-02-04T16:52:06Z"),
            Currency.BTC,
            Currency.USD,
            TransactionType.BUY,
            new BigDecimal("0.0104853700"),
            new BigDecimal("9212.8262799294"),
            new BigDecimal("0")
        );
        ParserTestUtils.checkEqual(txBeanParsed, txBeanCorrect);
    }


    @Test
    void testCorrectParsingRawTransactionSell() {
        final String row = "414007820,BTC/USD,-0.0095,9214.7,-0.1750793,USD,04-02-20 16:49:55,38828965094\n";
        final ImportedTransactionBean txBeanParsed = ParserTestUtils.getTransactionBean(HEADER_CORRECT + row);
        final ImportedTransactionBean txBeanCorrect = new ImportedTransactionBean(
            "414007820",
            Instant.parse("2020-02-04T16:49:55Z"),
            Currency.BTC,
            Currency.USD,
            TransactionType.SELL,
            new BigDecimal("0.0095000000"),
            new BigDecimal("9196.2706000000"),
            new BigDecimal("0")
        );
        ParserTestUtils.checkEqual(txBeanParsed, txBeanCorrect);
    }

    @Test
    void testCorrectParsingRawTransactionSellDiffFee() {
        final String row = "414007820,BTC/USD,-0.0095,9214.7,-0.000000123,BTC,04-02-20 16:49:55,38828965094\n";
        final ImportedTransactionBean txBeanParsed = ParserTestUtils.getTransactionBean(HEADER_CORRECT + row);
        final ImportedTransactionBean txBeanCorrect = new ImportedTransactionBean(
            "414007820",
            Instant.parse("2020-02-04T16:49:55Z"),
            Currency.BTC,
            Currency.USD,
            TransactionType.SELL,
            new BigDecimal("0.009500123"),
            new BigDecimal("9214.5806954289"),
            new BigDecimal("0")
        );
        ParserTestUtils.checkEqual(txBeanParsed, txBeanCorrect);
    }


    @Test
    void testZerroAmount() {
        final String row = "0,BTC/USD,0.0,9212.82428,-0.00002097,BTC,04-02-20 16:52:06,1\n";
        final RowError rowError = ParserTestUtils.getRowError(HEADER_CORRECT + row);
        assertNotNull(rowError);
        final String error = rowError.getMessage();
        assertTrue(error.contains(ILLEGAL_ZERO_VALUE_OF_AMOUNT));
    }

    @Test
    void testFeeSkipped() {
        final String row = "0,BTC/USD,0.01048537,9212.82428,-0.00002097,XXX,04-02-20 16:52:06,1\n";
        final ImportedTransactionBean txBeanParsed = ParserTestUtils.getTransactionBean(HEADER_CORRECT + row);
        final ImportedTransactionBean txBeanCorrect = new ImportedTransactionBean(
            "0",
            Instant.parse("2020-02-04T16:52:06Z"),
            Currency.BTC,
            Currency.USD,
            TransactionType.BUY,
            new BigDecimal("0.0104853700"),
            new BigDecimal("9212.82428"),
            new BigDecimal("0")
        );
        ParserTestUtils.checkEqual(txBeanParsed, txBeanCorrect);
    }


    @Test
    void testNotAllowedPair() {
        final String row = "0,CAD/USD,0.01048537,9212.82428,-0.00002097,USD,04-02-20 16:52:06,1\n";
        final RowError rowError = ParserTestUtils.getRowError(HEADER_CORRECT + row);
        assertNotNull(rowError);
        final String error = rowError.getMessage();
        assertTrue(error.contains(UNSUPPORTED_CURRENCY_PAIR.concat("CAD/USD")));
    }

    @Test
    void testCorrectParsingMoreDateFormats() {
        final String row = "0,BTC/USD,0.01048537,9212.82428,-0.00002097,BTC,12-02-20 16:52:06,1\n";
        final String row2 = "0,BTC/USD,0.01048537,9212.82428,-0.00002097,BTC,12-20-20 16:52:06,1\n";
        final ImportedTransactionBean txBeanParsed
            = ParserTestUtils.getTransactionBean(HEADER_CORRECT + row + row2);
        final ImportedTransactionBean txBeanCorrect = new ImportedTransactionBean(
            "0",
            Instant.parse("2020-12-02T16:52:06Z"),
            Currency.BTC,
            Currency.USD,
            TransactionType.BUY,
            new BigDecimal("0.01046440"),
            new BigDecimal("9231.2862009082"),
            new BigDecimal("0")
        );
        ParserTestUtils.checkEqual(txBeanParsed, txBeanCorrect);
    }

    @Test
    void testCorrectParsingMoreDateFormatsMilliseconds() {
        final String row = "0,BTC/USD,0.01048537,9212.82428,-0.00002097,BTC,12-02-20 16:52:06.123," +
            "1\n";
        final String row2 = "0,BTC/USD,0.01048537,9212.82428,-0.00002097,BTC,12-20-20 16:52:06.123," +
            "1\n";
        final ImportedTransactionBean txBeanParsed
            = ParserTestUtils.getTransactionBean(HEADER_CORRECT + row + row2);
        final ImportedTransactionBean txBeanCorrect = new ImportedTransactionBean(
            "0",
            Instant.parse("2020-12-02T16:52:06.123Z"),
            Currency.BTC,
            Currency.USD,
            TransactionType.BUY,
            new BigDecimal("0.01046440"),
            new BigDecimal("9231.2862009082"),
            new BigDecimal("0")
        );
        ParserTestUtils.checkEqual(txBeanParsed, txBeanCorrect);
    }

    @Test
    void testIgnoredFee() {
        final String row = "0,BTC/USD,0.01048537,9212.82428,-0.00002097,XXX,04-02-20 16:52:06,1\n";
        final ConversionStatistic conversionStatistic =
            ParserTestUtils.getConversionStatistic(HEADER_CORRECT + row);
        assertNotNull(conversionStatistic);
        assertEquals(1, conversionStatistic.getIgnoredFeeTransactionCount());
    }

}