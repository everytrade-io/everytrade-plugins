package io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean;

import io.everytrade.server.model.Currency;
import io.everytrade.server.model.TransactionType;
import io.everytrade.server.plugin.api.parser.ConversionStatistic;
import io.everytrade.server.plugin.api.parser.ImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.RowError;
import io.everytrade.server.plugin.impl.everytrade.parser.exception.ParsingProcessException;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.ExchangeBean;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class CoinmateBeanV1Test {
    private static final String HEADER_CORRECT = "ID;Date;Type;Amount;Amount Currency;Price;Price Currency;Fee;" +
        "Fee Currency;Total;" +
        "Total Currency;Description;Status\n";

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
        final String headerWrong = "ID;Date;Type;Amount;Amount Xurrency;Price;Price Currency;Fee;Fee Currency;Total;" +
            "Total Currency;Description;Status\n";
        try {
            ParserTestUtils.testParsing(headerWrong);
            fail("No expected exception has been thrown.");
        } catch (ParsingProcessException e) {
        }
    }


    @Test
    void testCorrectParsingRawTransaction()  {
        final String row = "4589168;2019-08-30 05:05:24;BUY;0.0019;BTC;8630.7;EUR;0.03443649;EUR;16.43276649;EUR;;OK\n";
        final ImportedTransactionBean txBeanParsed  = ParserTestUtils.getTransactionBean(HEADER_CORRECT + row);
        final ImportedTransactionBean txBeanCorrect = new ImportedTransactionBean(
            "4589168",
            Instant.parse("2019-08-30T05:05:24Z"),
            Currency.BTC,
            Currency.EUR,
            TransactionType.BUY,
            new BigDecimal("0.0019"),
            new BigDecimal("8630.7"),
            new BigDecimal("0.03443649")
        );
        ParserTestUtils.checkEqual(txBeanParsed, txBeanCorrect);
    }

    @Test
    void testCorrectParsingRawTransactionAnotherAction()  {
        final String row = "4589168;2019-08-30 05:05:24;QUICK_BUY;0.0019;BTC;8630.7;EUR;0.03443649;EUR;16.43276649;" +
            "EUR;" +
            ";" +
            "OK\n";
        final ImportedTransactionBean txBeanParsed  = ParserTestUtils.getTransactionBean(HEADER_CORRECT + row);
        final ImportedTransactionBean txBeanCorrect = new ImportedTransactionBean(
            "4589168",
            Instant.parse("2019-08-30T05:05:24Z"),
            Currency.BTC,
            Currency.EUR,
            TransactionType.BUY,
            new BigDecimal("0.0019"),
            new BigDecimal("8630.7"),
            new BigDecimal("0.03443649")
        );
        ParserTestUtils.checkEqual(txBeanParsed, txBeanCorrect);
    }

    @Test
    void testDifferentCurrencies() {
        // Verify that currency unit at "Price Currency" and "Fee Currency" fields are the same, if not skip the row
        // and report an invalid one
        final String row = "4589168;2019-08-30 05:05:24;BUY;0.0019;BTC;8630.7;EUR;0.03443649;CZK;16.43276649;EUR;;OK\n";
        RowError rowError = ParserTestUtils.getRowError(HEADER_CORRECT + row);
        assertNotNull(rowError);
        final String error = rowError.getMessage();
        assertTrue(error.contains("Price currecy(EUR) and fee currency(CZK) are different."));
    }

    @Test
    void testIgnoredTransactionType() {
        final String row = "4589168;2019-08-30 05:05:24;DEPOSIT;0.0019;BTC;8630.7;EUR;0.03443649;EUR;16.43276649;EUR;" +
            ";OK\n";
        final ConversionStatistic conversionStatistic
            = ParserTestUtils.getConversionStatistic(HEADER_CORRECT + row);
        assertNotNull(conversionStatistic);
        assertEquals(1, conversionStatistic.getIgnoredRowsCount());
        assertTrue(
            conversionStatistic
                .getErrorRows()
                .get(0)
                .getMessage()
                .contains(ExchangeBean.UNSUPPORTED_TRANSACTION_TYPE.concat("DEPOSIT"))
        );

    }

    @Test
    void testIgnoredStatus() {
        // Verify that currency unit at "Price Currency" and "Fee Currency" fields are the same,
        // if not skip the row and report an invalid one
        final String row = "4589168;2019-08-30 05:05:24;BUY;0.0019;BTC;8630.7;EUR;0.03443649;EUR;16.43276649;EUR;;" +
            "n/a\n";
        final ConversionStatistic conversionStatistic
            = ParserTestUtils.getConversionStatistic(HEADER_CORRECT + row);
        assertNotNull(conversionStatistic);
        assertEquals(1, conversionStatistic.getIgnoredRowsCount());
        assertTrue(
            conversionStatistic
                .getErrorRows()
                .get(0)
                .getMessage()
                .contains(ExchangeBean.UNSUPPORTED_STATUS_TYPE.concat("n/a"))
        );
    }
}