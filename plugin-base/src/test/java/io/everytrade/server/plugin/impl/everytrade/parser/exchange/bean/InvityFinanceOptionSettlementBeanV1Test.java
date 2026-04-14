package io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean;

import io.everytrade.server.plugin.api.parser.ImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.TransactionCluster;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static io.everytrade.server.model.Currency.BTC;
import static io.everytrade.server.model.Currency.CZK;
import static io.everytrade.server.model.Currency.EUR;
import static io.everytrade.server.model.TransactionType.BUY;
import static io.everytrade.server.model.TransactionType.SELL;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class InvityFinanceOptionSettlementBeanV1Test {

    private static final String HEADER_RAW =
        "finalizedAt,\"id\",\"identityId\",\"type\",\"fiatCurrency\",\"cryptoCurrency\"," +
            "\"collateralCryptoAmount\",\"optionValueCryptoAmount\",\"optionValueFiatAmount\"," +
            "\"customerCryptoAmount\",\"customerFiatAmount\",\"settlementCryptoAmount\"," +
            "\"settlementFiatAmount\"\n";

    @Test
    void testOptionSettlementCZK() {
        final String row0 =
            "2025-12-01 08:12:10.263,\"a09b1031-6d34-4f95-a275-c0f2b0f326a5\",\"519a8d9f-1a58-4b57-af9c-596f73f048cd\"," +
                "\"OptionSettlement\",\"CZK\",\"BTC\",0.00037464,0.00022671,443.39,0.00035492,638.6,0.00024643,443.39\n";
        final List<TransactionCluster> actual = ParserTestUtils.getTransactionClusters(HEADER_RAW + row0);

        final TransactionCluster expectedBuy = new TransactionCluster(
            new ImportedTransactionBean(
                "a09b1031-6d34-4f95-a275-c0f2b0f326a5",
                Instant.parse("2025-12-01T08:12:10.263Z"),
                BTC,
                CZK,
                BUY,
                new BigDecimal("0.00024643"),
                new BigDecimal("1799253.33766181065617011"),
                "Turbo buy - settlement - splátka Option value od zákazníka",
                null,
                null,
                "519a8d9f-1a58-4b57-af9c-596f73f048cd",
                null
            ),
            List.of()
        );
        final TransactionCluster expectedSell = new TransactionCluster(
            new ImportedTransactionBean(
                "a09b1031-6d34-4f95-a275-c0f2b0f326a5",
                Instant.parse("2025-12-01T08:12:10.263Z"),
                BTC,
                CZK,
                SELL,
                new BigDecimal("0.00022671"),
                new BigDecimal("1955758.45794186405540117"),
                "Turbo buy - settlement - prodej Option value 60% BTC zákazníkovi",
                null,
                null,
                "519a8d9f-1a58-4b57-af9c-596f73f048cd",
                null
            ),
            List.of()
        );

        assertEquals(2, actual.size(), "Expected 2 transaction clusters");
        ParserTestUtils.checkEqual(expectedBuy, actual.get(0));
        ParserTestUtils.checkEqual(expectedSell, actual.get(1));
    }

    @Test
    void testOptionSettlementEUR() {
        final String row0 =
            "2025-12-02 17:09:14.951,\"85dcabee-7e06-4591-8f0c-50f2e7011420\",\"d6c84ed7-b930-409a-b346-6a22896b9bb2\"," +
                "\"OptionSettlement\",\"EUR\",\"BTC\",0.01216286,0.00734808,591.19,0.01196919,938.25,0.00754175,591.19\n";
        final List<TransactionCluster> actual = ParserTestUtils.getTransactionClusters(HEADER_RAW + row0);

        final TransactionCluster expectedBuy = new TransactionCluster(
            new ImportedTransactionBean(
                "85dcabee-7e06-4591-8f0c-50f2e7011420",
                Instant.parse("2025-12-02T17:09:14.951Z"),
                BTC,
                EUR,
                BUY,
                new BigDecimal("0.00754175"),
                new BigDecimal("78388.96807770079888620"),
                "Turbo buy - settlement - splátka Option value od zákazníka",
                null,
                null,
                "d6c84ed7-b930-409a-b346-6a22896b9bb2",
                null
            ),
            List.of()
        );
        final TransactionCluster expectedSell = new TransactionCluster(
            new ImportedTransactionBean(
                "85dcabee-7e06-4591-8f0c-50f2e7011420",
                Instant.parse("2025-12-02T17:09:14.951Z"),
                BTC,
                EUR,
                SELL,
                new BigDecimal("0.00734808"),
                new BigDecimal("80455.03042971769496249"),
                "Turbo buy - settlement - prodej Option value 60% BTC zákazníkovi",
                null,
                null,
                "d6c84ed7-b930-409a-b346-6a22896b9bb2",
                null
            ),
            List.of()
        );

        assertEquals(2, actual.size(), "Expected 2 transaction clusters");
        ParserTestUtils.checkEqual(expectedBuy, actual.get(0));
        ParserTestUtils.checkEqual(expectedSell, actual.get(1));
    }

    @Test
    void testOptionSettlementMultipleRows() {
        final String rows =
            "2025-12-03 05:48:52.557,\"c8f5b353-8b2c-4ad0-acd4-3b36f4c4e872\",\"7b08ca6f-0099-4a20-bf01-176da7a1b557\"," +
                "\"OptionSettlement\",\"EUR\",\"BTC\",0.00299572,0.00181021,159.09,0.00282565,227,0.00198028,159.09\n" +
                "2025-12-03 10:10:02.046,\"b57ab4e0-8574-463d-8629-2dd86c0790cd\",\"f9b3a828-b0ea-4f1f-b81a-c87f6c7696ad\"," +
                "\"OptionSettlement\",\"EUR\",\"BTC\",0.0004887,0.0002993,29.56,0.00041815,33.42,0.00036985,29.56\n";
        final List<TransactionCluster> actual = ParserTestUtils.getTransactionClusters(HEADER_RAW + rows);

        assertEquals(4, actual.size(), "Expected 4 transaction clusters (2 rows × 2 transactions per row)");
    }
}
