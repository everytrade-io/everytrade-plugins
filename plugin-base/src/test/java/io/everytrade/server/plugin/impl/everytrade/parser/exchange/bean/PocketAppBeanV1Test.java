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
import static io.everytrade.server.model.TransactionType.DEPOSIT;
import static io.everytrade.server.model.TransactionType.FEE;
import static io.everytrade.server.model.TransactionType.SELL;
import static io.everytrade.server.model.TransactionType.WITHDRAWAL;

public class PocketAppBeanV1Test {

    private static final String HEADER_CORRECT = "type,date,reference,price.currency,price.amount,cost.currency,cost.amount," +
        "fee.currency,fee.amount,value.currency,value.amount\n";

    @Test
    void testBuy() {
        final String row = "exchange,2024-08-29T10:04:11.000Z,RF96VU5HH4,EUR,53769.99000000,EUR,985.00000000,EUR,15.00000000,BTC," +
            "0.01831876,,,\n";
        final TransactionCluster actual1 = ParserTestUtils.getTransactionCluster(HEADER_CORRECT + row);
        final TransactionCluster expected1 = new TransactionCluster(

            new ImportedTransactionBean(
                null,
                Instant.parse("2024-08-29T10:04:11Z"),
                BTC,
                EUR,
                BUY,
                new BigDecimal("0.01831876"),
                new BigDecimal("53770.01500101535256753")
            ),
            List.of(
                new FeeRebateImportedTransactionBean(
                    null,
                    Instant.parse("2024-08-29T10:04:11Z"),
                    EUR,
                    EUR,
                    FEE,
                    new BigDecimal("15.00000000000000000"),
                    EUR
                )
            )
        );
        ParserTestUtils.checkEqual(expected1, actual1);
    }

    @Test
    void testSell() {
        final String row = "exchange,2024-08-29T10:04:11.000Z,RF96VU5HH4,EUR,53769.99000000,BTC,985.00000000,EUR,15.00000000," +
            "EUR,0.01831876,,,\n";
        final TransactionCluster actual1 = ParserTestUtils.getTransactionCluster(HEADER_CORRECT + row);
        final TransactionCluster expected1 = new TransactionCluster(

            new ImportedTransactionBean(
                null,
                Instant.parse("2024-08-29T10:04:11.000Z"),
                BTC,
                EUR,
                SELL,
                new BigDecimal("985.00000000"),
                new BigDecimal("53770.01500101535256753")
            ),
            List.of(
                new FeeRebateImportedTransactionBean(
                    null,
                    Instant.parse("2024-08-29T10:04:11.000Z"),
                    EUR,
                    EUR,
                    FEE,
                    new BigDecimal("15.00000000000000000"),
                    EUR
                )
            )
        );
        ParserTestUtils.checkEqual(expected1, actual1);
    }

    @Test
    void testDeposit() {
        final String row = "deposit,2024-02-06T11:48:39.099Z,RF96VU5HH4,,,,,EUR,0.00000000,EUR,500.00000000,,,\n";
        final TransactionCluster actual1 = ParserTestUtils.getTransactionCluster(HEADER_CORRECT + row);
        final TransactionCluster expected1 = new TransactionCluster(

            new ImportedTransactionBean(
                null,
                Instant.parse("2024-02-06T11:48:39.099Z"),
                EUR,
                EUR,
                DEPOSIT,
                new BigDecimal("500.00000000"),
                null
                ),
            List.of()
        );
        ParserTestUtils.checkEqual(expected1, actual1);
    }

    @Test
    void testWithdraw() {
        final String row = "withdrawal,2024-02-06T11:57:45.177Z,,,,,,BTC,0.00000000,BTC,0.01235745,,,\n";
        final TransactionCluster actual1 = ParserTestUtils.getTransactionCluster(HEADER_CORRECT + row);
        final TransactionCluster expected1 = new TransactionCluster(

            new ImportedTransactionBean(
                null,
                Instant.parse("2024-02-06T11:57:45.177Z"),
                BTC,
                BTC,
                WITHDRAWAL,
                new BigDecimal("0.01235745"),
                null
            ),
            List.of()
        );
        ParserTestUtils.checkEqual(expected1, actual1);
    }
}
