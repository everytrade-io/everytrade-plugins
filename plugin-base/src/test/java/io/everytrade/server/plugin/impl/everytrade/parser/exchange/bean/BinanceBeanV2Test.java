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

class BinanceBeanV2Test {
    public static final String HEADER_CORRECT
        = "Date(UTC);Pair;Type;Order Price;Order Amount;AvgTrading Price;Filled;Total;status\n";


    @Test
    void testWrongHeader() {
        final String headerWrong = "Date(UTC);Pair;Type;OrdeX Price;Order Amount;AvgTrading Price;Filled;" +
            "Total;status\n";
        try {
            ParserTestUtils.testParsing(headerWrong);
            fail("No expected exception has been thrown.");
        } catch (ParsingProcessException e) {
        }
    }

    @Test
    void testCorrectParsingRawTransactionBuy() {
        final String row0 = "2020-03-19 17:02:52;BTCUSDT;BUY;0.0;0.041600;6236.39;0.041600;259.44;Filled\n";
        final String row1 = ";Date(UTC);Trading Price;Filled;Total;Fee;;;\n";
        final String row2 = ";2020-03-19 17:02:52;6236.39;0.041600;259.43382400;0.01612653BNB;;;\n";

        final ImportedTransactionBean txBeanParsed
            = ParserTestUtils.getTransactionBean(HEADER_CORRECT + row0 + row1 + row2);
        final ImportedTransactionBean txBeanCorrect = new ImportedTransactionBean(
            null,
            Instant.parse("2020-03-19T17:02:52Z"),
            Currency.BTC,
            Currency.USDT,
            TransactionType.BUY,
            new BigDecimal("0.041600"),
            new BigDecimal("6236.39"),
            new BigDecimal("0")
        );
        ParserTestUtils.checkEqual(txBeanParsed, txBeanCorrect);
    }

    @Test
    void testCorrectParsingRawTransactionBuyBaseFee() {
        final String row0 = "2020-03-19 17:02:52;BTCUSDT;BUY;0.0;0.041600;6236.39;0.041600;259.44;Filled\n";
        final String row1 = ";Date(UTC);Trading Price;Filled;Total;Fee;;;\n";
        final String row2 = ";2020-03-19 17:02:52;6236.39;0.041600;259.43382400;0.0001612653BTC;;;\n";

        final ImportedTransactionBean txBeanParsed
            = ParserTestUtils.getTransactionBean(HEADER_CORRECT + row0 + row1 + row2);
        final ImportedTransactionBean txBeanCorrect = new ImportedTransactionBean(
            null,
            Instant.parse("2020-03-19T17:02:52Z"),
            Currency.BTC,
            Currency.USDT,
            TransactionType.BUY,
            new BigDecimal("0.0414387347"),
            new BigDecimal("6260.6598844824"),
            new BigDecimal("0")
        );
        ParserTestUtils.checkEqual(txBeanParsed, txBeanCorrect);
    }

    @Test
    void testCorrectParsingRawTransactionBuyQuoteFee() {
        final String row0 = "2020-03-19 17:02:52;BTCUSDT;BUY;0.0;0.041600;6236.39;0.041600;259.44;Filled\n";
        final String row1 = ";Date(UTC);Trading Price;Filled;Total;Fee;;;\n";
        final String row2 = ";2020-03-19 17:02:52;6236.39;0.041600;259.43382400;0.1612653USDT;;;\n";

        final ImportedTransactionBean txBeanParsed
            = ParserTestUtils.getTransactionBean(HEADER_CORRECT + row0 + row1 + row2);
        final ImportedTransactionBean txBeanCorrect = new ImportedTransactionBean(
            null,
            Instant.parse("2020-03-19T17:02:52Z"),
            Currency.BTC,
            Currency.USDT,
            TransactionType.BUY,
            new BigDecimal("0.0416000000"),
            new BigDecimal("6240.2665697115"),
            new BigDecimal("0")
        );
        ParserTestUtils.checkEqual(txBeanParsed, txBeanCorrect);
    }

    @Test
    void testCorrectParsingRawTransactionSell() {
        final String row0 = "2020-03-19 17:02:52;BTCUSDT;SELL;0.0;0.041600;6236.39;0.041600;259.44;Filled\n";
        final String row1 = ";Date(UTC);Trading Price;Filled;Total;Fee;;;\n";
        final String row2 = ";2020-03-19 17:02:52;6236.39;0.041600;259.43382400;0.01612653BNB;;;\n";

        final ImportedTransactionBean txBeanParsed
            = ParserTestUtils.getTransactionBean(HEADER_CORRECT + row0 + row1 + row2);
        final ImportedTransactionBean txBeanCorrect = new ImportedTransactionBean(
            null,
            Instant.parse("2020-03-19T17:02:52Z"),
            Currency.BTC,
            Currency.USDT,
            TransactionType.SELL,
            new BigDecimal("0.041600"),
            new BigDecimal("6236.39"),
            new BigDecimal("0")
        );
        ParserTestUtils.checkEqual(txBeanParsed, txBeanCorrect);
    }

