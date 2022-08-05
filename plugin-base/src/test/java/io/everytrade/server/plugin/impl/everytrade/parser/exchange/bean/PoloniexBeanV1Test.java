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

import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.ExchangeBean.UNSUPPORTED_TRANSACTION_TYPE;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean.PoloniexBeanV1.UNSUPPORTED_CATEGORY;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class PoloniexBeanV1Test {
    private static final String HEADER_CORRECT
        = "Date,Market,Category,Type,Price,Amount,Total,Fee,Order Number,Base Total Less Fee,Quote Total Less Fee\n";

    @Test
    void testWrongHeader() {
        final String headerWrong = "Date,Market,Type,Total,Fee,Order Number,Base Total Less Fee,Quote Total Less Fee\n";
        try {
            ParserTestUtils.testParsing(headerWrong);
            fail("No expected exception has been thrown.");
        } catch (ParsingProcessException ignored) {
        }
    }

    @Test
    void testCorrectParsingRawTransactionBuy() {
        final String row = "2020-03-31 12:11:51,LTC/BTC,Exchange,Buy,0.00606752,1.97596992,0.01198923,0.09%," +
            "1,-0.01198923,1.97419154\n";
        final TransactionCluster actual = ParserTestUtils.getTransactionCluster(HEADER_CORRECT + row);
        final TransactionCluster expected = new TransactionCluster(
            new BuySellImportedTransactionBean(
                null,
                Instant.parse("2020-03-31T12:11:51Z"),
                Currency.LTC,
                Currency.BTC,
                TransactionType.BUY,
                new BigDecimal("1.97419154"),
                new BigDecimal("0.0060729822")
            ),
            Collections.emptyList()
        );
        ParserTestUtils.checkEqual(expected, actual);
    }

    @Test
    void testCorrectParsingRawTransactionSell() {
        final String row = "2020-03-31 12:11:33,LTC/BTC,Exchange,Sell,0.00606592,1.97827088,0.01200003,0.09%," +
            "0,0.01198923,-1.97827088\n";
        final TransactionCluster actual = ParserTestUtils.getTransactionCluster(HEADER_CORRECT + row);
        final TransactionCluster expected = new TransactionCluster(
            new BuySellImportedTransactionBean(
                null,
                Instant.parse("2020-03-31T12:11:33Z"),
                Currency.LTC,
                Currency.BTC,
                TransactionType.SELL,
                new BigDecimal("1.97827088"),
                new BigDecimal("0.0060604592")
            ),
            Collections.emptyList()
        );
        ParserTestUtils.checkEqual(expected, actual);
    }

    @Test
    void testUnknownCategory() {
        final String row = "2020-03-31 12:11:33,LTC/BTC,XXX,Sell,0.00606592,1.97827088,0.01200003,0.09%,0," +
            "0.01198923,-1.97827088\n";
        final ParsingProblem parsingProblem = ParserTestUtils.getParsingProblem(HEADER_CORRECT + row);
        final String error = parsingProblem.getMessage();
        assertTrue(error.contains(UNSUPPORTED_CATEGORY.concat("XXX")));
    }

    @Test
    void testIgnoredTransactionType() {
        final String row = "2020-03-31 12:11:51,LTC/BTC,Exchange,Sold,0.00606752,1.97596992,0.01198923,0.09%," +
            "1,-0.01198923,1.97419154\n";
        final ParsingProblem parsingProblem = ParserTestUtils.getParsingProblem(HEADER_CORRECT + row);
        final String error = parsingProblem.getMessage();
        assertTrue(error.contains(UNSUPPORTED_TRANSACTION_TYPE.concat("Sold")));
    }
}