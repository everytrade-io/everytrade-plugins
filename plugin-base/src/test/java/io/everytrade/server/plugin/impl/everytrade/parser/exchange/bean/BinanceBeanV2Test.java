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

class BinanceBeanV2Test {
    public static final String HEADER_CORRECT = "Date(UTC);Pair;Type;Order Price;Order Amount;AvgTrading Price;Filled;Total;status\n";

    @Test
    void testWrongHeader() {
        final String headerWrong = "Date(UTC);Pair;Type;OrdeX Price;Order Amount;AvgTrading Price;Filled;Total;status\n";
        try {
            ParserTestUtils.testParsing(headerWrong);
            fail("No expected exception has been thrown.");
        } catch (ParsingProcessException ignored) {
        }
    }

    @Test
    void testCorrectParsingRawTransactionBuy() {
        final String row0 = "2020-03-19 17:02:52;BTCUSDT;BUY;0.0;0.041600;6236.39;0.041600;259.44;Filled\n";
        final String row1 = ";Date(UTC);Trading Price;Filled;Total;Fee;;;\n";
        final String row2 = ";2020-03-19 17:02:52;6236.39;0.041600;259.43382400;0.01612653BNB;;;\n";

        final TransactionCluster actual
            = ParserTestUtils.getTransactionCluster(HEADER_CORRECT + row0 + row1 + row2);
        final TransactionCluster expected = new TransactionCluster(
            new ImportedTransactionBean(
                null,
                Instant.parse("2020-03-19T17:02:52Z"),
                Currency.BTC,
                Currency.USDT,
                TransactionType.BUY,
                new BigDecimal("0.041600"),
                new BigDecimal("6236.39")
            ),
            List.of(
                new FeeRebateImportedTransactionBean(
                    null,
                    Instant.parse("2020-03-19T17:02:52Z"),
                    Currency.BNB,
                    Currency.BNB,
                    TransactionType.FEE,
                    new BigDecimal("0.01612653"),
                    Currency.BNB
                )
            )
        );
        ParserTestUtils.checkEqual(expected, actual);
    }

    @Test
    void testCorrectParsingRawTransactionBuyBaseFee() {
        final String row0 = "2020-03-19 17:02:52;BTCUSDT;BUY;0.0;0.041600;6236.39;0.041600;259.44;Filled\n";
        final String row1 = ";Date(UTC);Trading Price;Filled;Total;Fee;;;\n";
        final String row2 = ";2020-03-19 17:02:52;6236.39;0.041600;259.43382400;0.0001612653BTC;;;\n";

        final TransactionCluster actual
            = ParserTestUtils.getTransactionCluster(HEADER_CORRECT + row0 + row1 + row2);
        final TransactionCluster expected = new TransactionCluster(
            new ImportedTransactionBean(
                null,
                Instant.parse("2020-03-19T17:02:52Z"),
                Currency.BTC,
                Currency.USDT,
                TransactionType.BUY,
                new BigDecimal("0.041600"),
                new BigDecimal("6236.39")
            ),
            List.of(
                new FeeRebateImportedTransactionBean(
                    null,
                    Instant.parse("2020-03-19T17:02:52Z"),
                    Currency.BTC,
                    Currency.BTC,
                    TransactionType.FEE,
                    new BigDecimal("0.0001612653"),
                    Currency.BTC
                )
            )
        );
        ParserTestUtils.checkEqual(expected, actual);
    }

