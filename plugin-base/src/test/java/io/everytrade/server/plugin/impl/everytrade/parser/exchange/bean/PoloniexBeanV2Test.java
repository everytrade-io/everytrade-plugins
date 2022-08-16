package io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean;

import io.everytrade.server.model.Currency;
import io.everytrade.server.model.TransactionType;
import io.everytrade.server.plugin.api.parser.FeeRebateImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.ImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.ParsingProblem;
import io.everytrade.server.plugin.api.parser.TransactionCluster;
import io.everytrade.server.plugin.impl.everytrade.parser.exception.ParsingProcessException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.ExchangeBean.ILLEGAL_NEGATIVE_VALUES;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.ExchangeBean.UNSUPPORTED_CURRENCY_PAIR;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean.PoloniexBeanV1.UNSUPPORTED_CATEGORY;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean.PoloniexBeanV2.UNSUPPORTED_FEE_CURRENCY;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class PoloniexBeanV2Test {
    private static final String HEADER_CORRECT
        = "Date,Market,Category,Type,Price,Amount,Total,Fee,Order Number,Base Total Less Fee,Quote Total Less Fee,Fee Currency,Fee Total\n";

    @Test
    void testWrongHeader() {
        final String headerWrong = "Date,Market,Category,TypeX,Price,Amount,Total,Fee,Order Number," +
            "Base Total Less Fee,Quote Total Less Fee,Fee Currency,Fee Total\n";
        try {
            ParserTestUtils.testParsing(headerWrong);
            fail("No expected exception has been thrown.");
        } catch (ParsingProcessException ignored) {
        }
    }

    @Test
    void testCorrectParsingRawTransactionBuy() {
        final String row = "2020-01-31 10:56:44,BTC/USDT,Exchange,Buy,9353.07763368,0.5671584,5304.67654579,0.075%," +
            "441050233396,-5304.67654579,0.56673303,BTC,3.9785074\n";
        final TransactionCluster actual = ParserTestUtils.getTransactionCluster(HEADER_CORRECT + row);
        final TransactionCluster expected = new TransactionCluster(
            new ImportedTransactionBean(
                null,
                Instant.parse("2020-01-31T10:56:44Z"),
                Currency.BTC,
                Currency.USDT,
                TransactionType.BUY,
                new BigDecimal("0.5671584"),
                new BigDecimal("9353.0776336734")
            ),
            List.of(
                new FeeRebateImportedTransactionBean(
                    null,
                    Instant.parse("2020-01-31T10:56:44Z"),
                    Currency.BTC,
                    Currency.BTC,
                    TransactionType.FEE,
                    new BigDecimal("0.00042537"),
                    Currency.BTC
                )
            )
        );
        ParserTestUtils.checkEqual(expected, actual);
    }

    @Test
    void testCorrectParsingRawTransactionSell() {
        final String row = "2020-01-27 20:39:14,BTC/USDT,Exchange,Sell,8955,0.32647158,2923.5529989,0.075%," +
            "436733958991,2921.36033415,-0.32647158,USDT,2.19266474\n";
        final TransactionCluster actual = ParserTestUtils.getTransactionCluster(HEADER_CORRECT + row);
        final TransactionCluster expected = new TransactionCluster(
            new ImportedTransactionBean(
                null,
                Instant.parse("2020-01-27T20:39:14Z"),
                Currency.BTC,
                Currency.USDT,
                TransactionType.SELL,
                new BigDecimal("0.32647158"),
                new BigDecimal("8948.2837499975")
            ),
            List.of(
                new FeeRebateImportedTransactionBean(
                    null,
                    Instant.parse("2020-01-27T20:39:14Z"),
                    Currency.USDT,
                    Currency.USDT,
                    TransactionType.FEE,
                    new BigDecimal("2.19266475"),
                    Currency.USDT
                )
            )
        );
        ParserTestUtils.checkEqual(expected, actual);
    }

    @Test
    void testUnknownCategory() {
        final String row = "2020-01-27 20:39:14,BTC/USDT,XXX,Sell,8955,0.32647158,2923.5529989,0.075%," +
            "436733958991,2921.36033415,-0.32647158,USDT,2.19266474\n";
        final ParsingProblem parsingProblem = ParserTestUtils.getParsingProblem(HEADER_CORRECT + row);
        final String error = parsingProblem.getMessage();
        assertTrue(error.contains(UNSUPPORTED_CATEGORY.concat("XXX")));
    }

    @Test
    void testNotAllowedPair() {
        final String row = "2020-01-27 20:39:14,USDT/BTC,Exchange,Sell,8955,0.32647158,2923.5529989,0.075%," +
            "436733958991,2921.36033415,-0.32647158,USDT,2.19266474\n";
        final ParsingProblem parsingProblem = ParserTestUtils.getParsingProblem(HEADER_CORRECT + row);
        final String error = parsingProblem.getMessage();
        assertTrue(error.contains(UNSUPPORTED_CURRENCY_PAIR.concat("USDT/BTC")));
    }

    @Test
    void testNegativeValues() {
        final String row = "2020-01-27 20:39:14,BTC/USDT,Exchange,Sell,8955,-0.32647158,-2923.5529989,0.075%," +
            "436733958991,2921.36033415,-0.32647158,USDT,2.19266474\n";
        final ParsingProblem parsingProblem = ParserTestUtils.getParsingProblem(HEADER_CORRECT + row);
        final String error = parsingProblem.getMessage();
        assertTrue(error.contains(ILLEGAL_NEGATIVE_VALUES.concat("[0, 1]")));
    }

    @Test
    void testNotAllowedFeeCurrency() {
        final String row = "2020-01-27 20:39:14,BTC/USDT,Exchange,Sell,8955,0.32647158,2923.5529989,0.075%," +
            "436733958991,2921.36033415,-0.32647158,BTC,2.19266474\n";
        final ParsingProblem parsingProblem = ParserTestUtils.getParsingProblem(HEADER_CORRECT + row);
        final String error = parsingProblem.getMessage();
        assertTrue(error.contains(UNSUPPORTED_FEE_CURRENCY.concat("'BTC'")));
    }
}