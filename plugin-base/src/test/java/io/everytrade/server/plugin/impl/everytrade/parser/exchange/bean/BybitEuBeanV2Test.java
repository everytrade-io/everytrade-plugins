package io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean;

import io.everytrade.server.plugin.api.parser.FeeRebateImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.ImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.TransactionCluster;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static io.everytrade.server.model.Currency.BTC;
import static io.everytrade.server.model.Currency.EUR;
import static io.everytrade.server.model.TransactionType.BUY;
import static io.everytrade.server.model.TransactionType.FEE;
import static io.everytrade.server.model.TransactionType.SELL;
import static io.everytrade.server.plugin.impl.everytrade.parser.ParserUtils.DECIMAL_DIGITS;
import static java.math.RoundingMode.HALF_UP;

class BybitEuBeanV2Test {

    private static final String HEADER_CORRECT = "Spot Pairs,feeCoin,ExecFeeV2,feeInfo,Order Type,Direction,"
        + "Filled Value,Avg. Filled Price,Order Price,Order Quantity,Order Value,Order Status,Order No.,Timestamp (UTC)\n";

    @Test
    void testSellWithOrderQuantity() {
        final String row = "BTCEUR,EUR,0.0040382175,\"{\"\"EUR\"\":\"\"0.0040382175\"\"}\",MARKET,SELL,1.615287,53842.9,"
            + "MARKET,0.00003000000000000000,--,FILLED, 04058112,08:22 2026-06-05\n";
        final TransactionCluster actual = ParserTestUtils.getTransactionCluster(HEADER_CORRECT + row);
        final TransactionCluster expected = new TransactionCluster(
            new ImportedTransactionBean(
                "04058112",
                Instant.parse("2026-06-05T08:22:00Z"),
                BTC,
                EUR,
                SELL,
                new BigDecimal("0.00003000000000000000"),
                new BigDecimal("53842.9")
            ),
            List.of(
                new FeeRebateImportedTransactionBean(
                    "04058112-fee",
                    Instant.parse("2026-06-05T08:22:00Z"),
                    EUR,
                    EUR,
                    FEE,
                    new BigDecimal("0.0040382175").setScale(DECIMAL_DIGITS, HALF_UP),
                    EUR
                )
            )
        );
        ParserTestUtils.checkEqual(expected, actual);
    }

    @Test
    void testMarketBuyDerivesQuantityFromFilledValue() {
        // market BUY is specified by quote value => Order Quantity is "--", base quantity = Filled Value / Avg. Filled Price
        final String row = "BTCEUR,BTC,0.000000175,\"{\"\"BTC\"\":\"\"0.000000175\"\"}\",MARKET,BUY,3.771152,53873.6,"
            + "MARKET,--,4.00000000000000000000,FILLED, 28338176,08:22 2026-06-05\n";
        final TransactionCluster actual = ParserTestUtils.getTransactionCluster(HEADER_CORRECT + row);
        final TransactionCluster expected = new TransactionCluster(
            new ImportedTransactionBean(
                "28338176",
                Instant.parse("2026-06-05T08:22:00Z"),
                BTC,
                EUR,
                BUY,
                new BigDecimal("0.00007").setScale(DECIMAL_DIGITS, HALF_UP),
                new BigDecimal("53873.6")
            ),
            List.of(
                new FeeRebateImportedTransactionBean(
                    "28338176-fee",
                    Instant.parse("2026-06-05T08:22:00Z"),
                    BTC,
                    BTC,
                    FEE,
                    new BigDecimal("0.000000175").setScale(DECIMAL_DIGITS, HALF_UP),
                    BTC
                )
            )
        );
        ParserTestUtils.checkEqual(expected, actual);
    }

    @Test
    void testPartiallyFilledCancelledImportsExecutedPart() {
        // ordered 0.0001 BTC, but only 0.00003 BTC executed before cancellation - the EXECUTED quantity
        // (Filled Value / Avg. Filled Price) must be imported, never the ordered "Order Quantity"
        final String row = "BTCEUR,EUR,0.0040382175,\"{\"\"EUR\"\":\"\"0.0040382175\"\"}\",LIMIT,SELL,1.615287,53842.9,"
            + "53842.9,0.00010000000000000000,--,PartiallyFilledCanceled, 04058113,08:22 2026-06-05\n";
        final TransactionCluster actual = ParserTestUtils.getTransactionCluster(HEADER_CORRECT + row);
        final TransactionCluster expected = new TransactionCluster(
            new ImportedTransactionBean(
                "04058113",
                Instant.parse("2026-06-05T08:22:00Z"),
                BTC,
                EUR,
                SELL,
                new BigDecimal("0.00003").setScale(DECIMAL_DIGITS, HALF_UP),
                new BigDecimal("53842.9")
            ),
            List.of(
                new FeeRebateImportedTransactionBean(
                    "04058113-fee",
                    Instant.parse("2026-06-05T08:22:00Z"),
                    EUR,
                    EUR,
                    FEE,
                    new BigDecimal("0.0040382175").setScale(DECIMAL_DIGITS, HALF_UP),
                    EUR
                )
            )
        );
        ParserTestUtils.checkEqual(expected, actual);
    }

    @Test
    void testNotFilledOrderIsIgnored() {
        // the CANCELLED row comes first — if it was (incorrectly) imported, getTransactionCluster would return it
        // and the uid assertion below would fail; only the FILLED row must be parsed
        final String cancelledRow = "BTCEUR,EUR,--,--,LIMIT,SELL,--,--,55000,0.00003000000000000000,--,CANCELLED,"
            + " 99999999,08:22 2026-06-05\n";
        final String filledRow = "BTCEUR,EUR,0.0040382175,\"{\"\"EUR\"\":\"\"0.0040382175\"\"}\",MARKET,SELL,1.615287,"
            + "53842.9,MARKET,0.00003000000000000000,--,FILLED, 04058112,08:22 2026-06-05\n";
        final TransactionCluster actual = ParserTestUtils.getTransactionCluster(HEADER_CORRECT + cancelledRow + filledRow);
        final TransactionCluster expected = new TransactionCluster(
            new ImportedTransactionBean(
                "04058112",
                Instant.parse("2026-06-05T08:22:00Z"),
                BTC,
                EUR,
                SELL,
                new BigDecimal("0.00003000000000000000"),
                new BigDecimal("53842.9")
            ),
            List.of(
                new FeeRebateImportedTransactionBean(
                    "04058112-fee",
                    Instant.parse("2026-06-05T08:22:00Z"),
                    EUR,
                    EUR,
                    FEE,
                    new BigDecimal("0.0040382175").setScale(DECIMAL_DIGITS, HALF_UP),
                    EUR
                )
            )
        );
        ParserTestUtils.checkEqual(expected, actual);
    }
}
