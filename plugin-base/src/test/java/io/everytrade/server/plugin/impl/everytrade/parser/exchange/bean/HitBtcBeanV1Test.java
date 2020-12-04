package io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean;

import io.everytrade.server.model.Currency;
import io.everytrade.server.model.TransactionType;
import io.everytrade.server.plugin.api.parser.ConversionStatistic;
import io.everytrade.server.plugin.api.parser.ImportedTransactionBean;
import io.everytrade.server.plugin.impl.everytrade.parser.exception.ParsingProcessException;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.ExchangeBean;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class HitBtcBeanV1Test {
    private static final String HEADER_CORRECT = "\"Date (+01)\",\"Instrument\",\"Trade ID\",\"Order ID\"," +
        "\"Side\"," +
        "\"Quantity\",\"Price\",\"Volume\",\"Fee\",\"Rebate\",\"Total\"\n";

    @Test
    void testCorrectHeader() {
        try {
            ParserTestUtils.testParsing(HEADER_CORRECT);
        } catch (ParsingProcessException e) {
            fail("Unexpected exception has been thrown." + e.getMessage());
        }
    }

    @Test
    void testWrongHeader() {
        try {
            final String header = "\"Date +01)\",\"Instrument\",\"Trade ID\",\"Order ID\",\"Side\",\"Quantity\",\"Pri" +
                "ce\",\"Volume\",\"Fee\",\"Rebate\",\"Total\"\n";
            ParserTestUtils.testParsing(header);
            fail("Expected exception has not been thrown.");
        } catch (ParsingProcessException e) {

        }
    }

    @Test
    void testCorrectParsingRawTransaction() {
        final String row
            = "\"2018-10-29 12:41:32\",\"ETH/USD\",\"388286158\",\"67856774287\",\"sell\",\"0.2700\",\"194.01\"," +
            "\"52.38270000\",\"0.00000000\",\"0.00523827\",\"52.38793827\"";
        final ImportedTransactionBean txBeanParsed = ParserTestUtils.getTransactionBean(HEADER_CORRECT + row);
        final ImportedTransactionBean txBeanCorrect = new ImportedTransactionBean(
            "388286158",
            Instant.parse("2018-10-29T12:41:32Z"),
            Currency.ETH,
            Currency.USD,
            TransactionType.SELL,
            new BigDecimal("0.27"),
            new BigDecimal("194.01"),
            new BigDecimal("-0.00523827")
        );
        ParserTestUtils.checkEqual(txBeanParsed, txBeanCorrect);
    }

    @Test
    void testIgnoredTransactionType() {
        final String row = "\"2018-10-29 12:41:32\",\"ETH/USD\",\"388286158\",\"67856774287\",\"sold\",\"0.2700\"" +
            ",\"194.01\",\"52.38270000\",\"0.00000000\",\"0.00523827\",\"52.38793827\"";
        final ConversionStatistic conversionStatistic
            = ParserTestUtils.getConversionStatistic(HEADER_CORRECT + row);
        assertNotNull(conversionStatistic);
        assertEquals(1, conversionStatistic.getIgnoredRowsCount());
        assertTrue(
            conversionStatistic
                .getErrorRows()
                .get(0)
                .getMessage()
                .contains(ExchangeBean.UNSUPPORTED_TRANSACTION_TYPE.concat("sold"))
        );
    }
}