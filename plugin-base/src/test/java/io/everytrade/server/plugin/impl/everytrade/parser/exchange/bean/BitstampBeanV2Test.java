package io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean;

import io.everytrade.server.model.Currency;
import io.everytrade.server.model.TransactionType;
import io.everytrade.server.plugin.api.parser.FeeRebateImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.ImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.TransactionCluster;
import io.everytrade.server.plugin.impl.everytrade.parser.ParserUtils;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Data are synthetic (anonymized) but structurally identical to the real Bitstamp 2025 export layout:
 * separate currency columns, ISO-8601 Datetime, spaceless Subtype, Order ID.
 */
class BitstampBeanV2Test {
    private static final String HEADER_CORRECT = "ID,Account,Type,Subtype,Datetime,Amount,Amount currency,Value,"
        + "Value currency,Rate,Rate currency,Fee,Fee currency,Order ID\n";

    private static BigDecimal unitPrice(String value, String amount) {
        return new BigDecimal(value).divide(new BigDecimal(amount), ParserUtils.DECIMAL_DIGITS, RoundingMode.HALF_UP);
    }

    @Test
    void testBuyFiatQuote() {
        final String row = "1,Main Account,Market,Buy,2025-01-02T10:00:00Z,2.00000000,LTC,100.00,EUR,"
            + "50.000,EUR,0.50000,EUR,1001\n";
        final TransactionCluster actual = ParserTestUtils.getTransactionCluster(HEADER_CORRECT + row);
        final TransactionCluster expected = new TransactionCluster(
            new ImportedTransactionBean(
                null,
                Instant.parse("2025-01-02T10:00:00Z"),
                Currency.LTC,
                Currency.EUR,
                TransactionType.BUY,
                new BigDecimal("2.00000000"),
                unitPrice("100.00", "2.00000000")
            ),
            List.of(
                new FeeRebateImportedTransactionBean(
                    null,
                    Instant.parse("2025-01-02T10:00:00Z"),
                    Currency.EUR,
                    Currency.EUR,
                    TransactionType.FEE,
                    new BigDecimal("0.50000"),
                    Currency.EUR
                )
            )
        );
        ParserTestUtils.checkEqual(expected, actual);
    }

    @Test
    void testSellFiatQuote() {
        final String row = "2,Main Account,Market,Sell,2025-01-03T11:00:00Z,4.00000000,LTC,200.00,EUR,"
            + "50.000,EUR,1.00000,EUR,1002\n";
        final TransactionCluster actual = ParserTestUtils.getTransactionCluster(HEADER_CORRECT + row);
        final TransactionCluster expected = new TransactionCluster(
            new ImportedTransactionBean(
                null,
                Instant.parse("2025-01-03T11:00:00Z"),
                Currency.LTC,
                Currency.EUR,
                TransactionType.SELL,
                new BigDecimal("4.00000000"),
                unitPrice("200.00", "4.00000000")
            ),
            List.of(
                new FeeRebateImportedTransactionBean(
                    null,
                    Instant.parse("2025-01-03T11:00:00Z"),
                    Currency.EUR,
                    Currency.EUR,
                    TransactionType.FEE,
                    new BigDecimal("1.00000"),
                    Currency.EUR
                )
            )
        );
        ParserTestUtils.checkEqual(expected, actual);
    }

    @Test
    void testSellCryptoToCryptoQuote() {
        final String row = "3,Main Account,Market,Sell,2025-01-04T12:00:00Z,0.50000000,ETH,0.02000000,BTC,"
            + "0.04000000,BTC,0.00001000,BTC,1003\n";
        final TransactionCluster actual = ParserTestUtils.getTransactionCluster(HEADER_CORRECT + row);
        final TransactionCluster expected = new TransactionCluster(
            new ImportedTransactionBean(
                null,
                Instant.parse("2025-01-04T12:00:00Z"),
                Currency.ETH,
                Currency.BTC,
                TransactionType.SELL,
                new BigDecimal("0.50000000"),
                unitPrice("0.02000000", "0.50000000")
            ),
            List.of(
                new FeeRebateImportedTransactionBean(
                    null,
                    Instant.parse("2025-01-04T12:00:00Z"),
                    Currency.BTC,
                    Currency.BTC,
                    TransactionType.FEE,
                    new BigDecimal("0.00001000"),
                    Currency.BTC
                )
            )
        );
        ParserTestUtils.checkEqual(expected, actual);
    }

    @Test
    void testWithdrawalWithAssetFee() {
        final String row = "4,Main Account,Withdrawal,,2025-01-05T13:00:00Z,100.00000000,XLM,,,,,"
            + "0.00500000,XLM,\n";
        final TransactionCluster actual = ParserTestUtils.getTransactionCluster(HEADER_CORRECT + row);
        final ImportedTransactionBean main = actual.getMain();
        assertEquals(Currency.XLM, main.getBase());
        assertEquals(TransactionType.WITHDRAWAL, main.getAction());
        assertEquals(new BigDecimal("100.00000000"), main.getVolume());
        assertEquals(Instant.parse("2025-01-05T13:00:00Z"), main.getExecuted());
        assertEquals(1, actual.getRelated().size());
        final FeeRebateImportedTransactionBean fee = (FeeRebateImportedTransactionBean) actual.getRelated().get(0);
        assertEquals(Currency.XLM, fee.getFeeRebateCurrency());
        assertEquals(TransactionType.FEE, fee.getAction());
        assertEquals(new BigDecimal("0.00500000").setScale(ParserUtils.DECIMAL_DIGITS, RoundingMode.HALF_UP), fee.getVolume());
    }

    @Test
    void testDepositNoFee() {
        final String row = "5,Main Account,Deposit,,2025-01-06T14:00:00Z,1.00000000,LTC,,,,,,,\n";
        final TransactionCluster actual = ParserTestUtils.getTransactionCluster(HEADER_CORRECT + row);
        final ImportedTransactionBean main = actual.getMain();
        assertEquals(Currency.LTC, main.getBase());
        assertEquals(TransactionType.DEPOSIT, main.getAction());
        assertEquals(new BigDecimal("1.00000000"), main.getVolume());
        assertTrue(actual.getRelated().isEmpty());
    }

    @Test
    void testAllRowKindsParseWithoutProblems() {
        final String rows = HEADER_CORRECT
            + "1,Main Account,Market,Buy,2025-01-02T10:00:00Z,2.00000000,LTC,100.00,EUR,50.000,EUR,0.50000,EUR,1001\n"
            + "2,Main Account,Market,Sell,2025-01-03T11:00:00Z,4.00000000,LTC,200.00,EUR,50.000,EUR,1.00000,EUR,1002\n"
            + "3,Main Account,Market,Sell,2025-01-04T12:00:00Z,0.50000000,ETH,0.02000000,BTC,0.04000000,BTC,"
            + "0.00001000,BTC,1003\n"
            + "4,Main Account,Withdrawal,,2025-01-05T13:00:00Z,100.00000000,XLM,,,,,0.00500000,XLM,\n"
            + "5,Main Account,Deposit,,2025-01-06T14:00:00Z,1.00000000,LTC,,,,,,,\n";
        var result = ParserTestUtils.getParseResult(rows);
        assertTrue(result.getParsingProblems().isEmpty(), () -> "Unexpected parsing problems: " + result.getParsingProblems());
        assertEquals(5, result.getTransactionClusters().size());
    }
}
