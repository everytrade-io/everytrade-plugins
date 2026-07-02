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

class BybitEuBeanV1Test {

    private static final String HEADER_CORRECT = "Spot Pairs,Order Type,Direction,feeCoin,ExecFeeV2,Filled Value,"
        + "Filled Price,Filled Quantity,Fees,Transaction ID,Order No.,Timestamp (UTC)\n";

    @Test
    void testSellWithQuoteFee() {
        final String row = "BTCEUR,MARKET,SELL,EUR,0.0040382175,1.61528700000000000000,53842.90000000000000000000,"
            + "0.00003000000000000000,--,3010000000039612078, 04058112,08:22 2026-06-05\n";
        final TransactionCluster actual = ParserTestUtils.getTransactionCluster(HEADER_CORRECT + row);
        final TransactionCluster expected = new TransactionCluster(
            new ImportedTransactionBean(
                "3010000000039612078",
                Instant.parse("2026-06-05T08:22:00Z"),
                BTC,
                EUR,
                SELL,
                new BigDecimal("0.00003000000000000000"),
                new BigDecimal("53842.90000000000000000000")
            ),
            List.of(
                new FeeRebateImportedTransactionBean(
                    "3010000000039612078-fee",
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
    void testBuyWithBaseFee() {
        final String row = "BTCEUR,MARKET,BUY,BTC,0.000000175,3.77115200000000000000,53873.60000000000000000000,"
            + "0.00007000000000000000,--,3010000000039611932, 28338176,08:22 2026-06-05\n";
        final TransactionCluster actual = ParserTestUtils.getTransactionCluster(HEADER_CORRECT + row);
        final TransactionCluster expected = new TransactionCluster(
            new ImportedTransactionBean(
                "3010000000039611932",
                Instant.parse("2026-06-05T08:22:00Z"),
                BTC,
                EUR,
                BUY,
                new BigDecimal("0.00007000000000000000"),
                new BigDecimal("53873.60000000000000000000")
            ),
            List.of(
                new FeeRebateImportedTransactionBean(
                    "3010000000039611932-fee",
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
}
