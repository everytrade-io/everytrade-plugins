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

class CoinbaseBeanV2Test {
    private static final String HEADER_CORRECT
        = "Timestamp,Transaction Type,Asset,Quantity Transacted,USD Spot Price at Transaction,USD Subtotal,USD Total " +
        "(inclusive of fees),USD Fees,Notes\n";

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
            = "Timestamp,Transaction Type,Asset,Quantity Transacted,USD Spot Price at Transaction,USD Subtotal," +
            "USD Total (inclusive of fees),XXX Fees,Notes\n";
        try {
            ParserTestUtils.testParsing(headerWrong);
            fail("No expected exception has been thrown.");
        } catch (ParsingProcessException ignored) {
        }
    }

    @Test
    void testCorrectParsingRawTransactionBuy() {
        final String row = "2020-03-29T12:49:52Z,Buy,BTC,0.00162435,5546.83,9.01,10.00,0.990000," +
            "Bought 0.00162435 BTC for $10.00 USD";
        final TransactionCluster actual = ParserTestUtils.getTransactionCluster(HEADER_CORRECT + row);
        final TransactionCluster expected = new TransactionCluster(
            new BuySellImportedTransactionBean(
                null,
                Instant.parse("2020-03-29T12:49:52Z"),
                Currency.BTC,
                Currency.USD,
                TransactionType.BUY,
                new BigDecimal("0.00162435"),
                new BigDecimal("5546.8341182627")
            ),
            List.of(
                new FeeRebateImportedTransactionBean(
                    null,
                    Instant.parse("2020-03-29T12:49:52Z"),
                    Currency.BTC,
                    Currency.USD,
                    TransactionType.FEE,
                    new BigDecimal("0.99"),
                    Currency.USD
                )
            ),
            0
        );
        ParserTestUtils.checkEqual(expected, actual);
    }

    @Test
    void testCorrectParsingRawTransactionSell() {
        final String row = "2020-03-09T05:17:11Z,Sell,BTC,0.03517833,6831.48,240.32,236.74,3.58," +
            "Sold 0.03517833 BTC for $236.74 USD";
        final TransactionCluster actual = ParserTestUtils.getTransactionCluster(HEADER_CORRECT + row);
        final TransactionCluster expected = new TransactionCluster(
            new BuySellImportedTransactionBean(
                null,
                Instant.parse("2020-03-09T05:17:11Z"),
                Currency.BTC,
                Currency.USD,
                TransactionType.SELL,
                new BigDecimal("0.03517833"),
                new BigDecimal("6831.4783561357")
            ),
            List.of(
                new FeeRebateImportedTransactionBean(
                    null,
                    Instant.parse("2020-03-09T05:17:11Z"),
                    Currency.BTC,
                    Currency.USD,
                    TransactionType.FEE,
                    new BigDecimal("3.58"),
                    Currency.USD
                )
            ),
            0
        );
        ParserTestUtils.checkEqual(expected, actual);
    }

    @Test
    void testIgnoredTransactionType() {
        final String row = "2020-09-27T18:36:58Z,Coinbase Earn,BTC,0.03182812,9287.38,295.60,300.00," +
            "4.40,Bought 0,03182812 BTC for € 300,00 EUR\n";
        final ParsingProblem parsingProblem = ParserTestUtils.getParsingProblem(HEADER_CORRECT + row);
        final String error = parsingProblem.getMessage();
        assertTrue(error.contains(ExchangeBean.UNSUPPORTED_TRANSACTION_TYPE.concat("Coinbase Earn")));
    }
}