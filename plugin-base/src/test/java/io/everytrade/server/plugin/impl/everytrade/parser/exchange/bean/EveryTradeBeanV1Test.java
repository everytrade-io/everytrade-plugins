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

class EveryTradeBeanV1Test {
    private static final String HEADER_CORRECT = "UID;DATE;SYMBOL;ACTION;QUANTY;PRICE;FEE\n";

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
        final String headerWrong = "UID;DATE;SYMBOL;aCTION;QUANTY;PRICE;FEE\n";
        try {
            ParserTestUtils.testParsing(headerWrong);
            fail("No expected exception has been thrown.");
        } catch (ParsingProcessException ignored) {
        }
    }

    @Test
    void testCorrectParsingRawTransaction() {
        final String row = "1;1.9.2019 14:43:18;BTC/CZK;BUY;0.066506;210507.322647581000;0.1\n";
        final TransactionCluster acual = ParserTestUtils.getTransactionCluster(HEADER_CORRECT + row);
        final TransactionCluster expected = new TransactionCluster(
            new BuySellImportedTransactionBean(
                "1",
                Instant.parse("2019-09-01T14:43:18Z"),
                Currency.BTC,
                Currency.CZK,
                TransactionType.BUY,
                new BigDecimal("0.066506"),
                new BigDecimal("210507.322647581")
            ),
            List.of(
                new FeeRebateImportedTransactionBean(
                    "1-fee",
                    Instant.parse("2019-09-01T14:43:18Z"),
                    Currency.BTC,
                    Currency.CZK,
                    TransactionType.FEE,
                    new BigDecimal("0.1"),
                    Currency.CZK
                )
            )
        );
        ParserTestUtils.checkEqual(expected, acual);
    }

    @Test
    void testCorrectParsingRawTransactionDiffDateFormat() {
        final String row = "1;2019-09-01 14:43:18;BTC/CZK;BUY;0.066506;210507.322647581000;0.1\n";
        final TransactionCluster acual = ParserTestUtils.getTransactionCluster(HEADER_CORRECT + row);
        final TransactionCluster expected = new TransactionCluster(
            new BuySellImportedTransactionBean(
                "1",
                Instant.parse("2019-09-01T14:43:18Z"),
                Currency.BTC,
                Currency.CZK,
                TransactionType.BUY,
                new BigDecimal("0.066506"),
                new BigDecimal("210507.322647581")
            ),
            List.of(
                new FeeRebateImportedTransactionBean(
                    "1-fee",
                    Instant.parse("2019-09-01T14:43:18Z"),
                    Currency.BTC,
                    Currency.CZK,
                    TransactionType.FEE,
                    new BigDecimal("0.1"),
                    Currency.CZK
                )
            )
        );
        ParserTestUtils.checkEqual(expected, acual);
    }

    @Test
    void testConversionFromNullToZero() {
        final String row = "1;1.9.2019 14:43:18;BTC/CZK;BUY;0.066506;210507.322647581000;\n";
        final TransactionCluster acual = ParserTestUtils.getTransactionCluster(HEADER_CORRECT + row);
        final TransactionCluster expected = new TransactionCluster(
            new BuySellImportedTransactionBean(
                "1",
                Instant.parse("2019-09-01T14:43:18Z"),
                Currency.BTC,
                Currency.CZK,
                TransactionType.BUY,
                new BigDecimal("0.066506"),
                new BigDecimal("210507.322647581")
            ),
            List.of()
        );
        ParserTestUtils.checkEqual(expected, acual);
    }

    @Test
    void testUnknownExchange() {
        final String row = "1;1.9.2019 14:43:18;XXX/CZK;BUY;0.066506;210507.322647581000;0\n";
        final ParsingProblem parsingProblem = ParserTestUtils.getParsingProblem(HEADER_CORRECT + row);
        final String error = parsingProblem.getMessage();
        assertTrue(error.contains("XXX/CZK"));
    }

    @Test
    void testIgnoredTransactionType() {
        // test wrong unsupported tx type
        final String row = "1;1.9.2019 14:43:18;BTC/CZK;WITHDRAWZ;0.066506;210507.322647581000;0\n";
        final ParsingProblem parsingProblem = ParserTestUtils.getParsingProblem(HEADER_CORRECT + row);
        assertTrue(parsingProblem.getMessage().contains(ExchangeBean.UNSUPPORTED_TRANSACTION_TYPE.concat("WITHDRAWZ"))
        );
    }
}