    @Test
    void testCorrectParsingRawTransactionSellBaseFee() {
        final String row0 = "2020-03-19 17:02:52;BTCUSDT;SELL;0.0;0.041600;6236.39;0.041600;259.44;Filled\n";
        final String row1 = ";Date(UTC);Trading Price;Filled;Total;Fee;;;\n";
        final String row2 = ";2020-03-19 17:02:52;6236.39;0.041600;259.43382400;0.001612653BTC;;;\n";

        final ImportedTransactionBean txBeanParsed
            = ParserTestUtils.getTransactionBean(HEADER_CORRECT + row0 + row1 + row2);
        final ImportedTransactionBean txBeanCorrect = new ImportedTransactionBean(
            null,
            Instant.parse("2020-03-19T17:02:52Z"),
            Currency.BTC,
            Currency.USDT,
            TransactionType.SELL,
            new BigDecimal("0.0432126530"),
            new BigDecimal("6003.6541612013"),
            new BigDecimal("0")
        );
        ParserTestUtils.checkEqual(txBeanParsed, txBeanCorrect);
    }

    @Test
    void testCorrectParsingRawTransactionSellQuoteFee() {
        final String row0 = "2020-03-19 17:02:52;BTCUSDT;SELL;0.0;0.041600;6236.39;0.041600;259.44;Filled\n";
        final String row1 = ";Date(UTC);Trading Price;Filled;Total;Fee;;;\n";
        final String row2 = ";2020-03-19 17:02:52;6236.39;0.041600;259.43382400;0.1612653USDT;;;\n";

        final ImportedTransactionBean txBeanParsed
            = ParserTestUtils.getTransactionBean(HEADER_CORRECT + row0 + row1 + row2);
        final ImportedTransactionBean txBeanCorrect = new ImportedTransactionBean(
            null,
            Instant.parse("2020-03-19T17:02:52Z"),
            Currency.BTC,
            Currency.USDT,
            TransactionType.SELL,
            new BigDecimal("0.0416000000"),
            new BigDecimal("6232.5134302885"),
            new BigDecimal("0")
        );
        ParserTestUtils.checkEqual(txBeanParsed, txBeanCorrect);
    }


    @Test
    void testUnknownPair() {
        final String row0 = "2020-03-19 17:02:52;USDTBTC;SELL;0.0;0.041600;6236.39;0.041600;259.44;Filled\n";
        final String row1 = ";Date(UTC);Trading Price;Filled;Total;Fee;;;\n";
        final String row2 = ";2020-03-19 17:02:52;6236.39;0.041600;259.43382400;0.1612653USDT;;;\n";

        final RowError rowError = ParserTestUtils.getRowError(HEADER_CORRECT + row0 + row1 + row2);
        assertNotNull(rowError);
        final String error = rowError.getMessage();
        assertTrue(error.contains(UNSUPPORTED_CURRENCY_PAIR.concat("USDTBTC")));
    }

    @Test
    void testIgnoredFee() {
        final String row0 = "2020-03-19 17:02:52;BTCUSDT;BUY;0.0;0.041600;6236.39;0.041600;259.44;Filled\n";
        final String row1 = ";Date(UTC);Trading Price;Filled;Total;Fee;;;\n";
        final String row2 = ";2020-03-19 17:02:52;6236.39;0.041600;259.43382400;0.1612653BNB;;;\n";

        final ConversionStatistic conversionStatistic =
            ParserTestUtils.getConversionStatistic(HEADER_CORRECT + row0 + row1 + row2);
        assertNotNull(conversionStatistic);
        assertEquals(1, conversionStatistic.getIgnoredFeeTransactionCount());
    }

    @Test
    void testIgnoredTransactionType() {
        final String row0 = "2020-03-19 17:02:52;BTCUSDT;SOLD;0.0;0.041600;6236.39;0.041600;259.44;Filled\n";
        final String row1 = ";Date(UTC);Trading Price;Filled;Total;Fee;;;\n";
        final String row2 = ";2020-03-19 17:02:52;6236.39;0.041600;259.43382400;0.01612653BNB;;;\n";

        final ConversionStatistic conversionStatistic =
            ParserTestUtils.getConversionStatistic(HEADER_CORRECT + row0 + row1 + row2);
        assertNotNull(conversionStatistic);
        assertEquals(1, conversionStatistic.getIgnoredRowsCount());
        assertTrue(
            conversionStatistic
                .getErrorRows()
                .get(0)
                .getMessage()
                .contains(ExchangeBean.UNSUPPORTED_TRANSACTION_TYPE.concat("SOLD"))
        );
    }

    @Test
    void testIgnoredStatusType() {
        final String row0 = "2020-03-19 17:02:52;BTCUSDT;SOLD;0.0;0.041600;6236.39;0.041600;259.44;Cancel\n";
        final String row1 = ";Date(UTC);Trading Price;Filled;Total;Fee;;;\n";
        final String row2 = ";2020-03-19 17:02:52;6236.39;0.041600;259.43382400;0.01612653BNB;;;\n";

        final ConversionStatistic conversionStatistic =
            ParserTestUtils.getConversionStatistic(HEADER_CORRECT + row0 + row1 + row2);
        assertNotNull(conversionStatistic);
        assertEquals(1, conversionStatistic.getIgnoredRowsCount());
        assertTrue(
            conversionStatistic
                .getErrorRows()
                .get(0)
                .getMessage()
                .contains(ExchangeBean.UNSUPPORTED_STATUS_TYPE.concat("Cancel"))
        );
    }
}