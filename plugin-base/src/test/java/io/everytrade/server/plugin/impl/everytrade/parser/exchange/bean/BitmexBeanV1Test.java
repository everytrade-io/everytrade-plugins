package io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean;

import io.everytrade.server.model.Currency;
import io.everytrade.server.model.TransactionType;
import io.everytrade.server.plugin.api.parser.BuySellImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.FeeRebateImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.ParsingProblem;
import io.everytrade.server.plugin.api.parser.TransactionCluster;
import io.everytrade.server.plugin.impl.everytrade.parser.exception.ParsingProcessException;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.ExchangeBean;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class BitmexBeanV1Test {
    private static final String HEADER_CORRECT
        = "\uFEFF\"transactTime\",\"symbol\",\"execType\",\"side\",\"lastQty\",\"lastPx\",\"execCost\"," +
        "\"commission\",\"execComm\",\"ordType\",\"orderQty\",\"leavesQty\",\"price\",\"text\",\"orderID\"\n";

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
        } catch (ParsingProcessException ignored) {
        }
    }

    @Test
    void testCorrectParsingRawTransaction() {
        final String row = "\"1/24/2020, 5:48:15 PM\",\"XBTUSD\",\"Trade\",\"Sell\",\"170\",\"8470.5\",\"2007020\"," +
            "\"0.00075\",\"1505\",\"Limit\",\"170\",\"0\",\"8400\",\"Submission from www.bitmex.com\",\"2da-01\"\n";
        final TransactionCluster actual = ParserTestUtils.getTransactionCluster(HEADER_CORRECT + row);
        final TransactionCluster expected = new TransactionCluster(
            new BuySellImportedTransactionBean(
                "2da-01",
                Instant.parse("2020-01-24T17:48:15Z"),
                Currency.BTC,
                Currency.USD,
                TransactionType.SELL,
                new BigDecimal("170"),
                new BigDecimal("8470.5")
            ),
            List.of(
                new FeeRebateImportedTransactionBean(
                    "2da-01-fee",
                    Instant.parse("2020-01-24T17:48:15Z"),
                    Currency.BTC,
                    Currency.USD,
                    TransactionType.FEE,
                    new BigDecimal("0.00001505"),
                    Currency.USD
                )
            ),
            0
        );
        ParserTestUtils.checkEqual(expected, actual);
    }

    @Test
    void testUnknonwExchangePair() {
        final String row = "\"1/24/2020, 5:48:15 PM\",\"XXBTUSD\",\"Trade\",\"Sell\",\"170\",\"8470.5\",\"2007020\"," +
            "\"0.00075\",\"1505\",\"Limit\",\"170\",\"0\",\"8400\",\"Submission from www.bitmex.com\",\"2da-01\"\n";
        final ParsingProblem parsingProblem = ParserTestUtils.getParsingProblem(HEADER_CORRECT + row);
        final String error = parsingProblem.getMessage();
        assertTrue(error.contains("Unable to set value 'XXBTUSD'"));
    }

    @Test
    void testIgnoredStatus() {
        final String row = "\"1/24/2020, 5:48:15 PM\",\"XBTUSD\",\"Fund\",\"Sell\",\"170\",\"8470.5\",\"2007020\",\"0" +
            ".00075\",\"1505\",\"Limit\",\"170\",\"0\",\"8400\",\"Submission from www.bitmex.com\",\"2da-01\"\n";
        final ParsingProblem parsingProblem = ParserTestUtils.getParsingProblem(HEADER_CORRECT + row);
        assertTrue(parsingProblem.getMessage().contains(ExchangeBean.UNSUPPORTED_STATUS_TYPE.concat("Fund")));
    }
}