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

import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.ExchangeBean.UNSUPPORTED_CURRENCY_PAIR;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class BinanceBeanV1Test {
    public static final String HEADER_CORRECT = "Date(UTC);Market;Type;Price;Amount;Total;Fee;Fee Coin\n";


    @Test
    public void testWrongHeader() {
        String headerWrong = "Date(UTC);Market;Price;Amount;Total;Fee;Fee Coin\n";
        try {
            ParserTestUtils.testParsing(headerWrong);
            fail("No expected exception has been thrown.");
        } catch (ParsingProcessException e) {
        }
    }

    @Test
    public void testCorrectParsingRawTransactionBuy() {
        String row = "2020-02-04 16:19:07;LTCBTC;BUY;0.007393;1.61;0.01190273;0.00161;LTC\n";
        final ImportedTransactionBean txBeanParsed = ParserTestUtils.getTransactionBean(HEADER_CORRECT + row);
        final ImportedTransactionBean txBeanCorrect = new ImportedTransactionBean(
            null,
            Instant.parse("2020-02-04T16:19:07Z"),
            Currency.LTC,
            Currency.BTC,
            TransactionType.BUY,
            new BigDecimal("1.60839"),
            new BigDecimal("0.0074004004"),
            new BigDecimal("0")
        );
        ParserTestUtils.checkEqual(txBeanParsed, txBeanCorrect);
    }

    @Test
    public void testCorrectParsingRawTransactionBuyDiffFeeCurrency() {
        String row = "2020-02-04 16:19:07;LTCBTC;BUY;0.007393;1.61;0.01190273;0.00161;BTC\n";
        final ImportedTransactionBean txBeanParsed = ParserTestUtils.getTransactionBean(HEADER_CORRECT + row);
        final ImportedTransactionBean txBeanCorrect = new ImportedTransactionBean(
            null,
            Instant.parse("2020-02-04T16:19:07Z"),
            Currency.LTC,
            Currency.BTC,
            TransactionType.BUY,
            new BigDecimal("1.61"),
            new BigDecimal("0.008393"),
            new BigDecimal("0")
        );
        ParserTestUtils.checkEqual(txBeanParsed, txBeanCorrect);
    }

    @Test
    public void testCorrectParsingRawTransactionSell() {
        String row = "2020-02-03 11:09:51;LTCBTC;SELL;0.007497;1.72;0.01289484;0.00001289;BTC\n";
        final ImportedTransactionBean txBeanParsed = ParserTestUtils.getTransactionBean(HEADER_CORRECT + row);
        final ImportedTransactionBean txBeanCorrect = new ImportedTransactionBean(
            null,
            Instant.parse("2020-02-03T11:09:51Z"),
            Currency.LTC,
            Currency.BTC,
            TransactionType.SELL,
            new BigDecimal("1.72"),
            new BigDecimal("0.0074895058"),
            new BigDecimal("0")
        );
        ParserTestUtils.checkEqual(txBeanParsed, txBeanCorrect);
    }

    @Test
    public void testCorrectParsingRawTransactionSellDiffFeeCurrency() {
        String row = "2020-02-03 11:09:51;LTCBTC;SELL;0.007497;1.72;0.01289484;0.00001289;LTC\n";
        final ImportedTransactionBean txBeanParsed = ParserTestUtils.getTransactionBean(HEADER_CORRECT + row);
        final ImportedTransactionBean txBeanCorrect = new ImportedTransactionBean(
            null,
            Instant.parse("2020-02-03T11:09:51Z"),
            Currency.LTC,
            Currency.BTC,
            TransactionType.SELL,
            new BigDecimal("1.72001289"),
            new BigDecimal("0.0074969438"),
            new BigDecimal("0")
        );
        ParserTestUtils.checkEqual(txBeanParsed, txBeanCorrect);
    }


    @Test
    public void testUnknownPair() {
        String row = "2020-02-03 11:09:51;BTCLTC;SELL;0.007497;1.72;0.01289484;0.00001289;LTC\n";
        RowError rowError = ParserTestUtils.getRowError(HEADER_CORRECT + row);
        assertNotNull(rowError);
        String error = rowError.getMessage();
        assertTrue(error.contains(UNSUPPORTED_CURRENCY_PAIR.concat("BTCLTC")));
    }

    @Test
    public void testCorrectParsingRawTransactionBuyFeeSkipped() {
        String row = "2020-02-04 16:19:07;LTCBTC;BUY;0.007393;1.61;0.01190273;0.00161;BNB\n";
        final ImportedTransactionBean txBeanParsed = ParserTestUtils.getTransactionBean(HEADER_CORRECT + row);
        final ImportedTransactionBean txBeanCorrect = new ImportedTransactionBean(
            null,
            Instant.parse("2020-02-04T16:19:07Z"),
            Currency.LTC,
            Currency.BTC,
            TransactionType.BUY,
            new BigDecimal("1.61"),
            new BigDecimal("0.007393"),
            new BigDecimal("0")
        );
        ParserTestUtils.checkEqual(txBeanParsed, txBeanCorrect);
    }

    @Test
    public void testIgnoredFee() {
        String row = "2020-02-04 16:19:07;LTCBTC;BUY;0.007393;1.61;0.01190273;0.00161;BNB\n";
        final ConversionStatistic conversionStatistic = ParserTestUtils.getConversionStatistic(HEADER_CORRECT + row);
        assertNotNull(conversionStatistic);
        assertEquals(1, conversionStatistic.getIgnoredFeeTransactionCount());
    }

    @Test
    public void testIgnoredTransactionType() {
        String row = "2020-02-04 16:19:07;LTCBTC;DEPOSIT;0.007393;1.61;0.01190273;0.00161;LTC\n";
        final ConversionStatistic conversionStatistic = ParserTestUtils.getConversionStatistic(HEADER_CORRECT + row);
        assertNotNull(conversionStatistic);
        assertEquals(1, conversionStatistic.getIgnoredRowsCount());
        assertTrue(
            conversionStatistic
                .getErrorRows()
                .get(0)
                .getMessage()
                .contains(ExchangeBean.UNSUPPORTED_TRANSACTION_TYPE.concat("DEPOSIT"))
        );
    }
}