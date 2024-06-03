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

import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.ExchangeBean.FEE_UID_PART;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.ExchangeBean.REBATE_UID_PART;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class HitBtcBeanV2Test {
    private static final String HEADER_CORRECT = "\"Email\",\"Date (+01:00)\",\"Instrument\",\"Trade ID\",\"Order " +
        "ID\",\"Side\",\"Quantity\",\"Price\",\"Volume\",\"Fee\",\"Rebate\",\"Total\",\"Taker\"\n";

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
            final String header = "\"Email\",\"Date (+01:00)\",\"Instrument\",\"Trade ID\",\"Order " +
                "ID\",\"Side\",\"Quantity\",\"Price\",\"Fee\",\"Rebate\",\"Total\"\n";
            ParserTestUtils.testParsing(header);
            fail("Expected exception has not been thrown.");
        } catch (ParsingProcessException ignored) {

        }
    }

    @Test
    void testCorrectParsingRawTransactionFee() {
        final String row
            = "\"email@email.com\",\"2018-10-29 12:41:32\",\"ETH/USD\",\"0\",\"6\",\"sell\",\"0.2700\"," +
            "\"194.01\",\"52.38270000\",\"0.00523827\",\"0.00000\",\"52.38793827\",\"true\"";
        final TransactionCluster actual = ParserTestUtils.getTransactionCluster(HEADER_CORRECT + row);
        final TransactionCluster expected = new TransactionCluster(
            new ImportedTransactionBean(
                "0",
                Instant.parse("2018-10-29T12:41:32Z"),
                Currency.ETH,
                Currency.USD,
                TransactionType.SELL,
                new BigDecimal("0.2700"),
                new BigDecimal("194.01")
            ),
            List.of(
                new FeeRebateImportedTransactionBean(
                    "0" + FEE_UID_PART,
                    Instant.parse("2018-10-29T12:41:32Z"),
                    Currency.USD,
                    Currency.USD,
                    TransactionType.FEE,
                    new BigDecimal("0.00523827"),
                    Currency.USD
                )
            )
        );
        ParserTestUtils.checkEqual(expected, actual);
    }

    @Test
    void testCorrectParsingRawTransactionRebate() {
        final String row
            = "\"email@email.com\",\"2018-10-29 12:41:32\",\"ETH/USD\",\"0\",\"6\",\"sell\",\"0.2700\",\"194.01\"," +
            "\"52.38270000\",\"0.00000000\",\"0.00523827\",\"52.38793827\",\"true\"";
        final TransactionCluster actual = ParserTestUtils.getTransactionCluster(HEADER_CORRECT + row);
        final TransactionCluster expected = new TransactionCluster(
            new ImportedTransactionBean(
                "0",
                Instant.parse("2018-10-29T12:41:32Z"),
                Currency.ETH,
                Currency.USD,
                TransactionType.SELL,
                new BigDecimal("0.2700"),
                new BigDecimal("194.01")
            ),
            List.of(
                new FeeRebateImportedTransactionBean(
                    "0" + REBATE_UID_PART,
                    Instant.parse("2018-10-29T12:41:32Z"),
                    Currency.USD,
                    Currency.USD,
                    TransactionType.REBATE,
                    new BigDecimal("0.00523827"),
                    Currency.USD
                )
            )
        );
        ParserTestUtils.checkEqual(expected, actual);
    }

    @Test
    void testIgnoredTransactionType() {
        final String row = "\"email@email.com\",\"2018-10-29 12:41:32\",\"ETH/USD\",\"0\",\"6\",\"sold\",\"0.2700\"" +
            ",\"194.01\",\"52.38270000\",\"0.00000000\",\"0.00523827\",\"52.38793827\",\"true\"";
        final ParsingProblem parsingProblem = ParserTestUtils.getParsingProblem(HEADER_CORRECT + row);
        final String error = parsingProblem.getMessage();
        assertTrue(error.contains(ExchangeBean.UNSUPPORTED_TRANSACTION_TYPE.concat("sold")));
    }
}