    @Test
    void testCorrectParsingRawTransactionBuyQuoteFee() {
        final String row0 = "2020-03-19 17:02:52;BTCUSDT;BUY;0.0;0.041600;6236.39;0.041600;259.44;Filled\n";
        final String row1 = ";Date(UTC);Trading Price;Filled;Total;Fee;;;\n";
        final String row2 = ";2020-03-19 17:02:52;6236.39;0.041600;259.43382400;0.1612653USDT;;;\n";

        final TransactionCluster actual
            = ParserTestUtils.getTransactionCluster(HEADER_CORRECT + row0 + row1 + row2);
        final TransactionCluster expected = new TransactionCluster(
            new ImportedTransactionBean(
                null,
                Instant.parse("2020-03-19T17:02:52Z"),
                Currency.BTC,
                Currency.USDT,
                TransactionType.BUY,
                new BigDecimal("0.041600"),
                new BigDecimal("6236.39")
            ),
            List.of(
                new FeeRebateImportedTransactionBean(
                    null,
                    Instant.parse("2020-03-19T17:02:52Z"),
                    Currency.USDT,
                    Currency.USDT,
                    TransactionType.FEE,
                    new BigDecimal("0.1612653"),
                    Currency.USDT
                )
            )
        );
        ParserTestUtils.checkEqual(expected, actual);
    }

    @Test
    void testCorrectParsingRawTransactionSell() {
        final String row0 = "2020-03-19 17:02:52;BTCUSDT;SELL;0.0;0.041600;6236.39;0.041600;259.44;Filled\n";
        final String row1 = ";Date(UTC);Trading Price;Filled;Total;Fee;;;\n";
        final String row2 = ";2020-03-19 17:02:52;6236.39;0.041600;259.43382400;0.01612653BNB;;;\n";

        final TransactionCluster actual
            = ParserTestUtils.getTransactionCluster(HEADER_CORRECT + row0 + row1 + row2);
        final TransactionCluster expected = new TransactionCluster(
            new ImportedTransactionBean(
                null,
                Instant.parse("2020-03-19T17:02:52Z"),
                Currency.BTC,
                Currency.USDT,
                TransactionType.SELL,
                new BigDecimal("0.041600"),
                new BigDecimal("6236.39")
            ),
            List.of(
                new FeeRebateImportedTransactionBean(
                    null,
                    Instant.parse("2020-03-19T17:02:52Z"),
                    Currency.BNB,
                    Currency.BNB,
                    TransactionType.FEE,
                    new BigDecimal("0.01612653"),
                    Currency.BNB
                )
            ));
        ParserTestUtils.checkEqual(expected, actual);
    }

    @Test
    void testCorrectParsingRawTransactionSellType2() {
        var header = "Date(UTC);orderId;clientOrderId;Pair;Type;Order Price;Order Amount;AvgTrading Price;Filled;Total;status;Strategy " +
            "Id;Strategy Type\n";
        var row0 = "1.11.2021 6:01;8,38977E+18;web_T8XLoZ0jvfoUUMECz9pT;ETHUSDT;SELL;0.00000000;0.07100000;4225.14000;0.07100000;299" +
            ".98494000;FILLED;-;\n";
        var row1 = ";Date(UTC);Trading Price;Filled;Total;Fee;;;;;;;\n";
        var row2 = ";1.11.2021 6:01;4225.14000000;0.07100000;299.9849400000000000;0.11999397USDT;;;;;;;\n";

        final TransactionCluster actual
            = ParserTestUtils.getTransactionCluster(header + row0 + row1 + row2);
        final TransactionCluster expected = new TransactionCluster(
            new ImportedTransactionBean(
                null,
                Instant.parse("2021-11-01T06:01:00Z"),
                Currency.ETH,
                Currency.USDT,
                TransactionType.SELL,
                new BigDecimal("0.071"),
                new BigDecimal("4225.14")
            ),
            List.of(
                new FeeRebateImportedTransactionBean(
                    null,
                    Instant.parse("2021-11-01T06:01:00Z"),
                    Currency.USDT,
                    Currency.USDT,
                    TransactionType.FEE,
                    new BigDecimal("0.11999397"),
                    Currency.USDT
                )
            )
        );
        ParserTestUtils.checkEqual(expected, actual);
    }

