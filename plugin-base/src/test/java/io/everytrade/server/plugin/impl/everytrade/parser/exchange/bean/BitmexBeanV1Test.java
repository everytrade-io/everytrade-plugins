package io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean;

import io.everytrade.server.model.Currency;
import io.everytrade.server.model.TransactionType;
import io.everytrade.server.plugin.api.parser.FeeRebateImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.ImportedTransactionBean;
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

    private static final String HEADER_CORRECT_V2
        = "\uFEFF\"transactTime\",\"account\",\"symbol\",\"execType\",\"side\",\"lastQty\",\"lastPx\",\"execCost\"," +
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
            new ImportedTransactionBean(
                "2da-01",
                Instant.parse("2020-01-24T17:48:15Z"),
                Currency.BTC,
                Currency.USD,
                TransactionType.SELL,
                new BigDecimal("170.00000000000000000"),
                new BigDecimal("8470.50000000000000000")
            ),
            List.of(
                new FeeRebateImportedTransactionBean(
                    "2da-01-fee",
                    Instant.parse("2020-01-24T17:48:15Z"),
                    Currency.USD,
                    Currency.USD,
                    TransactionType.FEE,
                    new BigDecimal("0.00001505000000000"),
                    Currency.USD
                )
            )
        );
        ParserTestUtils.checkEqual(expected, actual);
    }

    @Test
    void testNewHeader() {
        final String row = """
                "2020-10-20T17:46:13.553Z","665454","XBTUSD","Trade","Buy","20472","11996","-170654592","0.00075","127990",\
                "StopLimit","20472","0","12178","Triggered: Order stop price reached
                Submission from www.bitmex.com","aa6efd43-4945-4ae1-979c-dc1d1ade2a1b"
                """;
        final TransactionCluster actual = ParserTestUtils.getTransactionCluster(HEADER_CORRECT_V2 + row);
        final TransactionCluster expected = new TransactionCluster(
            new ImportedTransactionBean(
                "aa6efd43-4945-4ae1-979c-dc1d1ade2a1b",
                Instant.parse("2020-10-20T17:46:13Z"),
                Currency.BTC,
                Currency.USD,
                TransactionType.BUY,
                new BigDecimal("20472.00000000000000000"),
                new BigDecimal("11996.00000000000000000")
            ),
            List.of(
                new FeeRebateImportedTransactionBean(
                    "aa6efd43-4945-4ae1-979c-dc1d1ade2a1b-fee",
                    Instant.parse("2020-10-20T17:46:13Z"),
                    Currency.USD,
                    Currency.USD,
                    TransactionType.FEE,
                    new BigDecimal("0.00127990000000000"),
                    Currency.USD
                )
            )
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