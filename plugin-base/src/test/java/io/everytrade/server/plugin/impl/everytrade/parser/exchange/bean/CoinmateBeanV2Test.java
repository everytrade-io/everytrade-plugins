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

class CoinmateBeanV2Test {
    private static final String HEADER_CORRECT = "?Transaction id;Date;Email;Name;Type;Type detail;Currency " +
        "amount;Amount;Currency price;Price;Currency fee;Fee;Currency total;Total;Description;Status;" +
        "Currency first balance after;First balance after;Currency second balance after;Second balance after\n";

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
        final String headerWrong = "Transaction id;Date;Email;Name;Type;Type detail;Currency amount;" +
            "Amount;Currency price;Price;Currency fee;Fee;Currency total;Total;Description;Status;" +
            "Currency first balance after;First balance after;Currency second balance after;Second balance after\n";
        try {
            ParserTestUtils.testParsing(headerWrong);
            fail("No expected exception has been thrown.");
        } catch (ParsingProcessException e) {
        }
    }

    @Test
    void testCorrectParsingRawTransaction()  {
        final String row = "1;2020-02-10 19:08:30;mail;name;Quick trade;SELL;BTC;" +
            "-0.047668;EUR;9011.62834249;EUR;0.85913259;EUR;428.70716724;;OK;BTC;1.99655368;EUR;83230.41972657\n";
        final ImportedTransactionBean txBeanParsed  = ParserTestUtils.getTransactionBean(HEADER_CORRECT + row);
        final ImportedTransactionBean txBeanCorrect = new ImportedTransactionBean(
            "1",
            Instant.parse("2020-02-10T19:08:30Z"),
            Currency.BTC,
            Currency.EUR,
            TransactionType.SELL,
            new BigDecimal("0.04766800"),
            new BigDecimal("9011.62834249"),
            new BigDecimal("0.85913259")
        );
        ParserTestUtils.checkEqual(txBeanParsed, txBeanCorrect);
    }

    @Test
    void testCorrectParsingRawTransactionDiffSignAmount()  {
        final String row = "1;2020-02-10 19:08:30;mail;name;Quick trade;SELL;BTC;" +
            "0.047668;EUR;9011.62834249;EUR;0.85913259;EUR;428.70716724;;OK;BTC;1.99655368;EUR;83230.41972657\n";
        final ImportedTransactionBean txBeanParsed  = ParserTestUtils.getTransactionBean(HEADER_CORRECT + row);
        final ImportedTransactionBean txBeanCorrect = new ImportedTransactionBean(
            "1",
            Instant.parse("2020-02-10T19:08:30Z"),
            Currency.BTC,
            Currency.EUR,
            TransactionType.SELL,
            new BigDecimal("0.04766800"),
            new BigDecimal("9011.62834249"),
            new BigDecimal("0.85913259")
        );
        ParserTestUtils.checkEqual(txBeanParsed, txBeanCorrect);
    }

    @Test
    void testCorrectParsingRawTransactionAnotherAction()  {
        final String row = "1;2020-02-10 19:08:30;mail;name;Quick trade;QUICK_SELL;" +
            "BTC;-0.047668;EUR;9011.62834249;EUR;0.85913259;EUR;428.70716724;;OK;BTC;1.99655368;EUR;83230.41972657\n";
        final ImportedTransactionBean txBeanParsed  = ParserTestUtils.getTransactionBean(HEADER_CORRECT + row);
        final ImportedTransactionBean txBeanCorrect = new ImportedTransactionBean(
            "1",
            Instant.parse("2020-02-10T19:08:30Z"),
            Currency.BTC,
            Currency.EUR,
            TransactionType.SELL,
            new BigDecimal("0.04766800"),
            new BigDecimal("9011.62834249"),
            new BigDecimal("0.85913259")
        );
        ParserTestUtils.checkEqual(txBeanParsed, txBeanCorrect);
    }

    @Test
    void testDifferentCurrencies()  {
        final String row = "1;2020-02-10 19:08:30;mail;name;Quick trade;SELL;BTC;" +
            "-0.047668;EUR;9011.62834249;USD;0.85913259;EUR;428.70716724;;OK;BTC;1.99655368;EUR;83230.41972657\n";
        final RowError rowError = ParserTestUtils.getRowError(HEADER_CORRECT + row);
        assertNotNull(rowError);
        final String error = rowError.getMessage();
        assertTrue(error.contains("Price currecy(EUR) and fee currency(USD) are different."));
    }

    @Test
    void testIgnoredTransactionType() {
        final String row = "1;2020-02-10 19:08:30;mail;name;Quick trade;CANCEL;BTC;" +
            "-0.047668;EUR;9011.62834249;EUR;0.85913259;EUR;428.70716724;;OK;BTC;1.99655368;EUR;83230.41972657\n";
        final ConversionStatistic conversionStatistic
            = ParserTestUtils.getConversionStatistic(HEADER_CORRECT + row);
        assertNotNull(conversionStatistic);
        assertEquals(1, conversionStatistic.getIgnoredRowsCount());
        assertTrue(
            conversionStatistic
                .getErrorRows()
                .get(0)
                .getMessage()
                .contains(ExchangeBean.UNSUPPORTED_TRANSACTION_TYPE.concat("CANCEL"))
        );
    }

    @Test
    void testIgnoredStatus() {
        final String row = "1;2020-02-10 19:08:30;mail;name;Quick trade;SELL;BTC;" +
            "-0.047668;EUR;9011.62834249;EUR;0.85913259;EUR;428.70716724;;NO;BTC;1.99655368;EUR;83230.41972657\n";
        final ConversionStatistic conversionStatistic
            = ParserTestUtils.getConversionStatistic(HEADER_CORRECT + row);
        assertNotNull(conversionStatistic);
        assertEquals(1, conversionStatistic.getIgnoredRowsCount());
        assertTrue(
            conversionStatistic
                .getErrorRows()
                .get(0)
                .getMessage()
                .contains(ExchangeBean.UNSUPPORTED_STATUS_TYPE.concat("NO"))
        );
    }
}