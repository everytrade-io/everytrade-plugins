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
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean.CoinbaseProBeanV1.BASE_DIFFERS_FROM_UNIT_SIZE;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean.CoinbaseProBeanV1.QUOTE_DIFFERS_FROM_PRICE_FEE_TOTAL_UNIT;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class CoinbaseProBeanV1Test {
    private static final String HEADER_CORRECT = "portfolio,trade id,product,side,created at,size,size unit,price," +
        "fee,total,price/fee/total unit\n";

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
        final String headerWrong = "portfolio,tradeX,product,side,created at,size,size unit,price," +
            "fee,total,price/fee/total unit\n";
        try {
            ParserTestUtils.testParsing(headerWrong);
            fail("No expected exception has been thrown.");
        } catch (ParsingProcessException ignored) {
        }
    }

    @Test
    void testCorrectParsingRawTransactionBuy() {
        final String row = "default,0,LTC-EUR,BUY,2020-05-18T20:08:39.184Z,2.96875093,LTC,41.45," +
            "0.6152736302425,-123.6699996787425,EUR\n";
        final TransactionCluster actual = ParserTestUtils.getTransactionCluster(HEADER_CORRECT + row);
        final TransactionCluster expected = new TransactionCluster(
            new ImportedTransactionBean(
                "0",
                Instant.parse("2020-05-18T20:08:39.184Z"),
                Currency.LTC,
                Currency.EUR,
                TransactionType.BUY,
                new BigDecimal("2.96875093000000000"),
                new BigDecimal("41.45000000000000000")
            ),
            List.of(
                new FeeRebateImportedTransactionBean(
                    "0" + FEE_UID_PART,
                    Instant.parse("2020-05-18T20:08:39.184Z"),
                    Currency.EUR,
                    Currency.EUR,
                    TransactionType.FEE,
                    new BigDecimal("0.61527363024250000"),
                    Currency.EUR
                )
            )
        );
        ParserTestUtils.checkEqual(expected, actual);
    }

    @Test
    void testCorrectParsingRawTransactionSell() {
        final String row = "default,1,LTC-EUR,SELL,2020-05-18T20:10:26.735Z,2.81680093,LTC,41.35,0" +
            ".5823735922775,115.8923448632225,EUR\n";
        final TransactionCluster actual = ParserTestUtils.getTransactionCluster(HEADER_CORRECT + row);
        final TransactionCluster expected = new TransactionCluster(
            new ImportedTransactionBean(
                "1",
                Instant.parse("2020-05-18T20:10:26.735Z"),
                Currency.LTC,
                Currency.EUR,
                TransactionType.SELL,
                new BigDecimal("2.81680093000000000"),
                new BigDecimal("41.35000000000000000")
            ),
            List.of(
                new FeeRebateImportedTransactionBean(
                    "1" + FEE_UID_PART,
                    Instant.parse("2020-05-18T20:10:26.735Z"),
                    Currency.EUR,
                    Currency.EUR,
                    TransactionType.FEE,
                    new BigDecimal("0.58237359227750000"),
                    Currency.EUR
                )
            )
        );
        ParserTestUtils.checkEqual(expected, actual);
    }

    @Test
    void testDiffFeeCurrency() {
        final String row = "default,1,LTC-EUR,SELL,2020-05-18T20:10:26.735Z,2.81680093,LTC,41.35,0" +
            ".5823735922775,115.8923448632225,BTC\n";
        final ParsingProblem parsingProblem = ParserTestUtils.getParsingProblem(HEADER_CORRECT + row);
        final String error = parsingProblem.getMessage();
        assertTrue(error.contains(String.format(QUOTE_DIFFERS_FROM_PRICE_FEE_TOTAL_UNIT, "EUR", "BTC")));
    }

    @Test
    void testDiffSizeCurrency() {
        final String row = "default,1,LTC-EUR,SELL,2020-05-18T20:10:26.735Z,2.81680093,ETH,41.35,0" +
            ".5823735922775,115.8923448632225,BTC\n";
        final ParsingProblem parsingProblem = ParserTestUtils.getParsingProblem(HEADER_CORRECT + row);
        final String error = parsingProblem.getMessage();
        assertTrue(error.contains(String.format(BASE_DIFFERS_FROM_UNIT_SIZE, "LTC", "ETH")));
    }

    @Test
    void testIgnoredTransactionType() {
        // test wrong unsupported tx type
        final String row = "default,1,LTC-EUR,DEPOSITZ,2020-05-18T20:10:26.735Z,2.81680093,LTC,41.35,0" +
            ".5823735922775,115.8923448632225,BTC\n";
        final ParsingProblem parsingProblem = ParserTestUtils.getParsingProblem(HEADER_CORRECT + row);
        final String error = parsingProblem.getMessage();
        assertTrue(error.contains(ExchangeBean.UNSUPPORTED_TRANSACTION_TYPE.concat("DEPOSITZ")));
    }
}