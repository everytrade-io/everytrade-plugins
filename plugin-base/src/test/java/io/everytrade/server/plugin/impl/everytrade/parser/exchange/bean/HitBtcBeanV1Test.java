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
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.ExchangeBean.REBATE_UID_PART;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class   HitBtcBeanV1Test {
    private static final String HEADER_CORRECT = "\"Date (+01)\",\"Instrument\",\"Trade ID\",\"Order ID\"," +
        "\"Side\"," +
        "\"Quantity\",\"Price\",\"Volume\",\"Fee\",\"Rebate\",\"Total\"\n";

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
            final String header = "\"Date +01)\",\"Instrument\",\"Trade ID\",\"Order ID\",\"Side\",\"Quantity\",\"Pri" +
                "ce\",\"Volume\",\"Fee\",\"Rebate\",\"Total\"\n";
            ParserTestUtils.testParsing(header);
            fail("Expected exception has not been thrown.");
        } catch (ParsingProcessException ignored) {

        }
    }

    @Test
    void testCorrectParsingRawTransactionFee() {
        final String row
            = "\"2018-10-29 12:41:32\",\"ETH/USD\",\"0\",\"6\",\"sell\",\"0.2700\",\"194.01\"," +
            "\"52.38270000\",\"0.00523827\",\"0.00000\",\"52.38793827\"";
        final TransactionCluster actual = ParserTestUtils.getTransactionCluster(HEADER_CORRECT + row);
        final TransactionCluster expected = new TransactionCluster(
            new BuySellImportedTransactionBean(
                "0",
                Instant.parse("2018-10-29T12:41:32Z"),
                Currency.ETH,
                Currency.USD,
                TransactionType.SELL,
                new BigDecimal("0.27"),
                new BigDecimal("194.01")
            ),
            List.of(
                new FeeRebateImportedTransactionBean(
                    "0" + FEE_UID_PART,
                    Instant.parse("2018-10-29T12:41:32Z"),
                    Currency.ETH,
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
            = "\"2018-10-29 12:41:32\",\"ETH/USD\",\"0\",\"6\",\"sell\",\"0.2700\",\"194.01\"," +
            "\"52.38270000\",\"0.00000000\",\"0.00523827\",\"52.38793827\"";
        final TransactionCluster actual = ParserTestUtils.getTransactionCluster(HEADER_CORRECT + row);
        final TransactionCluster expected = new TransactionCluster(
            new BuySellImportedTransactionBean(
                "0",
                Instant.parse("2018-10-29T12:41:32Z"),
                Currency.ETH,
                Currency.USD,
                TransactionType.SELL,
                new BigDecimal("0.27"),
                new BigDecimal("194.01")
            ),
            List.of(
                new FeeRebateImportedTransactionBean(
                    "0" + REBATE_UID_PART,
                    Instant.parse("2018-10-29T12:41:32Z"),
                    Currency.ETH,
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
        final String row = "\"2018-10-29 12:41:32\",\"ETH/USD\",\"0\",\"6\",\"sold\",\"0.2700\"" +
            ",\"194.01\",\"52.38270000\",\"0.00000000\",\"0.00523827\",\"52.38793827\"";
        final ParsingProblem parsingProblem = ParserTestUtils.getParsingProblem(HEADER_CORRECT + row);
        final String error = parsingProblem.getMessage();
        assertTrue(error.contains(ExchangeBean.UNSUPPORTED_TRANSACTION_TYPE.concat("sold")));
    }
}