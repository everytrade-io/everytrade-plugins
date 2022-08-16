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
import java.util.Collections;
import java.util.List;

import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.ExchangeBean.UNSUPPORTED_CURRENCY_PAIR;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean.BitfinexBeanV1.ILLEGAL_ZERO_VALUE_OF_AMOUNT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class BitfinexBeanV1Test {
    public static final String HEADER_CORRECT = "#,PAIR,AMOUNT,PRICE,FEE,FEE CURRENCY,DATE,ORDER ID\n";


    @Test
    void testWrongHeader() {
        final  String headerWrong = "#,PAIR,AMOUNT,PRICE,X,FEE CURRENCY,DATE,ORDER ID\n";
        try {
            ParserTestUtils.testParsing(headerWrong);
            fail("No expected exception has been thrown.");
        } catch (ParsingProcessException ignored) {
        }
    }

    @Test
    void testCorrectParsingRawTransactionBuy() {
        final String row = "0,BTC/USD,0.01048537,9212.82428,-0.00002097,BTC,04-02-20 16:52:06,1\n";
        final TransactionCluster actual = ParserTestUtils.getTransactionCluster(HEADER_CORRECT + row);
        final TransactionCluster expected = new TransactionCluster(
            new ImportedTransactionBean(
                "0",
                Instant.parse("2020-02-04T16:52:06Z"),
                Currency.BTC,
                Currency.USD,
                TransactionType.BUY,
                new BigDecimal("0.01048537"),
                new BigDecimal("9212.82428")
            ),
            List.of(
                new FeeRebateImportedTransactionBean(
                    "0-fee",
                    Instant.parse("2020-02-04T16:52:06Z"),
                    Currency.BTC,
                    Currency.BTC,
                    TransactionType.FEE,
                    new BigDecimal("0.00002097"),
                    Currency.BTC
                )
            )
        );
        ParserTestUtils.checkEqual(expected, actual);
    }

    @Test
    void testCorrectParsingRawTransactionBuyWithIgnoredChars() {
        final String row = "0,BTC/USD,0.01048537$,9 212.82428$,-0.00002097,BTC,04-02-20 16:52:06,1\n";
        final TransactionCluster actual = ParserTestUtils.getTransactionCluster(HEADER_CORRECT + row);
        final TransactionCluster expected = new TransactionCluster(
            new ImportedTransactionBean(
                "0",
                Instant.parse("2020-02-04T16:52:06Z"),
                Currency.BTC,
                Currency.USD,
                TransactionType.BUY,
                new BigDecimal("0.01048537"),
                new BigDecimal("9212.82428")
            ),
            List.of(
                new FeeRebateImportedTransactionBean(
                    "0-fee",
                    Instant.parse("2020-02-04T16:52:06Z"),
                    Currency.BTC,
                    Currency.BTC,
                    TransactionType.FEE,
                    new BigDecimal("0.00002097"),
                    Currency.BTC
                )
            )
        );
        ParserTestUtils.checkEqual(expected, actual);
    }

    @Test
    void testCorrectParsingRawTransactionBuyDiffFee() {
        final String row = "0,BTC/USD,0.01048537,9212.82428,-0.00002097,USD,04-02-20 16:52:06,1\n";
        final TransactionCluster actual = ParserTestUtils.getTransactionCluster(HEADER_CORRECT + row);
        final TransactionCluster expected = new TransactionCluster(
            new ImportedTransactionBean(
                "0",
                Instant.parse("2020-02-04T16:52:06Z"),
                Currency.BTC,
                Currency.USD,
                TransactionType.BUY,
                new BigDecimal("0.01048537"),
                new BigDecimal("9212.82428")
            ),
            List.of(
                new FeeRebateImportedTransactionBean(
                    "0-fee",
                    Instant.parse("2020-02-04T16:52:06Z"),
                    Currency.USD,
                    Currency.USD,
                    TransactionType.FEE,
                    new BigDecimal("0.00002097"),
                    Currency.USD
                )
            )
        );
        ParserTestUtils.checkEqual(expected, actual);
    }


    @Test
    void testCorrectParsingRawTransactionSell() {
        final String row = "0,BTC/USD,-0.0095,9214.7,-0.1750793,USD,04-02-20 16:49:55,3\n";
        final TransactionCluster actual = ParserTestUtils.getTransactionCluster(HEADER_CORRECT + row);
        final TransactionCluster expected = new TransactionCluster(
            new ImportedTransactionBean(
                "0",
                Instant.parse("2020-02-04T16:49:55Z"),
                Currency.BTC,
                Currency.USD,
                TransactionType.SELL,
                new BigDecimal("0.0095"),
                new BigDecimal("9214.7")
            ),
            List.of(
                new FeeRebateImportedTransactionBean(
                    "0-fee",
                    Instant.parse("2020-02-04T16:49:55Z"),
                    Currency.USD,
                    Currency.USD,
                    TransactionType.FEE,
                    new BigDecimal("0.1750793"),
                    Currency.USD
                )
            )
        );
        ParserTestUtils.checkEqual(expected, actual);
    }

    @Test
    void testCorrectParsingRawTransactionSellDiffFee() {
        final String row = "0,BTC/USD,-0.0095,9214.7,-0.000000123,BTC,04-02-20 16:49:55,3\n";
        final TransactionCluster actual = ParserTestUtils.getTransactionCluster(HEADER_CORRECT + row);
        final TransactionCluster expected = new TransactionCluster(
            new ImportedTransactionBean(
                "0",
                Instant.parse("2020-02-04T16:49:55Z"),
                Currency.BTC,
                Currency.USD,
                TransactionType.SELL,
                new BigDecimal("0.0095"),
                new BigDecimal("9214.7")
            ),
            List.of(
                new FeeRebateImportedTransactionBean(
                    "0-fee",
                    Instant.parse("2020-02-04T16:49:55Z"),
                    Currency.BTC,
                    Currency.BTC,
                    TransactionType.FEE,
                    new BigDecimal("0.000000123"),
                    Currency.BTC
                )
            )
        );
        ParserTestUtils.checkEqual(expected, actual);
    }


    @Test
    void testZerroAmount() {
        final String row = "0,BTC/USD,0.0,9212.82428,-0.00002097,BTC,04-02-20 16:52:06,1\n";
        final ParsingProblem parsingProblem = ParserTestUtils.getParsingProblem(HEADER_CORRECT + row);
        final String error = parsingProblem.getMessage();
        assertTrue(error.contains(ILLEGAL_ZERO_VALUE_OF_AMOUNT));
    }

    @Test
    void testFeeSkipped() {
        final String row = "0,BTC/USD,0.01048537,9212.82428,-0.00002097,XXX,04-02-20 16:52:06,1\n";
        final TransactionCluster actual = ParserTestUtils.getTransactionCluster(HEADER_CORRECT + row);
        final TransactionCluster expected = new TransactionCluster(
            new ImportedTransactionBean(
                "0",
                Instant.parse("2020-02-04T16:52:06Z"),
                Currency.BTC,
                Currency.USD,
                TransactionType.BUY,
                new BigDecimal("0.01048537"),
                new BigDecimal("9212.82428")
            ),
           Collections.emptyList()
        );
        expected.setFailedFee(1, "");
        ParserTestUtils.checkEqual(expected, actual);
    }


    @Test
    void testNotAllowedPair() {
        final String row = "0,CAD/USD,0.01048537,9212.82428,-0.00002097,USD,04-02-20 16:52:06,1\n";
        final ParsingProblem parsingProblem = ParserTestUtils.getParsingProblem(HEADER_CORRECT + row);
        final String error = parsingProblem.getMessage();
        assertTrue(error.contains(UNSUPPORTED_CURRENCY_PAIR.concat("CAD/USD")));
    }

    @Test
    void testCorrectParsingMoreDateFormats() {
        final String row = "0,BTC/USD,0.01048537,9212.82428,-0.00002097,BTC,12-02-20 16:52:06,1\n";
        final String row2 = "0,BTC/USD,0.01048537,9212.82428,-0.00002097,BTC,12-20-20 16:52:06,1\n";
        final TransactionCluster actual = ParserTestUtils.getTransactionCluster(HEADER_CORRECT + row + row2);
        final TransactionCluster expected = new TransactionCluster(
            new ImportedTransactionBean(
                "0",
                Instant.parse("2020-12-02T16:52:06Z"),
                Currency.BTC,
                Currency.USD,
                TransactionType.BUY,
                new BigDecimal("0.01048537"),
                new BigDecimal("9212.82428")
            ),
            List.of(
                new FeeRebateImportedTransactionBean(
                    "0-fee",
                    Instant.parse("2020-12-02T16:52:06Z"),
                    Currency.BTC,
                    Currency.BTC,
                    TransactionType.FEE,
                    new BigDecimal("0.00002097"),
                    Currency.BTC
                )
            )
        );
        ParserTestUtils.checkEqual(expected, actual);
    }

    @Test
    void testCorrectParsingMoreDateFormatsMilliseconds() {
        final String row = "0,BTC/USD,0.01048537,9212.82428,-0.00002097,BTC,12-02-20 16:52:06.123,1\n";
        final String row2 = "0,BTC/USD,0.01048537,9212.82428,-0.00002097,BTC,12-20-20 16:52:06.123,1\n";
        final TransactionCluster actual = ParserTestUtils.getTransactionCluster(HEADER_CORRECT + row + row2);
        final TransactionCluster expected = new TransactionCluster(
            new ImportedTransactionBean(
                "0",
                Instant.parse("2020-12-02T16:52:06.123Z"),
                Currency.BTC,
                Currency.USD,
                TransactionType.BUY,
                new BigDecimal("0.01048537"),
                new BigDecimal("9212.82428")
            ),
            List.of(
                new FeeRebateImportedTransactionBean(
                    "0-fee",
                    Instant.parse("2020-12-02T16:52:06.123Z"),
                    Currency.BTC,
                    Currency.BTC,
                    TransactionType.FEE,
                    new BigDecimal("0.00002097"),
                    Currency.BTC
                )
            )
        );
        ParserTestUtils.checkEqual(expected, actual);
    }

    @Test
    void testIgnoredFee() {
        final String row = "0,BTC/USD,0.01048537,9212.82428,-0.00002097,XXX,04-02-20 16:52:06,1\n";
        final TransactionCluster transactionCluster = ParserTestUtils.getTransactionCluster(HEADER_CORRECT + row);
        assertEquals(1, transactionCluster.getFailedFeeTransactionCount());
    }
}