    @Test
    void testCorrectParsingRawTransactionSellBaseFee() {
        final String row0 = "2020-03-19 17:02:52;BTCUSDT;SELL;0.0;0.041600;6236.39;0.041600;259.44;Filled\n";
        final String row1 = ";Date(UTC);Trading Price;Filled;Total;Fee;;;\n";
        final String row2 = ";2020-03-19 17:02:52;6236.39;0.041600;259.43382400;0.001612653BTC;;;\n";
        final TransactionCluster actual
            = ParserTestUtils.getTransactionCluster(HEADER_CORRECT + row0 + row1 + row2);
        final TransactionCluster expected = new TransactionCluster(
            new ImportedTransactionBean(
                null,
                Instant.parse("2020-03-19T17:02:52Z"),
                Currency.BTC,
                Currency.USDT,
                TransactionType.SELL,
                new BigDecimal("0.041600"),
                new BigDecimal("6236.39")
            ),
            List.of(
                new FeeRebateImportedTransactionBean(
                    null,
                    Instant.parse("2020-03-19T17:02:52Z"),
                    Currency.BTC,
                    Currency.BTC,
                    TransactionType.FEE,
                    new BigDecimal("0.001612653"),
                    Currency.BTC
                )
            )
        );
        ParserTestUtils.checkEqual(expected, actual);
    }

    @Test
    void testCorrectParsingRawTransactionSellQuoteFee() {
        final String row0 = "2020-03-19 17:02:52;BTCUSDT;SELL;0.0;0.041600;6236.39;0.041600;259.44;Filled\n";
        final String row1 = ";Date(UTC);Trading Price;Filled;Total;Fee;;;\n";
        final String row2 = ";2020-03-19 17:02:52;6236.39;0.041600;259.43382400;0.1612653USDT;;;\n";

        final TransactionCluster actual
            = ParserTestUtils.getTransactionCluster(HEADER_CORRECT + row0 + row1 + row2);
        final TransactionCluster expected = new TransactionCluster(
            new ImportedTransactionBean(
                null,
                Instant.parse("2020-03-19T17:02:52Z"),
                Currency.BTC,
                Currency.USDT,
                TransactionType.SELL,
                new BigDecimal("0.041600"),
                new BigDecimal("6236.39")
            ),
            List.of(
                new FeeRebateImportedTransactionBean(
                    null,
                    Instant.parse("2020-03-19T17:02:52Z"),
                    Currency.USDT,
                    Currency.USDT,
                    TransactionType.FEE,
                    new BigDecimal("0.1612653"),
                    Currency.USDT
                )
            )
        );
        ParserTestUtils.checkEqual(expected, actual);
    }

    @Test
    void testIgnoredTransactionType() {
        final String row0 = "2020-03-19 17:02:52;BTCUSDT;SOLD;0.0;0.041600;6236.39;0.041600;259.44;Filled\n";
        final String row1 = ";Date(UTC);Trading Price;Filled;Total;Fee;;;\n";
        final String row2 = ";2020-03-19 17:02:52;6236.39;0.041600;259.43382400;0.01612653BNB;;;\n";

        final ParsingProblem parsingProblem =
            ParserTestUtils.getParsingProblem(HEADER_CORRECT + row0 + row1 + row2);
        assertTrue(parsingProblem.getMessage().contains(ExchangeBean.UNSUPPORTED_TRANSACTION_TYPE.concat("SOLD")));
    }

    @Test
    void testIgnoredStatusType() {
        final String row0 = "2020-03-19 17:02:52;BTCUSDT;SOLD;0.0;0.041600;6236.39;0.041600;259.44;Cancel\n";
        final String row1 = ";Date(UTC);Trading Price;Filled;Total;Fee;;;\n";
        final String row2 = ";2020-03-19 17:02:52;6236.39;0.041600;259.43382400;0.01612653BNB;;;\n";

        final ParsingProblem parsingProblem
            = ParserTestUtils.getParsingProblem(HEADER_CORRECT + row0 + row1 + row2);
        assertTrue(parsingProblem.getMessage().contains(ExchangeBean.UNSUPPORTED_STATUS_TYPE.concat("Cancel")));
    }
}