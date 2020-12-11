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

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class EveryTradeBeanV2Test {
    private static final String HEADER_CORRECT = "UID;DATE;SYMBOL;ACTION;QUANTY;VOLUME;FEE\n";

    @Test
    void testCorrectHeader() {
        final String headerWrong = "UID;DATE;SYMBOL;ACTION;QUANTY;VOLUME;FEE\n";
        try {
            ParserTestUtils.testParsing(headerWrong);
        } catch (ParsingProcessException e) {
            fail("Unexpected exception has been thrown.");
        }
    }
    @Test
    void testWrongHeader() {
        final String headerWrong = "UID;DATE;sYMBOL;ACTION;QUANTY;PRICE;FEE\n";
        try {
            ParserTestUtils.testParsing(headerWrong);
            fail("No expected exception has been thrown.");
        } catch (ParsingProcessException ignored) {
        }
    }


    @Test
    void testCorrectParsingRawTransaction() {
        final String row = "1;1.9.2019 14:43:18;BTC/CZK;BUY;0.066506;14000;0.2\n";
        final TransactionCluster acual = ParserTestUtils.getTransactionCluster(HEADER_CORRECT + row);
        final TransactionCluster expected = new TransactionCluster(
            new BuySellImportedTransactionBean(
                "1",
                Instant.parse("2019-09-01T14:43:18Z"),
                Currency.BTC,
                Currency.CZK,
                TransactionType.BUY,
                new BigDecimal("0.066506"),
                new BigDecimal("210507.3226475807")
            ),
            List.of(
                new FeeRebateImportedTransactionBean(
                    "1-fee",
                    Instant.parse("2019-09-01T14:43:18Z"),
                    Currency.BTC,
                    Currency.CZK,
                    TransactionType.FEE,
                    new BigDecimal("0.2"),
                    Currency.CZK
                )
            ),
            0
        );
        ParserTestUtils.checkEqual(expected, acual);
    }

    @Test
    void testCorrectParsingRawTransactionDiffDate() {
        final String row = "1;2019-9-1 14:43:18;BTC/CZK;BUY;0.066506;14000;0.2\n";
        final TransactionCluster acual = ParserTestUtils.getTransactionCluster(HEADER_CORRECT + row);
        final TransactionCluster expected = new TransactionCluster(
            new BuySellImportedTransactionBean(
                "1",
                Instant.parse("2019-09-01T14:43:18Z"),
                Currency.BTC,
                Currency.CZK,
                TransactionType.BUY,
                new BigDecimal("0.066506"),
                new BigDecimal("210507.3226475807")
            ),
            List.of(
                new FeeRebateImportedTransactionBean(
                    "1-fee",
                    Instant.parse("2019-09-01T14:43:18Z"),
                    Currency.BTC,
                    Currency.CZK,
                    TransactionType.FEE,
                    new BigDecimal("0.2"),
                    Currency.CZK
                )
            ),
            0
        );
        ParserTestUtils.checkEqual(expected, acual);
    }

    @Test
    void testConversionFromNullToZero() {
        final String row = "1;1.9.2019 14:43:18;BTC/CZK;BUY;0.066506;14000;\n";
        final TransactionCluster acual = ParserTestUtils.getTransactionCluster(HEADER_CORRECT + row);
        final TransactionCluster expected = new TransactionCluster(
            new BuySellImportedTransactionBean(
                "1",
                Instant.parse("2019-09-01T14:43:18Z"),
                Currency.BTC,
                Currency.CZK,
                TransactionType.BUY,
                new BigDecimal("0.066506"),
                new BigDecimal("210507.3226475807")
            ),
            List.of(
                new FeeRebateImportedTransactionBean(
                    "1-fee",
                    Instant.parse("2019-09-01T14:43:18Z"),
                    Currency.BTC,
                    Currency.CZK,
                    TransactionType.FEE,
                    new BigDecimal("0"),
                    Currency.CZK
                )
            ),
            0
        );
        ParserTestUtils.checkEqual(expected, acual);
    }

    @Test
    void testUnknonwExchange() {
        final String row = "1;1.9.2019 14:43:18;XXX/CZK;BUY;0.066506;210507.322647581000;0\n";
        final ParsingProblem parsingProblem = ParserTestUtils.getParsingProblem(HEADER_CORRECT + row);
        assertNotNull(parsingProblem);
        final String error = parsingProblem.getMessage();
        assertTrue(error.contains("XXX/CZK"));
    }

    @Test
    void testIgnoredTransactionType() {
        final String row = "1;1.9.2019 14:43:18;BTC/CZK;BOUGHT;0.066506;14000;0\n";
        final ParsingProblem parsingProblem = ParserTestUtils.getParsingProblem(HEADER_CORRECT + row);
        assertNotNull(parsingProblem);
        assertTrue(parsingProblem.getMessage().contains(ExchangeBean.UNSUPPORTED_TRANSACTION_TYPE.concat("BOUGHT"))
        );
    }
}