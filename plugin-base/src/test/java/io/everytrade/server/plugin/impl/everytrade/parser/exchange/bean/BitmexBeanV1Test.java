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

class BitmexBeanV1Test {
    private static final String HEADER_CORRECT
        = "\uFEFF\"transactTime\",\"symbol\",\"execType\",\"side\",\"lastQty\",\"lastPx\",\"execCost\",\"commission\"," +
        "\"execComm\",\"ordType\",\"orderQty\",\"leavesQty\",\"price\",\"text\",\"orderID\"\n";

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
        final String headerWrong
            = "\"transactTime\",\"symbol\",\"execType\",\"side\",\"lastQty\",\"lastPx\",\"execCost\",\"commission\"," +
            "\"execComm\",\"ordType\",\"orderQty\",\"leavesQty\",\"price\",\"text\",\"order\"\n";
        try {
            ParserTestUtils.testParsing(headerWrong);
            fail("No expected exception has been thrown.");
        } catch (ParsingProcessException e) {
        }
    }


    @Test
    void testCorrectParsingRawTransaction() {
        final String row = "\"1/24/2020, 5:48:15 PM\",\"XBTUSD\",\"Trade\",\"Sell\",\"170\",\"8470.5\",\"2007020\"," +
            "\"0.00075\",\"1505\",\"Limit\",\"170\",\"0\",\"8400\",\"Submission from www.bitmex.com\"," +
            "\"2da254ab-5638-7894-6b4b-ddc5e9e265cb\"\n";
        final ImportedTransactionBean txBeanParsed = ParserTestUtils.getTransactionBean(HEADER_CORRECT + row);
        final ImportedTransactionBean txBeanCorrect = new ImportedTransactionBean(
            "2da254ab-5638-7894-6b4b-ddc5e9e265cb",
            Instant.parse("2020-01-24T17:48:15Z"),
            Currency.BTC,
            Currency.USD,
            TransactionType.SELL,
            new BigDecimal("170"),
            new BigDecimal("8470.5"),
            new BigDecimal("0.00001505")
        );

        ParserTestUtils.checkEqual(txBeanParsed, txBeanCorrect);
    }


    @Test
    void testUnknonwExchangePair() {
        final String row = "\"1/24/2020, 5:48:15 PM\",\"XXBTUSD\",\"Trade\",\"Sell\",\"170\",\"8470.5\",\"2007020\"," +
            "\"0.00075\",\"1505\",\"Limit\",\"170\",\"0\",\"8400\",\"Submission from www.bitmex.com\"," +
            "\"2da254ab-5638-7894-6b4b-ddc5e9e265cb\"\n";
        final RowError rowError = ParserTestUtils.getRowError(HEADER_CORRECT + row);
        assertNotNull(rowError);
        final String error = rowError.getMessage();
        assertTrue(error.contains("Unable to set value 'XXBTUSD'"));
    }

    @Test
    void testIgnoredStatus() {
        final String row = "\"1/24/2020, 5:48:15 PM\",\"XBTUSD\",\"Fund\",\"Sell\",\"170\",\"8470.5\",\"2007020\",\"0" +
            ".00075\",\"1505\",\"Limit\",\"170\",\"0\",\"8400\",\"Submission from www.bitmex.com\"," +
            "\"2da254ab-5638-7894-6b4b-ddc5e9e265cb\"\n";
        final ConversionStatistic conversionStatistic
            = ParserTestUtils.getConversionStatistic(HEADER_CORRECT + row);
        assertNotNull(conversionStatistic);
        assertEquals(1, conversionStatistic.getIgnoredRowsCount());
        assertTrue(
            conversionStatistic
                .getErrorRows()
                .get(0)
                .getMessage()
                .contains(ExchangeBean.UNSUPPORTED_STATUS_TYPE.concat("Fund"))
        );
    }
}