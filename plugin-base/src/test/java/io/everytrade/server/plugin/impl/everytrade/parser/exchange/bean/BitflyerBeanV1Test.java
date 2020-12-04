package io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean;

import io.everytrade.server.model.Currency;
import io.everytrade.server.model.TransactionType;
import io.everytrade.server.plugin.api.parser.ImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.RowError;
import io.everytrade.server.plugin.impl.everytrade.parser.exception.ParsingProcessException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class BitflyerBeanV1Test {
    private static final String HEADER_CORRECT
        = "Trade Date;Product;Trade Type;Traded Price;Currency 1;Amount (Currency 1);Fee;USD Rate (Currency);Currency" +
        " 2;Amount (Currency 2);Order ID;Details\n";

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
            = "Trade Date;Product;Trade Type;Traded Price;Currency 1;Amount (Currency);Fee;USD Rate (Currency);" +
            "Currency 2;Amount (Currency 2);Order ID;Details\n";
        try {
            ParserTestUtils.testParsing(headerWrong);
            fail("No expected exception has been thrown.");
        } catch (ParsingProcessException e) {
        }
    }


    @Test
    void testCorrectParsingRawTransaction() {
        final String row = "2020/09/29 17:55:30;LTC/USD;Buy;46.43;LTC;0.9;0;46.43;USD;-41.79;BFF20200929-175530-735572;\n";
        final ImportedTransactionBean txBeanParsed = ParserTestUtils.getTransactionBean(HEADER_CORRECT + row);
        final ImportedTransactionBean txBeanCorrect = new ImportedTransactionBean(
            "BFF20200929-175530-735572",
            Instant.parse("2020-09-29T17:55:30Z"),
            Currency.LTC,
            Currency.USD,
            TransactionType.BUY,
            new BigDecimal("0.9"),
            new BigDecimal("46.43"),
            new BigDecimal("0")
        );

        ParserTestUtils.checkEqual(txBeanParsed, txBeanCorrect);
    }

    @Test
    void testCorrectParsingRawTransactionSell() {
        final String row = "2020/09/29 17:53:28;LTC/USD;Sell;44.16;LTC;-1;0;44.16;USD;44.16;BFF20200929-175328-721339;\n";
        final ImportedTransactionBean txBeanParsed = ParserTestUtils.getTransactionBean(HEADER_CORRECT + row);
        final ImportedTransactionBean txBeanCorrect = new ImportedTransactionBean(
            "BFF20200929-175328-721339",
            Instant.parse("2020-09-29T17:53:28Z"),
            Currency.LTC,
            Currency.USD,
            TransactionType.SELL,
            new BigDecimal("1"),
            new BigDecimal("44.16"),
            new BigDecimal("0")
        );

        ParserTestUtils.checkEqual(txBeanParsed, txBeanCorrect);
    }


    @Test
    void testUnknonwExchangePair() {
        final String row = "2020/09/29 17:55:30;LTC/USD;Buy;46.43;LTC;0.9;0;46.43;LTC;-41.79;BFF20200929-175530-735572;\n";
        final RowError rowError = ParserTestUtils.getRowError(HEADER_CORRECT + row);
        assertNotNull(rowError);
        final String error = rowError.getMessage();
        assertTrue(error.contains("Unsupported currency pair LTC/LTC"));
    }
}