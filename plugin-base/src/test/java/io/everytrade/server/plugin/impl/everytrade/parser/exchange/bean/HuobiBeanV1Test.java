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

import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.ExchangeBean.UNSUPPORTED_TRANSACTION_TYPE;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean.HuobiBeanV1.UNSUPPORTED_TYPE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class HuobiBeanV1Test {
    public static final String HEADER_CORRECT
        = "\uFEFF\"Time\",\"Type\",\"Pair\",\"Side\",\"Price\",\"Amount\",\"Total\",\"Fee\"\n";


    @Test
    void testWrongHeader() {
        final String headerWrong = "\"Time\",\"Type\",\"Pair\",\"Price\",\"Amount\",\"Total\",\"Fee\"\n";
        try {
            ParserTestUtils.testParsing(headerWrong);
            fail("No expected exception has been thrown.");
        } catch (ParsingProcessException ignored) {
        }
    }

    @Test
    void testCorrectParsingRawTransactionBuy() {
        final String row = "2020-03-31 21:31:43,Exchange,LTC/BTC,Buy,0.006040,0.8940,0.0053,0.00178800LTC,\n";
        final TransactionCluster actual = ParserTestUtils.getTransactionCluster(HEADER_CORRECT + row);
        final TransactionCluster expected = new TransactionCluster(
            new ImportedTransactionBean(
                null,
                Instant.parse("2020-03-31T21:31:43Z"),
                Currency.LTC,
                Currency.BTC,
                TransactionType.BUY,
                new BigDecimal("0.8940"),
                new BigDecimal("0.0059284116")
            ),
            List.of(
                new FeeRebateImportedTransactionBean(
                    null,
                    Instant.parse("2020-03-31T21:31:43Z"),
                    Currency.LTC,
                    Currency.LTC,
                    TransactionType.FEE,
                    new BigDecimal("0.00178800"),
                    Currency.LTC
                )
            )
        );

        ParserTestUtils.checkEqual(expected, actual);
    }

    @Test
    void testCorrectParsingRawTransactionBuyDiffFee() {
        final String row = "2020-03-31 21:31:43,Exchange,LTC/BTC,Buy,0.006040,0.8940,0.0053,0.00178800BTC,\n";
        final TransactionCluster actual = ParserTestUtils.getTransactionCluster(HEADER_CORRECT + row);
        final TransactionCluster expected = new TransactionCluster(
            new ImportedTransactionBean(
                null,
                Instant.parse("2020-03-31T21:31:43Z"),
                Currency.LTC,
                Currency.BTC,
                TransactionType.BUY,
                new BigDecimal("0.8940"),
                new BigDecimal("0.0059284116")
            ),
            List.of(
                new FeeRebateImportedTransactionBean(
                    null,
                    Instant.parse("2020-03-31T21:31:43Z"),
                    Currency.BTC,
                    Currency.BTC,
                    TransactionType.FEE,
                    new BigDecimal("0.00178800"),
                    Currency.BTC
                )
            )
        );

        ParserTestUtils.checkEqual(expected, actual);
    }


    @Test
    void testCorrectParsingRawTransactionSell() {
        final String row = "2020-03-31 21:31:24,Exchange,LTC/BTC,Sell,0.006036,0.7362,0.0044,0.00000888BTC,\n";
        final TransactionCluster actual = ParserTestUtils.getTransactionCluster(HEADER_CORRECT + row);
        final TransactionCluster expected = new TransactionCluster(
            new ImportedTransactionBean(
                null,
                Instant.parse("2020-03-31T21:31:24Z"),
                Currency.LTC,
                Currency.BTC,
                TransactionType.SELL,
                new BigDecimal("0.7362"),
                new BigDecimal("0.0059766368")
            ),
            List.of(
                new FeeRebateImportedTransactionBean(
                    null,
                    Instant.parse("2020-03-31T21:31:24Z"),
                    Currency.BTC,
                    Currency.BTC,
                    TransactionType.FEE,
                    new BigDecimal("0.00000888"),
                    Currency.BTC
                )
            )
        );

        ParserTestUtils.checkEqual(expected, actual);
    }

    @Test
    void testCorrectParsingRawTransactionSellDiffFee() {
        final String row = "2020-03-31 21:31:24,Exchange,LTC/BTC,Sell,0.006036,0.7362,0.0044,0.00000888LTC,\n";
        final TransactionCluster actual = ParserTestUtils.getTransactionCluster(HEADER_CORRECT + row);
        final TransactionCluster expected = new TransactionCluster(
            new ImportedTransactionBean(
                null,
                Instant.parse("2020-03-31T21:31:24Z"),
                Currency.LTC,
                Currency.BTC,
                TransactionType.SELL,
                new BigDecimal("0.7362"),
                new BigDecimal("0.0059766368")
            ),
            List.of(
                new FeeRebateImportedTransactionBean(
                    null,
                    Instant.parse("2020-03-31T21:31:24Z"),
                    Currency.LTC,
                    Currency.LTC,
                    TransactionType.FEE,
                    new BigDecimal("0.00000888"),
                    Currency.LTC
                )
            )
        );

        ParserTestUtils.checkEqual(expected, actual);
    }


    @Test
    void testUnknownType() {
        final String row = "2020-03-31 21:31:24,YYY,LTC/BTC,Sell,0.006036,0.7362,0.0044,0.00000888LTC,\n";
        final ParsingProblem parsingProblem = ParserTestUtils.getParsingProblem(HEADER_CORRECT + row);
        final String error = parsingProblem.getMessage();
        assertTrue(error.contains(UNSUPPORTED_TYPE.concat("YYY")));
    }

    @Test
    void testUnknownTransactionType() {
        final String row = "2020-03-31 21:31:24,Exchange,LTC/BTC,Depositz,0.006036,0.7362,0.0044,0.00000888LTC,\n";
        final ParsingProblem parsingProblem = ParserTestUtils.getParsingProblem(HEADER_CORRECT + row);
        final String error = parsingProblem.getMessage();
        assertTrue(error.contains(UNSUPPORTED_TRANSACTION_TYPE.concat("Depositz")));
    }

    // TO-DO
    //    @Test
    //    void testIgnoredFee() {
    //        final String row = "2020-03-31 21:31:24,Exchange,LTC/BTC,Sell,0.006036,0.7362,0.0044,0.00000888XXX,\n";
    //        final TransactionCluster transactionCluster = ParserTestUtils.getTransactionCluster(HEADER_CORRECT + row);
    //        assertEquals(1, transactionCluster.getFailedFeeTransactionCount());
    //    }

    @Test
    void testIgnoredTransactionType() {
        final String row = "2020-03-31 21:31:43,Exchange,LTC/BTC,Bought,0.006040,0.8940,0.0053,0.00178800LTC,\n";
        final ParsingProblem parsingProblem = ParserTestUtils.getParsingProblem(HEADER_CORRECT + row);
        assertTrue(parsingProblem.getMessage().contains(ExchangeBean.UNSUPPORTED_TRANSACTION_TYPE.concat("Bought")));
    }
}