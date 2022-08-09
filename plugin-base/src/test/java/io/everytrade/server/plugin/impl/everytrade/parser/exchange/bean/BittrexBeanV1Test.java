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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;


class BittrexBeanV1Test {
    private static final String HEADER_CORRECT
        = "OrderUuid,Exchange,Type,Quantity,Limit,CommissionPaid,Price,Opened,Closed\n";

    @Test
    void testWrongHeader() {
        final String headerWrong = "OrderUuid,Exchange,Type,Quantity,Limit,CommissionPaid,Price,Opened,ClXsed\n";
        try {
            ParserTestUtils.testParsing(headerWrong);
            fail("No expected exception has been thrown.");
        } catch (ParsingProcessException e) {
        }
    }

    @Test
    void testCorrectParsingRawTransaction()  {
        final String row = "9-01,EUR-ETH,LIMIT_BUY,0.03280507,0.03380240,0.00000277," +
            "0.00110889,2/14/19 15:01,2/14/19 15:01\n";
        final TransactionCluster actual = ParserTestUtils.getTransactionCluster(HEADER_CORRECT + row);
        final TransactionCluster expected = new TransactionCluster(
            new BuySellImportedTransactionBean(
                "9-01",
                Instant.parse("2019-02-14T15:01:00Z"),
                Currency.ETH,
                Currency.EUR,
                TransactionType.BUY,
                new BigDecimal("0.03280507"),
                new BigDecimal("0.0338023970")
            ),
            List.of(
                new FeeRebateImportedTransactionBean(
                    "9-01" + FEE_UID_PART,
                    Instant.parse("2019-02-14T15:01:00Z"),
                    Currency.EUR,
                    Currency.EUR,
                    TransactionType.FEE,
                    new BigDecimal("0.00000277"),
                    Currency.EUR
                )
            )
        );
        ParserTestUtils.checkEqual(expected, actual);
    }

    @Test
     void testUnknonwExchange() {
        final String row = "9-01,ETH-BTK,LIMIT_BUY,0.03280507,0.03380240," +
            "0.00000277,0.00110889,2/14/19 15:01,2/14/19 15:01\n";
        final ParsingProblem parsingProblem = ParserTestUtils.getParsingProblem(HEADER_CORRECT + row);
        final String error = parsingProblem.getMessage();
        assertTrue(error.contains("ETH-BTK"));
    }

    @Test
    void testIgnoredTransactionType() {
        final String row = "9-01,EUR-ETH,BUY,0.03280507,0.03380240,0.00000277," +
            "0.00110889,2/14/19 15:01,2/14/19 15:01\n";
        final ParsingProblem parsingProblem = ParserTestUtils.getParsingProblem(HEADER_CORRECT + row);
        assertTrue(parsingProblem.getMessage().contains(ExchangeBean.UNSUPPORTED_TRANSACTION_TYPE.concat("BUY")));
    }
}