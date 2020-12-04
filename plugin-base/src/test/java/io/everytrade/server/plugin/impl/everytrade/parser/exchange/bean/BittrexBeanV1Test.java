package io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean;

import io.everytrade.server.model.Currency;
import io.everytrade.server.model.TransactionType;
import io.everytrade.server.plugin.api.parser.ConversionStatistic;
import io.everytrade.server.plugin.api.parser.ImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.RowError;
import io.everytrade.server.plugin.impl.everytrade.parser.exception.ParsingProcessException;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.ExchangeBean;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;


class BittrexBeanV1Test {
    private static final String HEADER_CORRECT
        = "OrderUuid,Exchange,Type,Quantity,Limit,CommissionPaid,Price,Opened,Closed\n";

    @Test
    public void testWrongHeader() {
        String headerWrong = "OrderUuid,Exchange,Type,Quantity,Limit,CommissionPaid,Price,Opened,ClXsed\n";
        try {
            ParserTestUtils.testParsing(headerWrong);
            fail("No expected exception has been thrown.");
        } catch (ParsingProcessException e) {
        }
    }

    @Test
    public void testCorrectParsingRawTransaction()  {
        String row = "90d3c9f7-6ddf-4e5a-86ca-2132658ea436,EUR-ETH,LIMIT_BUY,0.03280507,0.03380240,0.00000277," +
            "0.00110889,2/14/19 15:01,2/14/19 15:01\n";
        ImportedTransactionBean txBeanParsed = ParserTestUtils.getTransactionBean(HEADER_CORRECT + row);
        ImportedTransactionBean txBeanCorrect = new ImportedTransactionBean(
            "90d3c9f7-6ddf-4e5a-86ca-2132658ea436",
            Instant.parse("2019-02-14T15:01:00Z"),
            Currency.ETH,
            Currency.EUR,
            TransactionType.BUY,
            new BigDecimal("0.03280507"),
            new BigDecimal("0.0338023970"),
            new BigDecimal("0.00000277")
        );
        ParserTestUtils.checkEqual(txBeanParsed, txBeanCorrect);
    }

    @Test
    public void testUnknonwExchange() {
        String row = "90d3c9f7-6ddf-4e5a-86ca-2132658ea436,ETH-BTK,LIMIT_BUY,0.03280507,0.03380240," +
            "0.00000277,0.00110889,2/14/19 15:01,2/14/19 15:01\n";
        RowError rowError = ParserTestUtils.getRowError(HEADER_CORRECT + row);
        assertNotNull(rowError);
        String error = rowError.getMessage();
        assertTrue(error.contains("ETH-BTK"));
    }

    @Test
    public void testIgnoredTransactionType() {
        String row = "90d3c9f7-6ddf-4e5a-86ca-2132658ea436,EUR-ETH,BUY,0.03280507,0.03380240,0.00000277," +
            "0.00110889,2/14/19 15:01,2/14/19 15:01\n";
        final ConversionStatistic conversionStatistic
            = ParserTestUtils.getConversionStatistic(HEADER_CORRECT + row);
        assertNotNull(conversionStatistic);
        assertEquals(1, conversionStatistic.getIgnoredRowsCount());
        assertTrue(
            conversionStatistic
                .getErrorRows()
                .get(0)
                .getMessage()
                .contains(ExchangeBean.UNSUPPORTED_TRANSACTION_TYPE.concat("BUY"))
        );
    }

}