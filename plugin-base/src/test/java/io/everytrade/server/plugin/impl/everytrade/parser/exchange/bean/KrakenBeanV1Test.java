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

class KrakenBeanV1Test {
    private static final String HEADER_CORRECT
        = "txid,ordertxid,pair,time,type,ordertype,price,cost,fee,vol,margin,misc,ledgers\n";

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
        final String headerWrong = "txid,ordertxid,pair,time,type,ordertype,price,*ost,fee,vol,margin,misc,ledgers\n";
        try {
            ParserTestUtils.testParsing(headerWrong);
            fail("No expected exception has been thrown.");
        } catch (ParsingProcessException e) {
        }
    }


    @Test
    void testCorrectParsingQuotationMarks()  {
        final String row = "TTT,OI,XXBTZUSD,2019-07-29 17:04:41,buy," +
            "limit,9480.3,18.9606,\"0.0493\",0.002,0,,\"LX,JX\"\n";
        final ImportedTransactionBean txBeanParsed = ParserTestUtils.getTransactionBean(HEADER_CORRECT + row);
        final ImportedTransactionBean txBeanCorrect = new ImportedTransactionBean(
            "TTT",
            Instant.parse("2019-07-29T17:04:41Z"),
            Currency.BTC,
            Currency.USD,
            TransactionType.BUY,
            new BigDecimal("0.002"),
            new BigDecimal("9480.3"),
            new BigDecimal("0.0493")
        );
        ParserTestUtils.checkEqual(txBeanParsed, txBeanCorrect);
    }

    @Test
    void testCorrectParsingRawTransaction() {
        final String row = "TTT,OI,XXBTZUSD,2019-07-29 17:04:41.51,buy," +
            "limit,9480.3,18.9606,0.0493,0.002,0,,\"LX,JX\"\n";
        final ImportedTransactionBean txBeanParsed = ParserTestUtils.getTransactionBean(HEADER_CORRECT + row);
        final ImportedTransactionBean txBeanCorrect = new ImportedTransactionBean(
            "TTT",
            Instant.parse("2019-07-29T17:04:41.51Z"),
            Currency.BTC,
            Currency.USD,
            TransactionType.BUY,
            new BigDecimal("0.002"),
            new BigDecimal("9480.3"),
            new BigDecimal("0.0493")
        );
        ParserTestUtils.checkEqual(txBeanParsed, txBeanCorrect);
    }

    @Test
    void testUnknonwExchangePair() {
        final String row = "TTT,OI,XXBTUSD,2019-07-29 17:04:41.51,buy," +
            "limit,9480.3,18.9606,0.0493,0.002,0,,\"LX,JX\"\n";
        final RowError rowError = ParserTestUtils.getRowError(HEADER_CORRECT + row);
        assertNotNull(rowError);
        final String error = rowError.getMessage();
        System.out.println(error);
        assertTrue(error.contains("Unable to set value 'XXBTUSD'"));
    }

    @Test
    void testCorrectParsingRawTransaction24hTimeFormat() {
        final String row = "TTT,OI,XXBTZUSD,2019-07-29 12:04:41.1451,buy," +
            "limit,9480.3,18.9606,0.0493,0.002,0,,\"LX,JX\"\n";
        final ImportedTransactionBean txBeanParsed = ParserTestUtils.getTransactionBean(HEADER_CORRECT + row);
        final ImportedTransactionBean txBeanCorrect = new ImportedTransactionBean(
            "TTT",
            Instant.parse("2019-07-29T12:04:41.1451Z"),
            Currency.BTC,
            Currency.USD,
            TransactionType.BUY,
            new BigDecimal("0.002"),
            new BigDecimal("9480.3"),
            new BigDecimal("0.0493")
        );
        ParserTestUtils.checkEqual(txBeanParsed, txBeanCorrect);
    }

    @Test
    void testIgnoredTransactionType() {
        final String row = "TTT,OI,XXBTZUSD,2019-07-29 17:04:41,sold," +
            "limit,9480.3,18.9606,\"0.0493\",0.002,0,,\"LX,JX\"\n";
        final ConversionStatistic conversionStatistic
            = ParserTestUtils.getConversionStatistic(HEADER_CORRECT + row);
        assertNotNull(conversionStatistic);
        assertEquals(1, conversionStatistic.getIgnoredRowsCount());
        assertTrue(
            conversionStatistic
                .getErrorRows()
                .get(0)
                .getMessage()
                .contains(ExchangeBean.UNSUPPORTED_TRANSACTION_TYPE.concat("sold"))
        );
    }
}