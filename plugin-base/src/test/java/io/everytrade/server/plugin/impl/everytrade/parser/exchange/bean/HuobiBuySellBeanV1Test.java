package io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean;

import io.everytrade.server.plugin.api.parser.FeeRebateImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.ImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.TransactionCluster;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static io.everytrade.server.model.Currency.DYDX;
import static io.everytrade.server.model.Currency.SOL;
import static io.everytrade.server.model.Currency.USDT;
import static io.everytrade.server.model.TransactionType.BUY;
import static io.everytrade.server.model.TransactionType.FEE;
import static io.everytrade.server.model.TransactionType.SELL;

public class HuobiBuySellBeanV1Test {

    private static final String HEADER_CORRECT = "uid,symbol,deal_type,order_type,account_type,price,volume,amount,fee_amount," +
        "fee_currency,fee_point_currency,fee_point_volume,deal_time\n";

    @Test
    void testSell() {
        final String row = "449406209,SOL/USDT,sell,web,spot,24.140000000000000000,62.720000000000000000,1514.060800000000000000," +
            "3.028121600000000000,usdt,,,2023-10-03 14:29:46\n";
        final TransactionCluster actual = ParserTestUtils.getTransactionCluster(HEADER_CORRECT + row);
        final TransactionCluster expected = new TransactionCluster(
            new ImportedTransactionBean(
                "449406209",
                Instant.parse("2023-10-03T14:29:46Z"),
                SOL,
                USDT,
                SELL,
                new BigDecimal("62.720000000000000000"),
                new BigDecimal("24.140000000000000000")
            ),
            List.of(
                new FeeRebateImportedTransactionBean(
                    "449406209-fee",
                    Instant.parse("2023-10-03T14:29:46Z"),
                    USDT,
                    USDT,
                    FEE,
                    new BigDecimal("3.02812160000000000"),
                    USDT
                )
            )
        );
        ParserTestUtils.checkEqual(expected, actual);
    }

    @Test
    void testBuy() {
        final String row = "449406209,DYDX/USDT,buy,web,spot,2.056600000000000000,550.327200000000000000,1131.802919520000000000,1" +
            ".100654400000000000,dydx,,,2023-10-03 14:30:28\n";
        final TransactionCluster actual = ParserTestUtils.getTransactionCluster(HEADER_CORRECT + row);
        final TransactionCluster expected = new TransactionCluster(
            new ImportedTransactionBean(
                "449406209",
                Instant.parse("2023-10-03T14:30:28Z"),
                DYDX,
                USDT,
                BUY,
                new BigDecimal("550.327200000000000000"),
                new BigDecimal("2.056600000000000000")
            ),
            List.of(
                new FeeRebateImportedTransactionBean(
                    "449406209-fee",
                    Instant.parse("2023-10-03T14:30:28Z"),
                    DYDX,
                    DYDX,
                    FEE,
                    new BigDecimal("1.10065440000000000"),
                    DYDX
                )
            )
        );
        ParserTestUtils.checkEqual(expected, actual);
    }
}
