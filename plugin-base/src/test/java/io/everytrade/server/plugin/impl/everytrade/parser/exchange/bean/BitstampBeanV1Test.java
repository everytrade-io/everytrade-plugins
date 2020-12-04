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

import static io.everytrade.server.model.CurrencyPair.FiatCryptoCombinationException.INVALID_CURRENCY_PAIR;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class BitstampBeanV1Test {
    public static final String HEADER_CORRECT = "Type,Datetime,Account,Amount,Value,Rate,Fee,Sub Type\n";


    @Test
    void testWrongHeader() {
        final String headerWrong = "Type,Datetime,Account,AmountX,Value,Rate,Fee,Sub Type\n";
        try {
            ParserTestUtils.testParsing(headerWrong);
            fail("No expected exception has been thrown.");
        } catch (ParsingProcessException e) {
        }
    }

    @Test
    void testCorrectParsingRawTransaction() {
        final String row = "Market,\"Feb. 14, 2019, 03:32 PM\",Main Account,0.00111167 BTC,3.96238096 USD,3564.35 " +
            "USD,0.00990595 USD,Buy\n";
        final ImportedTransactionBean txBeanParsed = ParserTestUtils.getTransactionBean(HEADER_CORRECT + row);
        final ImportedTransactionBean txBeanCorrect = new ImportedTransactionBean(
            null,
            Instant.parse("2019-02-14T15:32:00Z"),
            Currency.BTC,
            Currency.USD,
            TransactionType.BUY,
            new BigDecimal("0.00111167"),
            new BigDecimal("3564.3499959520"),
            new BigDecimal("0.00990595")
        );
        ParserTestUtils.checkEqual(txBeanParsed, txBeanCorrect);
    }

    @Test
    void testCurrencyUnitAreTheSameFeeWrong() {
        // Verify that currency unit at Value/Rate/Fee fields are the same (must be a quote),
        // if not skip the row and report an invalid one
        final String row = "Market,\"Feb. 14, 2019, 03:32 PM\",Main Account,0.00111167 BTC,3.96238096 USD,3564.35 " +
            "USD,0.00990595 CZK,Buy\n";
        final RowError rowError = ParserTestUtils.getRowError(HEADER_CORRECT + row);
        assertNotNull(rowError);
        final String error = rowError.getMessage();
        assertTrue(error.contains(BitstampBeanV1.CURRENCY_EQUALITY_MESSAGE));
    }

    @Test
    void testWrongDateNameOfMonth() {
        final String row = "Market,\"XXX. 14, 2019, 03:32 PM\",Main Account,0.00111167 BTC,3.96238096 USD,3564.35 USD," +
            "0.00990595 USD,Buy\n";
        final RowError rowError = ParserTestUtils.getRowError(HEADER_CORRECT + row);
        assertNotNull(rowError);
        final String error = rowError.getMessage();
        assertTrue(error.contains("Cannot parse 'XXX. 14, 2019, 03:32 PM'"));
    }

    @Test
    void testUnknonwBase() {
        final String row = "Market,\"Feb. 14, 2019, 03:32 PM\",Main Account,0.00111167 XXX,3.96238096 USD,3564.35 USD," +
            "0.00990595 USD,Buy\n";
        final RowError rowError = ParserTestUtils.getRowError(HEADER_CORRECT + row);
        assertNotNull(rowError);
        final String error = rowError.getMessage();
        assertTrue(error.contains("Unable to set value '0.00111167 XXX'"));
    }

    @Test
    void testNotAllowedPair() {
        final String row = "Market,\"Feb. 14, 2019, 03:32 PM\",Main Account,0.00111167 USD,3.96238096 BTC,3564.35 BTC" +
            ",0.00990595 BTC,Buy\n";
        final RowError rowError = ParserTestUtils.getRowError(HEADER_CORRECT + row);
        assertNotNull(rowError);
        final String error = rowError.getMessage();
        assertTrue(error.contains(INVALID_CURRENCY_PAIR));
    }

    @Test
    void testIgnoredTransactionType() {
        final String row = "Market,\"Feb. 14, 2019, 03:32 PM\",Main Account,0.00111167 BTC,3.96238096 USD,3564.35 USD," +
            "0" +
            ".00990595 USD,Cancel\n";
        final ConversionStatistic conversionStatistic
            = ParserTestUtils.getConversionStatistic(HEADER_CORRECT + row);
        assertNotNull(conversionStatistic);
        assertEquals(1, conversionStatistic.getIgnoredRowsCount());
        assertTrue(
            conversionStatistic
                .getErrorRows()
                .get(0)
                .getMessage()
                .contains(ExchangeBean.UNSUPPORTED_TRANSACTION_TYPE.concat("Cancel"))
        );
    }
}