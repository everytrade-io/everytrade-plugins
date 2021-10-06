package io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean;

import io.everytrade.server.model.Currency;
import io.everytrade.server.model.TransactionType;
import io.everytrade.server.plugin.api.parser.BuySellImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.ParsingProblem;
import io.everytrade.server.plugin.api.parser.TransactionCluster;
import io.everytrade.server.plugin.impl.everytrade.parser.exception.ParsingProcessException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;

import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.ExchangeBean.UNSUPPORTED_CURRENCY_PAIR;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class CoinsquareBeanV2Test {
    private static final String HEADER_CORRECT = "date;from_currency;from_amount;to_currency;to_amount\n";

    @Test
    void testWrongHeader() {
        final String headerWrong = "date;currency;from_amount;to_currency;to_amount\n";
        try {
            ParserTestUtils.testParsing(headerWrong);
            fail("No expected exception has been thrown.");
        } catch (ParsingProcessException ignored) {
        }
    }

    @Test
    void testCorrectParsingRawTransactionBuy() {
        final String row = "29-05-19;CAD;2,000.00;BTC;0.16625219\n";
        final TransactionCluster actual = ParserTestUtils.getTransactionCluster(HEADER_CORRECT + row);
        final TransactionCluster expected = new TransactionCluster(
            new BuySellImportedTransactionBean(
                null,
                Instant.parse("2019-05-29T00:00:00Z"),
                Currency.BTC,
                Currency.CAD,
                TransactionType.BUY,
                new BigDecimal("0.16625219"),
                new BigDecimal("12029.9167186910")
            ),
            Collections.emptyList()
        );
        ParserTestUtils.checkEqual(expected, actual);
    }

    @Test
    void testCorrectParsingRawTransactionSell() {
        final String row = "10-11-17;BTC;0.11040202;CAD;958.76\n";
        final TransactionCluster actual = ParserTestUtils.getTransactionCluster(HEADER_CORRECT + row);
        final TransactionCluster expected = new TransactionCluster(
            new BuySellImportedTransactionBean(
                null,
                Instant.parse("2017-11-10T00:00:00Z"),
                Currency.BTC,
                Currency.CAD,
                TransactionType.SELL,
                new BigDecimal("0.11040202"),
                new BigDecimal("8684.2613930434")
            ),
            Collections.emptyList()
        );
        ParserTestUtils.checkEqual(expected, actual);
    }

    @Test
    void testUnsupportedTransactionPair() {
        final String row = "10-11-17;XMR;0.11040202;XRP;958.76\n";
        final ParsingProblem parsingProblem = ParserTestUtils.getParsingProblem(HEADER_CORRECT + row);
        final String error = parsingProblem.getMessage();
        assertTrue(error.contains(UNSUPPORTED_CURRENCY_PAIR.concat("XMR/XRP")));
    }
}