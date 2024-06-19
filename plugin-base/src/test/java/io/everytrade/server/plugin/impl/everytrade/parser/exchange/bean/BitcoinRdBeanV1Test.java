package io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean;

import io.everytrade.server.plugin.api.parser.FeeRebateImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.ImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.TransactionCluster;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static io.everytrade.server.model.Currency.DOP;
import static io.everytrade.server.model.Currency.USDT;
import static io.everytrade.server.model.TransactionType.BUY;
import static io.everytrade.server.model.TransactionType.FEE;
import static io.everytrade.server.model.TransactionType.SELL;

public class BitcoinRdBeanV1Test {

    private static final String HEADER_CORRECT = "side,size,price,timestamp,symbol,order_id,fee,fee_coin,quick\n";


    @Test
    void testSell() {
        final String row = "sell,252.54,59,\"2024-06-13T12:36:43.593Z\",\"usdt-dop\",\"510a6928-c591-4860-81f0-8a99608def6f\"," +
            "59.59944,\"dop\",\n";
        final TransactionCluster actual = ParserTestUtils.getTransactionCluster(HEADER_CORRECT + row);
        final TransactionCluster expected = new TransactionCluster(
            new ImportedTransactionBean(
                "510a6928-c591-4860-81f0-8a99608def6f",
                Instant.parse("2024-06-13T12:36:43.593Z"),
                USDT,
                DOP,
                SELL,
                new BigDecimal("252.54"),
                new BigDecimal("59")
            ),
            List.of(
                new FeeRebateImportedTransactionBean(
                    "510a6928-c591-4860-81f0-8a99608def6f-fee",
                    Instant.parse("2024-06-13T12:36:43.593Z"),
                    DOP,
                    DOP,
                    FEE,
                    new BigDecimal("59.59944000000000000"),
                    DOP
                )
            )
        );
        ParserTestUtils.checkEqual(expected, actual);
    }

    @Test
    void testBuy() {
        final String row = "\"buy\",113.6,57,\"2024-06-11T11:43:30.958Z\",\"usdt-dop\",\"b28aafde-cab9-4f48-94db-80326e4ff2a3\",0.4544," +
            "\"usdt\",\n";
        final TransactionCluster actual = ParserTestUtils.getTransactionCluster(HEADER_CORRECT + row);
        final TransactionCluster expected = new TransactionCluster(
            new ImportedTransactionBean(
                "b28aafde-cab9-4f48-94db-80326e4ff2a3",
                Instant.parse("2024-06-11T11:43:30.958Z"),
                USDT,
                DOP,
                BUY,
                new BigDecimal("113.6"),
                new BigDecimal("57")
            ),
            List.of(
                new FeeRebateImportedTransactionBean(
                    "b28aafde-cab9-4f48-94db-80326e4ff2a3-fee",
                    Instant.parse("2024-06-11T11:43:30.958Z"),
                    USDT,
                    USDT,
                    FEE,
                    new BigDecimal("0.45440000000000000"),
                    USDT
                )
            )
        );
        ParserTestUtils.checkEqual(expected, actual);
    }
}
