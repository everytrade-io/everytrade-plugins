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

class EveryTradeBeanV2Test {
    private static final String HEADER_CORRECT = "UID;DATE;SYMBOL;ACTION;QUANTY;VOLUME;FEE\n";

    @Test
    void testCorrectHeader() {
        final String headerWrong = "UID;DATE;SYMBOL;ACTION;QUANTY;VOLUME;FEE\n";
        try {
            ParserTestUtils.testParsing(headerWrong);
        } catch (ParsingProcessException e) {
            fail("Unexpected exception has been thrown.");
        }
    }
    @Test
    void testWrongHeader() {
        final String headerWrong = "UID;DATE;sYMBOL;ACTION;QUANTY;PRICE;FEE\n";
        try {
            ParserTestUtils.testParsing(headerWrong);
            fail("No expected exception has been thrown.");
        } catch (ParsingProcessException e) {
        }
    }


    @Test
    void testCorrectParsingRawTransaction() {
        final String row = "1;1.9.2019 14:43:18;BTC/CZK;BUY;0.066506;14000;0\n";
        final ImportedTransactionBean txBeanParsed = ParserTestUtils.getTransactionBean(HEADER_CORRECT + row);
        final ImportedTransactionBean txBeanCorrect = new ImportedTransactionBean(
            "1",
            Instant.parse("2019-09-01T14:43:18Z"),
            Currency.BTC,
            Currency.CZK,
            TransactionType.BUY,
            new BigDecimal("0.066506"),
            new BigDecimal("210507.3226475807"),
            BigDecimal.ZERO
        );
        ParserTestUtils.checkEqual(txBeanParsed, txBeanCorrect);
    }

    @Test
    void testCorrectParsingRawTransactionDiffDate() {
        final String row = "1;2019-9-1 14:43:18;BTC/CZK;BUY;0.066506;14000;0\n";
        final ImportedTransactionBean txBeanParsed = ParserTestUtils.getTransactionBean(HEADER_CORRECT + row);
        final ImportedTransactionBean txBeanCorrect = new ImportedTransactionBean(
            "1",
            Instant.parse("2019-09-01T14:43:18Z"),
            Currency.BTC,
            Currency.CZK,
            TransactionType.BUY,
            new BigDecimal("0.066506"),
            new BigDecimal("210507.3226475807"),
            BigDecimal.ZERO
        );
        ParserTestUtils.checkEqual(txBeanParsed, txBeanCorrect);
    }

    @Test
    void testConversionFromNullToZero() {
        final String row = "1;1.9.2019 14:43:18;BTC/CZK;BUY;0.066506;14000;\n";
        final ImportedTransactionBean txBeanParsed = ParserTestUtils.getTransactionBean(HEADER_CORRECT + row);
        final ImportedTransactionBean txBeanCorrect = new ImportedTransactionBean(
            "1",
            Instant.parse("2019-09-01T14:43:18Z"),
            Currency.BTC,
            Currency.CZK,
            TransactionType.BUY,
            new BigDecimal("0.066506"),
            new BigDecimal("210507.3226475807"),
            BigDecimal.ZERO
        );
        ParserTestUtils.checkEqual(txBeanParsed, txBeanCorrect);
    }

    @Test
    void testUnknonwExchange() {
        final String row = "1;1.9.2019 14:43:18;XXX/CZK;BUY;0.066506;210507.322647581000;0\n";
        final RowError rowError = ParserTestUtils.getRowError(HEADER_CORRECT + row);
        assertNotNull(rowError);
        final String error = rowError.getMessage();
        assertTrue(error.contains("XXX/CZK"));
    }

    @Test
    void testIgnoredTransactionType() {
        final String row = "1;1.9.2019 14:43:18;BTC/CZK;BOUGHT;0.066506;14000;0\n";
        final ConversionStatistic conversionStatistic
            = ParserTestUtils.getConversionStatistic(HEADER_CORRECT + row);
        assertNotNull(conversionStatistic);
        assertEquals(1, conversionStatistic.getIgnoredRowsCount());
        assertTrue(
            conversionStatistic
                .getErrorRows()
                .get(0)
                .getMessage()
                .contains(ExchangeBean.UNSUPPORTED_TRANSACTION_TYPE.concat("BOUGHT"))
        );
    }
}