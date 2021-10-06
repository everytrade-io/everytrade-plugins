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

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class EveryTradeBeanV3Test {
    private static final String HEADER_CORRECT = "UID;DATE;SYMBOL;ACTION;QUANTY;PRICE;FEE;FEE_CURRENCY;REBATE;REBATE_CURRENCY\n";

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
        final String headerWrong = "UID;DATE;SYMBOL;ACTION;QUANTY;PRICE;XFEE;FEE_CURRENCY;REBATE;REBATE_CURRENCY\n";
        try {
            ParserTestUtils.testParsing(headerWrong);
            fail("No expected exception has been thrown.");
        } catch (ParsingProcessException ignored) {
        }
    }

    @Test
    void testCorrectParsingRawTransactionBuy() {
        final String row = "1;27.7.2021 14:43:18;BTC/CZK;BUY;0.066506;210507.3226;;;;\n";
        final TransactionCluster actual = ParserTestUtils.getTransactionCluster(HEADER_CORRECT + row);
        final TransactionCluster expected = new TransactionCluster(
            new BuySellImportedTransactionBean(
                "1",
                Instant.parse("2021-07-27T14:43:18Z"),
                Currency.BTC,
                Currency.CZK,
                TransactionType.BUY,
                new BigDecimal("0.066506"),
                new BigDecimal("210507.3226")
            ),
            List.of()
        );
        ParserTestUtils.checkEqual(expected, actual);
    }

    @Test
    void testCorrectParsingRawTransactionBuyWithFeeQuote() {
        final String row = "1;27.7.2021 14:43:18;BTC/CZK;BUY;0.066506;210507.3226;140;CZK;;\n";
        final TransactionCluster actual = ParserTestUtils.getTransactionCluster(HEADER_CORRECT + row);
        final TransactionCluster expected = new TransactionCluster(
            new BuySellImportedTransactionBean(
                "1",
                Instant.parse("2021-07-27T14:43:18Z"),
                Currency.BTC,
                Currency.CZK,
                TransactionType.BUY,
                new BigDecimal("0.066506"),
                new BigDecimal("210507.3226")
            ),
            List.of(
                new FeeRebateImportedTransactionBean(
                    "1-fee",
                    Instant.parse("2021-07-27T14:43:18Z"),
                    Currency.BTC,
                    Currency.CZK,
                    TransactionType.FEE,
                    new BigDecimal("140"),
                    Currency.CZK
                )
            )
        );
        ParserTestUtils.checkEqual(expected, actual);
    }

    @Test
    void testCorrectParsingRawTransactionBuyWithFeeBase() {
        final String row = "1;27.7.2021 14:43:18;BTC/CZK;BUY;0.066506;210507.3226;0.001;BTC;;\n";
        final TransactionCluster actual = ParserTestUtils.getTransactionCluster(HEADER_CORRECT + row);
        final TransactionCluster expected = new TransactionCluster(
            new BuySellImportedTransactionBean(
                "1",
                Instant.parse("2021-07-27T14:43:18Z"),
                Currency.BTC,
                Currency.CZK,
                TransactionType.BUY,
                new BigDecimal("0.066506"),
                new BigDecimal("210507.3226")
            ),
            List.of(
                new FeeRebateImportedTransactionBean(
                    "1-fee",
                    Instant.parse("2021-07-27T14:43:18Z"),
                    Currency.BTC,
                    Currency.CZK,
                    TransactionType.FEE,
                    new BigDecimal("0.001"),
                    Currency.BTC
                )
            )
        );
        ParserTestUtils.checkEqual(expected, actual);
    }

    @Test
    void testCorrectParsingRawTransactionSell() {
        final String row = "1;27.7.2021 14:59:21;BTC/EUR;SELL;0.066306;8736.534094;;;;\n";
        final TransactionCluster actual = ParserTestUtils.getTransactionCluster(HEADER_CORRECT + row);
        final TransactionCluster expected = new TransactionCluster(
            new BuySellImportedTransactionBean(
                "1",
                Instant.parse("2021-07-27T14:59:21Z"),
                Currency.BTC,
                Currency.EUR,
                TransactionType.SELL,
                new BigDecimal("0.066306"),
                new BigDecimal("8736.534094")
            ),
            List.of()
        );
        ParserTestUtils.checkEqual(expected, actual);
    }

    @Test
    void testCorrectParsingRawTransactionSellWithRebateQuote() {
        final String row = "1;27.7.2021 14:59:21;BTC/EUR;SELL;0.066306;8736.534094;;;5.7;EUR\n";
        final TransactionCluster actual = ParserTestUtils.getTransactionCluster(HEADER_CORRECT + row);
        final TransactionCluster expected = new TransactionCluster(
            new BuySellImportedTransactionBean(
                "1",
                Instant.parse("2021-07-27T14:59:21Z"),
                Currency.BTC,
                Currency.EUR,
                TransactionType.SELL,
                new BigDecimal("0.066306"),
                new BigDecimal("8736.534094")
            ),
            List.of(new FeeRebateImportedTransactionBean(
                "1-fee",
                Instant.parse("2021-07-27T14:59:21Z"),
                Currency.BTC,
                Currency.EUR,
                TransactionType.REBATE,
                new BigDecimal("5.7"),
                Currency.EUR
            ))
        );
        ParserTestUtils.checkEqual(expected, actual);
    }

    @Test
    void testCorrectParsingRawTransactionSellWithRebateBase() {
        final String row = "1;27.7.2021 14:59:21;BTC/EUR;SELL;0.066306;8736.534094;;;0.001;BTC\n";
        final TransactionCluster actual = ParserTestUtils.getTransactionCluster(HEADER_CORRECT + row);
        final TransactionCluster expected = new TransactionCluster(
            new BuySellImportedTransactionBean(
                "1",
                Instant.parse("2021-07-27T14:59:21Z"),
                Currency.BTC,
                Currency.EUR,
                TransactionType.SELL,
                new BigDecimal("0.066306"),
                new BigDecimal("8736.534094")
            ),
            List.of(new FeeRebateImportedTransactionBean(
                "1-fee",
                Instant.parse("2021-07-27T14:59:21Z"),
                Currency.BTC,
                Currency.EUR,
                TransactionType.REBATE,
                new BigDecimal("0.001"),
                Currency.BTC
            ))
        );
        ParserTestUtils.checkEqual(expected, actual);
    }

    @Test
    void testCorrectParsingRawTransactionFeeQuoteUnrelated() {
        final String row = "1;27.7.2021 14:59:21;BTC/CZK;FEE;;;100;CZK;;\n";
        final TransactionCluster actual = ParserTestUtils.getTransactionCluster(HEADER_CORRECT + row);
        final TransactionCluster expected = new TransactionCluster(
            new FeeRebateImportedTransactionBean(
                "1",
                Instant.parse("2021-07-27T14:59:21Z"),
                Currency.BTC,
                Currency.CZK,
                TransactionType.FEE,
                new BigDecimal("100"),
                Currency.CZK
            ),
            List.of()
        );
        ParserTestUtils.checkEqualUnrelated(expected, actual);
    }

    @Test
    void testCorrectParsingRawTransactionFeeBaseUnrelated() {
        final String row = "1;27.7.2021 14:59:21;BTC/CZK;FEE;;;0.01;BTC;;\n";
        final TransactionCluster actual = ParserTestUtils.getTransactionCluster(HEADER_CORRECT + row);
        final TransactionCluster expected = new TransactionCluster(
            new FeeRebateImportedTransactionBean(
                "1",
                Instant.parse("2021-07-27T14:59:21Z"),
                Currency.BTC,
                Currency.CZK,
                TransactionType.FEE,
                new BigDecimal("0.01"),
                Currency.BTC
            ),
            List.of()
        );
        ParserTestUtils.checkEqualUnrelated(expected, actual);
    }

    @Test
    void testCorrectParsingRawTransactionRebateQuoteUnrelated() {
        final String row = "1;27.7.2021 14:59:21;BTC/CZK;REBATE;;;;;100;CZK\n";
        final TransactionCluster actual = ParserTestUtils.getTransactionCluster(HEADER_CORRECT + row);
        final TransactionCluster expected = new TransactionCluster(
            new FeeRebateImportedTransactionBean(
                "1",
                Instant.parse("2021-07-27T14:59:21Z"),
                Currency.BTC,
                Currency.CZK,
                TransactionType.REBATE,
                new BigDecimal("100"),
                Currency.CZK
            ),
            List.of()
        );
        ParserTestUtils.checkEqualUnrelated(expected, actual);
    }

    @Test
    void testCorrectParsingRawTransactionRebateBaseUnrelated() {
        final String row = "1;27.7.2021 14:59:21;BTC/CZK;REBATE;;;;;0.01;BTC\n";
        final TransactionCluster actual = ParserTestUtils.getTransactionCluster(HEADER_CORRECT + row);
        final TransactionCluster expected = new TransactionCluster(
            new FeeRebateImportedTransactionBean(
                "1",
                Instant.parse("2021-07-27T14:59:21Z"),
                Currency.BTC,
                Currency.CZK,
                TransactionType.REBATE,
                new BigDecimal("0.01"),
                Currency.BTC
            ),
            List.of()
        );
        ParserTestUtils.checkEqualUnrelated(expected, actual);
    }

    @Test
    void testCorrectParsingRawTransactionBuyDiffDateFormat() {
        final String row = "1;2021-07-27 14:43:18;BTC/CZK;BUY;0.066506;210507.3226;;;;\n";
        final TransactionCluster actual = ParserTestUtils.getTransactionCluster(HEADER_CORRECT + row);
        final TransactionCluster expected = new TransactionCluster(
            new BuySellImportedTransactionBean(
                "1",
                Instant.parse("2021-07-27T14:43:18Z"),
                Currency.BTC,
                Currency.CZK,
                TransactionType.BUY,
                new BigDecimal("0.066506"),
                new BigDecimal("210507.3226")
            ),
            List.of()
        );
        ParserTestUtils.checkEqual(expected, actual);
    }

    @Test
    void testUnknownPair() {
        final String row = "1;2021-07-27 14:43:18;XXX/CZK;BUY;0.066506;210507.3226;;;;\n";
        final ParsingProblem parsingProblem = ParserTestUtils.getParsingProblem(HEADER_CORRECT + row);
        final String error = parsingProblem.getMessage();
        assertTrue(error.contains("XXX/CZK"));
    }

    @Test
    void testIgnoredTransactionType() {
        final String row = "1;2021-07-27 14:43:18;BTC/CZK;WITHDRAW;0.066506;210507.3226;;;;\n";
        final ParsingProblem parsingProblem = ParserTestUtils.getParsingProblem(HEADER_CORRECT + row);
        assertTrue(parsingProblem.getMessage().contains(ExchangeBean.UNSUPPORTED_TRANSACTION_TYPE.concat("WITHDRAW"))
        );
    }

    @Test
    void testDiffFeeCurrency() {
        final String row = "1;27.7.2021 14:43:18;BTC/CZK;BUY;0.066506;210507.3226;140;EUR;;\n";
        final ParsingProblem parsingProblem = ParserTestUtils.getParsingProblem(HEADER_CORRECT + row);
        assertTrue(parsingProblem.getMessage().contains("Fee currency 'EUR' differs")
        );
    }

    @Test
    void testDiffRebateCurrency() {
        final String row = "1;27.7.2021 14:59:21;BTC/EUR;SELL;0.066306;8736.534094;;;0.001;LTC\n";
        final ParsingProblem parsingProblem = ParserTestUtils.getParsingProblem(HEADER_CORRECT + row);
        assertTrue(parsingProblem.getMessage().contains("Rebate currency 'LTC' differs")
        );
    }
}