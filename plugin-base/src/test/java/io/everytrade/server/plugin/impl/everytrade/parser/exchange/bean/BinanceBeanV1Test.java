package io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean;

import io.everytrade.server.model.Currency;
import io.everytrade.server.model.TransactionType;
import io.everytrade.server.plugin.api.parser.BuySellImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.ConversionStatistic;
import io.everytrade.server.plugin.api.parser.FeeRebateImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.ImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.RowError;
import io.everytrade.server.plugin.api.parser.TransactionCluster;
import io.everytrade.server.plugin.impl.everytrade.parser.exception.ParsingProcessException;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.ExchangeBean;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.ExchangeBean.UNSUPPORTED_CURRENCY_PAIR;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class BinanceBeanV1Test {
    public static final String HEADER_CORRECT = "Date(UTC);Market;Type;Price;Amount;Total;Fee;Fee Coin\n";


    @Test
    void testWrongHeader() {
        String headerWrong = "Date(UTC);Market;Price;Amount;Total;Fee;Fee Coin\n";
        try {
            ParserTestUtils.testParsing(headerWrong);
            fail("No expected exception has been thrown.");
        } catch (ParsingProcessException e) {
        }
    }

    @Test
    void testCorrectParsingRawTransactionBuy() {
        final String row = "2020-02-04 16:19:07;LTCBTC;BUY;0.007393;1.61;0.01190273;0.00161;LTC\n";
        final TransactionCluster actual = ParserTestUtils.getTransactionCluster(HEADER_CORRECT + row);
        final TransactionCluster expected = new TransactionCluster(
            new BuySellImportedTransactionBean(
                null,
                Instant.parse("2020-02-04T16:19:07Z"),
                Currency.LTC,
                Currency.BTC,
                TransactionType.BUY,
                new BigDecimal("1.61"),
                new BigDecimal("0.0073930000")
            ),
            List.of(
                new FeeRebateImportedTransactionBean(
                    "-fee",
                    Instant.parse("2020-02-04T16:19:07Z"),
                    Currency.LTC,
                    Currency.BTC,
                    TransactionType.FEE,
                    new BigDecimal("0.00161"),
                    Currency.LTC
                )
            ),
            0
        );
        ParserTestUtils.checkEqual(expected, actual);
    }

    @Test
    void testCorrectParsingRawTransactionBuyDiffFeeCurrency() {
        final String row = "2020-02-04 16:19:07;LTCBTC;BUY;0.007393;1.61;0.01190273;0.00161;BTC\n";
        final TransactionCluster actual = ParserTestUtils.getTransactionCluster(HEADER_CORRECT + row);
        final TransactionCluster expected = new TransactionCluster(
            new BuySellImportedTransactionBean(
                null,
                Instant.parse("2020-02-04T16:19:07Z"),
                Currency.LTC,
                Currency.BTC,
                TransactionType.BUY,
                new BigDecimal("1.61"),
                new BigDecimal("0.0073930000")
            ),
            List.of(
                new FeeRebateImportedTransactionBean(
                    "-fee",
                    Instant.parse("2020-02-04T16:19:07Z"),
                    Currency.LTC,
                    Currency.BTC,
                    TransactionType.FEE,
                    new BigDecimal("0.00161"),
                    Currency.BTC
                )
            ),
            0
        );
        ParserTestUtils.checkEqual(expected, actual);
    }

    @Test
    void testCorrectParsingRawTransactionSell() {
        final String row = "2020-02-03 11:09:51;LTCBTC;SELL;0.007497;1.72;0.01289484;0.00001289;BTC\n";
        final TransactionCluster actual = ParserTestUtils.getTransactionCluster(HEADER_CORRECT + row);
        final TransactionCluster expected = new TransactionCluster(
            new BuySellImportedTransactionBean(
                null,
                Instant.parse("2020-02-03T11:09:51Z"),
                Currency.LTC,
                Currency.BTC,
                TransactionType.SELL,
                new BigDecimal("1.72"),
                new BigDecimal("0.0074970000")
            ),
            List.of(
                new FeeRebateImportedTransactionBean(
                    "-fee",
                    Instant.parse("2020-02-03T11:09:51Z"),
                    Currency.LTC,
                    Currency.BTC,
                    TransactionType.FEE,
                    new BigDecimal("0.00001289"),
                    Currency.BTC
                )
            ),
            0
        );
        ParserTestUtils.checkEqual(expected, actual);
    }

    @Test
    void testCorrectParsingRawTransactionSellDiffFeeCurrency() {
        final String row = "2020-02-03 11:09:51;LTCBTC;SELL;0.007497;1.72;0.01289484;0.00001289;LTC\n";
        final TransactionCluster actual = ParserTestUtils.getTransactionCluster(HEADER_CORRECT + row);
        final TransactionCluster expected = new TransactionCluster(
            new BuySellImportedTransactionBean(
                null,
                Instant.parse("2020-02-03T11:09:51Z"),
                Currency.LTC,
                Currency.BTC,
                TransactionType.SELL,
                new BigDecimal("1.72"),
                new BigDecimal("0.0074970000")
            ),
            List.of(
                new FeeRebateImportedTransactionBean(
                    "-fee",
                    Instant.parse("2020-02-03T11:09:51Z"),
                    Currency.LTC,
                    Currency.BTC,
                    TransactionType.FEE,
                    new BigDecimal("0.00001289"),
                    Currency.LTC
                )
            ),
            0
        );
        ParserTestUtils.checkEqual(expected, actual);
    }


    @Test
    void testUnknownPair() {
        final String row = "2020-02-03 11:09:51;BTCLTC;SELL;0.007497;1.72;0.01289484;0.00001289;LTC\n";
        final RowError rowError = ParserTestUtils.getRowError(HEADER_CORRECT + row);
        assertNotNull(rowError);
        final String error = rowError.getMessage();
        assertTrue(error.contains(UNSUPPORTED_CURRENCY_PAIR.concat("BTCLTC")));
    }

    @Test
    void testCorrectParsingRawTransactionBuyFeeSkipped() {
        final String row = "2020-02-04 16:19:07;LTCBTC;BUY;0.007393;1.61;0.01190273;0.00161;BNB\n";
        final TransactionCluster actual = ParserTestUtils.getTransactionCluster(HEADER_CORRECT + row);
        final TransactionCluster expected = new TransactionCluster(
            new BuySellImportedTransactionBean(
                null,
                Instant.parse("2020-02-04T16:19:07Z"),
                Currency.LTC,
                Currency.BTC,
                TransactionType.BUY,
                new BigDecimal("1.61"),
                new BigDecimal("0.0073930000")
            ),
           Collections.emptyList(),
            1
        );
        ParserTestUtils.checkEqual(expected, actual);
    }

    @Test
    void testIgnoredFee() {
        final String row = "2020-02-04 16:19:07;LTCBTC;BUY;0.007393;1.61;0.01190273;0.00161;BNB\n";
        final ConversionStatistic conversionStatistic = ParserTestUtils.getConversionStatistic(HEADER_CORRECT + row);
        assertNotNull(conversionStatistic);
        assertEquals(1, conversionStatistic.getIgnoredFeeTransactionCount());
    }

    @Test
    void testIgnoredTransactionType() {
        final String row = "2020-02-04 16:19:07;LTCBTC;DEPOSIT;0.007393;1.61;0.01190273;0.00161;LTC\n";
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