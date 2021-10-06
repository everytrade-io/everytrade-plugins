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

import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.ExchangeBean.FEE_UID_PART;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class CoinmateBeanV1Test {
    private static final String HEADER_CORRECT = "ID;Date;Type;Amount;Amount Currency;Price;Price Currency;Fee;" +
        "Fee Currency;Total;" +
        "Total Currency;Description;Status\n";

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
        final String headerWrong = "ID;Date;Type;Amount;Amount Xurrency;Price;Price Currency;Fee;Fee Currency;Total;" +
            "Total Currency;Description;Status\n";
        try {
            ParserTestUtils.testParsing(headerWrong);
            fail("No expected exception has been thrown.");
        } catch (ParsingProcessException ignored) {
        }
    }


    @Test
    void testCorrectParsingRawTransaction() {
        final String row = "4;2019-08-30 05:05:24;BUY;0.0019;BTC;8630.7;EUR;0.03443649;EUR;16.43276649;EUR;;OK\n";
        final TransactionCluster actual = ParserTestUtils.getTransactionCluster(HEADER_CORRECT + row);
        final TransactionCluster expected = new TransactionCluster(
            new BuySellImportedTransactionBean(
                "4",
                Instant.parse("2019-08-30T05:05:24Z"),
                Currency.BTC,
                Currency.EUR,
                TransactionType.BUY,
                new BigDecimal("0.0019"),
                new BigDecimal("8630.7")
            ),
            List.of(
                new FeeRebateImportedTransactionBean(
                    "4" + FEE_UID_PART,
                    Instant.parse("2019-08-30T05:05:24Z"),
                    Currency.BTC,
                    Currency.EUR,
                    TransactionType.FEE,
                    new BigDecimal("0.03443649"),
                    Currency.EUR
                )
            )
        );
        ParserTestUtils.checkEqual(expected, actual);
    }

    @Test
    void testCorrectParsingRawTransactionAnotherAction() {
        final String row = "4;2019-08-30 05:05:24;QUICK_BUY;0.0019;BTC;8630.7;EUR;0.03443649;EUR;16.43276649;" +
            "EUR;;OK\n";
        final TransactionCluster actual = ParserTestUtils.getTransactionCluster(HEADER_CORRECT + row);
        final TransactionCluster expected = new TransactionCluster(
            new BuySellImportedTransactionBean(
                "4",
                Instant.parse("2019-08-30T05:05:24Z"),
                Currency.BTC,
                Currency.EUR,
                TransactionType.BUY,
                new BigDecimal("0.0019"),
                new BigDecimal("8630.7")
            ),
            List.of(
                new FeeRebateImportedTransactionBean(
                    "4" + FEE_UID_PART,
                    Instant.parse("2019-08-30T05:05:24Z"),
                    Currency.BTC,
                    Currency.EUR,
                    TransactionType.FEE,
                    new BigDecimal("0.03443649"),
                    Currency.EUR
                )
            )
        );
        ParserTestUtils.checkEqual(expected, actual);
    }

    @Test
    void testDifferentCurrencies() {
        // Verify that currency unit at "Price Currency" and "Fee Currency" fields are the same, if not skip the row
        // and report an invalid one
        final String row = "4;2019-08-30 05:05:24;BUY;0.0019;BTC;8630.7;EUR;0.03443649;CZK;16.43276649;EUR;;OK\n";
        final TransactionCluster actual = ParserTestUtils.getTransactionCluster(HEADER_CORRECT + row);
        assertEquals(1, actual.getIgnoredFeeTransactionCount());
    }

    @Test
    void testIgnoredTransactionType() {
        final String row = "4;2019-08-30 05:05:24;DEPOSIT;0.0019;BTC;8630.7;EUR;0.03443649;EUR;16.43276649;EUR;" +
            ";OK\n";
        final ParsingProblem parsingProblem = ParserTestUtils.getParsingProblem(HEADER_CORRECT + row);
        final String error = parsingProblem.getMessage();
        assertTrue(error.contains(ExchangeBean.UNSUPPORTED_TRANSACTION_TYPE.concat("DEPOSIT")));

    }

    @Test
    void testIgnoredStatus() {
        // Verify that currency unit at "Price Currency" and "Fee Currency" fields are the same,
        // if not skip the row and report an invalid one
        final String row = "4;2019-08-30 05:05:24;BUY;0.0019;BTC;8630.7;EUR;0.03443649;EUR;16.43276649;EUR;;" +
            "n/a\n";
        final ParsingProblem parsingProblem = ParserTestUtils.getParsingProblem(HEADER_CORRECT + row);
        final String error = parsingProblem.getMessage();
        assertTrue(error.contains(ExchangeBean.UNSUPPORTED_STATUS_TYPE.concat("n/a")));
    }
}