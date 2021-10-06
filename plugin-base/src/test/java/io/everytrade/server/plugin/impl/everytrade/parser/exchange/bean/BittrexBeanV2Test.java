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

class BittrexBeanV2Test {
    private static final String HEADER_CORRECT
        = "Uuid,Exchange,TimeStamp,OrderType,Limit,Quantity,QuantityRemaining,Commission,Price," +
        "PricePerUnit,IsConditional,Condition,ConditionTarget,ImmediateOrCancel,Closed\n";

    @Test
    void testWrongHeader() {
        String headerWrong = "Uuid,Exchang_,TimeStamp,OrderType,Limit,Quantity,QuantityRemaining,Commission,Price," +
            "PricePerUnit,IsConditional,Condition,ConditionTarget,ImmediateOrCancel,Closed\n";
        try {
            ParserTestUtils.testParsing(headerWrong);
            fail("No expected exception has been thrown.");
        } catch (ParsingProcessException e) {
        }
    }

    @Test
    void testCorrectParsingRawTransaction() {
        final String row = "d-01,USD-BTC,7/28/2019 1:35:53 PM,LIMIT_SELL,9471.45900000," +
            "0.00321000,0.00000000,0.07600851,30.40340586,9471.46600000,False,,0.00000000,False,7/28/2019 1:35:53 PM\n";
        final TransactionCluster actual = ParserTestUtils.getTransactionCluster(HEADER_CORRECT + row);
        final TransactionCluster expected = new TransactionCluster(
            new BuySellImportedTransactionBean(
                "d-01",
                Instant.parse("2019-07-28T13:35:53Z"),
                Currency.BTC,
                Currency.USD,
                TransactionType.SELL,
                new BigDecimal("0.00321"),
                new BigDecimal("9471.466")
            ),
            List.of(
                new FeeRebateImportedTransactionBean(
                    "d-01" + FEE_UID_PART,
                    Instant.parse("2019-07-28T13:35:53Z"),
                    Currency.BTC,
                    Currency.USD,
                    TransactionType.FEE,
                    new BigDecimal("0.07600851"),
                    Currency.USD
                )
            )
        );
        ParserTestUtils.checkEqual(expected, actual);
    }

    @Test
    void testUnknonwExchange() {
        final String row = "d-01,BTC-UUU,7/28/2019 1:35:53 PM,LIMIT_SELL," +
            "9471.45900000,0.00321000,0.00000000,0.07600851,30.40340586,9471.46600000,False,,0.00000000,False," +
            "7/28/2019 1:35:53 PM\n";
        final ParsingProblem parsingProblem = ParserTestUtils.getParsingProblem(HEADER_CORRECT + row);
        final String error = parsingProblem.getMessage();
        assertTrue(error.contains("BTC-UUU"));
    }

    @Test
    void testIgnoredTransactionType() {
        final String row = "d-01,USD-BTC,7/28/2019 1:35:53 PM,BUY,9471.45900000," +
            "0.00321000,0.00000000,0.07600851,30.40340586,9471.46600000,False,,0.00000000,False,7/28/2019 1:35:53 PM\n";
        final ParsingProblem parsingProblem = ParserTestUtils.getParsingProblem(HEADER_CORRECT + row);
        assertTrue(parsingProblem.getMessage().contains(ExchangeBean.UNSUPPORTED_TRANSACTION_TYPE.concat("BUY")));
    }
}