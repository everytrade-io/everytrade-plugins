package io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean;

import io.everytrade.server.model.Currency;
import io.everytrade.server.model.TransactionType;
import io.everytrade.server.plugin.api.parser.BuySellImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.FeeRebateImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.RowError;
import io.everytrade.server.plugin.api.parser.TransactionCluster;
import io.everytrade.server.plugin.impl.everytrade.parser.exception.ParsingProcessException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

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
        } catch (ParsingProcessException ignored) {
        }
    }

    @Test
    void testCorrectParsingRawTransaction() {
        final String row = "2020/09/29 17:55:30;LTC/USD;Buy;46.43;LTC;0.9;0;46.43;USD;-41.79;BF-01;\n";
        final TransactionCluster actual = ParserTestUtils.getTransactionCluster(HEADER_CORRECT + row);
        final TransactionCluster expected = new TransactionCluster(
            new BuySellImportedTransactionBean(
                "BF-01",
                Instant.parse("2020-09-29T17:55:30Z"),
                Currency.LTC,
                Currency.USD,
                TransactionType.BUY,
                new BigDecimal("0.9"),
                new BigDecimal("46.43")
            ),
            List.of(
                new FeeRebateImportedTransactionBean(
                    "BF-01-fee",
                    Instant.parse("2020-09-29T17:55:30Z"),
                    Currency.LTC,
                    Currency.USD,
                    TransactionType.FEE,
                    new BigDecimal("0"),
                    Currency.USD
                )
            ),
            0
        );
        ParserTestUtils.checkEqual(expected, actual);
    }

    @Test
    void testCorrectParsingRawTransactionSell() {
        final String row = "2020/09/29 17:53:28;LTC/USD;Sell;44.16;LTC;-1;0;44.16;USD;44.16;BF-02;\n";
        final TransactionCluster actual = ParserTestUtils.getTransactionCluster(HEADER_CORRECT + row);
        final TransactionCluster expected = new TransactionCluster(
            new BuySellImportedTransactionBean(
                "BF-02",
                Instant.parse("2020-09-29T17:53:28Z"),
                Currency.LTC,
                Currency.USD,
                TransactionType.SELL,
                new BigDecimal("1"),
                new BigDecimal("44.16")
            ),
            List.of(
                new FeeRebateImportedTransactionBean(
                    "BF-02-fee",
                    Instant.parse("2020-09-29T17:53:28Z"),
                    Currency.LTC,
                    Currency.USD,
                    TransactionType.FEE,
                    new BigDecimal("0"),
                    Currency.USD
                )
            ),
            0
        );
        ParserTestUtils.checkEqual(expected, actual);
    }

    @Test
    void testUnknonwExchangePair() {
        final String row = "2020/09/29 17:55:30;LTC/USD;Buy;46.43;LTC;0.9;0;46.43;LTC;-41.79;BF-01;\n";
        final RowError rowError = ParserTestUtils.getRowError(HEADER_CORRECT + row);
        assertNotNull(rowError);
        final String error = rowError.getMessage();
        assertTrue(error.contains("Unsupported currency pair LTC/LTC"));
    }
}