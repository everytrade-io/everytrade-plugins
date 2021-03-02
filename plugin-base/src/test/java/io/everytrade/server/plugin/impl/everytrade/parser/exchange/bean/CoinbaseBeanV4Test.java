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

class CoinbaseBeanV4Test {
    private static final String HEADER_CORRECT
        = "Timestamp,Transaction Type,Asset,Quantity Transacted,CZK Spot Price at Transaction,CZK Subtotal,CZK Total " +
        "(inclusive of fees),CZK Fees,Notes\n";

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
            = "Timestamp,Transaction Type,Asset,Quantity Transacted,USD Spot Price at Transaction,CZK Subtotal," +
            "CZK Total (inclusive of fees),CZK Fees,Notes\n";
        try {
            ParserTestUtils.testParsing(headerWrong);
            fail("No expected exception has been thrown.");
        } catch (ParsingProcessException ignored) {
        }
    }

    @Test
    void testCorrectParsingRawTransactionBuy() {
        final String row = "2020-03-29T12:49:52Z,Buy,LTC,1.124,129.83,145.90,150.00,4.10,Bought 1.124 LTC for 150.00 " +
            "CZK";
        final TransactionCluster actual = ParserTestUtils.getTransactionCluster(HEADER_CORRECT + row);
        final TransactionCluster expected = new TransactionCluster(
            new BuySellImportedTransactionBean(
                null,
                Instant.parse("2020-03-29T12:49:52Z"),
                Currency.LTC,
                Currency.CZK,
                TransactionType.BUY,
                new BigDecimal("1.124"),
                new BigDecimal("129.8042704626")
            ),
            List.of(
                new FeeRebateImportedTransactionBean(
                    null,
                    Instant.parse("2020-03-29T12:49:52Z"),
                    Currency.LTC,
                    Currency.CZK,
                    TransactionType.FEE,
                    new BigDecimal("4.10"),
                    Currency.CZK
                )
            ),
            0
        );
        ParserTestUtils.checkEqual(expected, actual);
    }

    @Test
    void testCorrectParsingRawTransactionSell() {
        final String row = "2020-03-09T05:17:11Z,Sell,LTC,0.5,144.00,72.00,73.00,1.00,Sold 0.5 LTC for 73.00 CZK";
        final TransactionCluster actual = ParserTestUtils.getTransactionCluster(HEADER_CORRECT + row);
        final TransactionCluster expected = new TransactionCluster(
            new BuySellImportedTransactionBean(
                null,
                Instant.parse("2020-03-09T05:17:11Z"),
                Currency.LTC,
                Currency.CZK,
                TransactionType.SELL,
                new BigDecimal("0.5"),
                new BigDecimal("144")
            ),
            List.of(
                new FeeRebateImportedTransactionBean(
                    null,
                    Instant.parse("2020-03-09T05:17:11Z"),
                    Currency.LTC,
                    Currency.CZK,
                    TransactionType.FEE,
                    new BigDecimal("1"),
                    Currency.CZK
                )
            ),
            0
        );
        ParserTestUtils.checkEqual(expected, actual);
    }

    @Test
    void testIgnoredTransactionType() {
        final String row = "2020-03-09T05:17:11Z,Receive,LTC,0.5,144.00,72.00,73.00,1.00,Sold 0.5 LTC for 73.00 CZK";
        final ParsingProblem parsingProblem = ParserTestUtils.getParsingProblem(HEADER_CORRECT + row);
        final String error = parsingProblem.getMessage();
        assertTrue(error.contains(ExchangeBean.UNSUPPORTED_TRANSACTION_TYPE.concat("Receive")));
    }
}