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

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class BitstampBeanV1Test {
    public static final String HEADER_CORRECT = "Type,Datetime,Account,Amount,Value,Rate,Fee,Sub Type\n";


    @Test
    void testWrongHeader() {
        final String headerWrong = "Type,Datetime,Account,AmountX,Value,Rate,Fee,Sub Type\n";
        try {
            ParserTestUtils.testParsing(headerWrong);
            fail("No expected exception has been thrown.");
        } catch (ParsingProcessException e) {
        }
    }

    @Test
    void testCorrectParsingRawTransaction() {
        final String row = "Market,\"Feb. 14, 2019, 03:32 PM\",Main Account,0.00111167 BTC,3.96238096 USD,3564.35 " +
            "USD,0.00990595 USD,Buy\n";
        final TransactionCluster actual = ParserTestUtils.getTransactionCluster(HEADER_CORRECT + row);
        final TransactionCluster expected = new TransactionCluster(
            new ImportedTransactionBean(
                null,
                Instant.parse("2019-02-14T15:32:00Z"),
                Currency.BTC,
                Currency.USD,
                TransactionType.BUY,
                new BigDecimal("0.00111167"),
                new BigDecimal("3564.3499959520")
            ),
            List.of(
                new FeeRebateImportedTransactionBean(
                    null,
                    Instant.parse("2019-02-14T15:32:00Z"),
                    Currency.USD,
                    Currency.USD,
                    TransactionType.FEE,
                    new BigDecimal("0.00990595"),
                    Currency.USD
                )
            )
        );
        ParserTestUtils.checkEqual(expected, actual);
    }

    @Test
    void testWrongDateNameOfMonth() {
        final String row = "Market,\"XXX. 14, 2019, 03:32 PM\",Main Account,0.00111167 BTC,3.96238096 USD," +
            "3564.35 USD,0.00990595 USD,Buy\n";
        final ParsingProblem parsingProblem = ParserTestUtils.getParsingProblem(HEADER_CORRECT + row);
        final String error = parsingProblem.getMessage();
        assertTrue(error.contains("Cannot parse 'XXX. 14, 2019, 03:32 PM'"));
    }

    @Test
    void testUnknonwBase() {
        final String row = "Market,\"Feb. 14, 2019, 03:32 PM\",Main Account,0.00111167 XXX,3.96238096 USD," +
            "3564.35 USD,0.00990595 USD,Buy\n";
        ParsingProblem parsingProblem = ParserTestUtils.getParsingProblem(HEADER_CORRECT + row);
        final String error = parsingProblem.getMessage();
        assertTrue(error.contains("Unable to set value '0.00111167 XXX'"));
    }

    @Test
    void testIgnoredTransactionType() {
        final String row = "Market,\"Feb. 14, 2019, 03:32 PM\",Main Account,0.00111167 BTC,3.96238096 USD," +
            "3564.35 USD,0.00990595 USD,Cancel\n";
        final ParsingProblem parsingProblem = ParserTestUtils.getParsingProblem(HEADER_CORRECT + row);
        assertTrue(parsingProblem.getMessage().contains(ExchangeBean.UNSUPPORTED_TRANSACTION_TYPE.concat("Cancel")));
    }
}