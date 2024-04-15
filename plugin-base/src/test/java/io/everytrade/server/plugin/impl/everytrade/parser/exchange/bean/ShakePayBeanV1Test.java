package io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean;

import io.everytrade.server.model.Currency;
import io.everytrade.server.model.TransactionType;
import io.everytrade.server.plugin.api.parser.ImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.ParsingProblem;
import io.everytrade.server.plugin.api.parser.TransactionCluster;
import io.everytrade.server.plugin.impl.everytrade.parser.exception.ParsingProcessException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class ShakePayBeanV1Test {
    private static final String HEADER_CORRECT = "Transaction Type,Date,Amount Debited,Debit Currency,Amount " +
        "Credited,Credit Currency,Exchange Rate,Credit/Debit,Spot Rate\n";

    @Test
    void testWrongHeader() {
        final String headerWrong = "Transaction Type,Date,AmountDebited,Debit Currency,Amount Credited,Credit " +
            "Currency," +
            "Exchange Rate,Credit/Debit,Spot Rate\n";
        try {
            ParserTestUtils.testParsing(headerWrong);
            fail("No expected exception has been thrown.");
        } catch (ParsingProcessException ignored) {
        }
    }

    @Test
    void testCorrectParsingRawTransactionBuy() {
        final String row = "exchange,2020-03-11T19:59:23+00,\"1,000\",CAD,0.09172307,BTC,10902.3826,,\n";
        final TransactionCluster actual = ParserTestUtils.getTransactionCluster(HEADER_CORRECT + row);
        final TransactionCluster expected = new TransactionCluster(
            new ImportedTransactionBean(
                null,
                Instant.parse("2020-03-11T19:59:23Z"),
                Currency.BTC,
                Currency.CAD,
                TransactionType.BUY,
                new BigDecimal("0.09172307"),
                new BigDecimal("10902.38257398056999182")
            ),
            Collections.emptyList()
        );
        ParserTestUtils.checkEqual(expected, actual);
    }

    @Test
    void testCorrectParsingRawTransactionSell() {
        final String row = "exchange,2020-03-11T19:59:23+00,0.09172307,BTC,\"1,000\",CAD,10902.3826,,\n";
        final TransactionCluster actual = ParserTestUtils.getTransactionCluster(HEADER_CORRECT + row);
        final TransactionCluster expected = new TransactionCluster(
            new ImportedTransactionBean(
                null,
                Instant.parse("2020-03-11T19:59:23Z"),
                Currency.BTC,
                Currency.CAD,
                TransactionType.SELL,
                new BigDecimal("0.09172307"),
                new BigDecimal("10902.38257398056999182")
            ),
            Collections.emptyList()
        );
        ParserTestUtils.checkEqual(expected, actual);
    }

    @Test
    void testUnknownBase() {
        final String row = "exchange,2020-03-11T19:59:23+00,\"1,000\",CAD,0.09172307,XXX,10902.3826,,\n";
        final ParsingProblem parsingProblem = ParserTestUtils.getParsingProblem(HEADER_CORRECT + row);
        final String error = parsingProblem.getMessage();
        assertTrue(error.contains("Unable to set value 'XXX'"));
    }
}