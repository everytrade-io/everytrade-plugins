package io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean;

import io.everytrade.server.plugin.api.parser.FeeRebateImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.ImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.TransactionCluster;
import io.everytrade.server.test.TestUtils;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static io.everytrade.server.model.Currency.BTC;
import static io.everytrade.server.model.Currency.CZK;
import static io.everytrade.server.model.Currency.EUR;
import static io.everytrade.server.model.Currency.USD;
import static io.everytrade.server.model.Currency.XRP;
import static io.everytrade.server.model.TransactionType.BUY;
import static io.everytrade.server.model.TransactionType.DEPOSIT;
import static io.everytrade.server.model.TransactionType.FEE;
import static io.everytrade.server.model.TransactionType.SELL;
import static io.everytrade.server.model.TransactionType.WITHDRAWAL;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.ExchangeBean.FEE_UID_PART;

public class RevolutBeanV1Test {

    private static final String HEADER_CORRECT = "Symbol,Type,Quantity,Price,Value,Fees,Date\n";

    @Test
    void testBuy() {
        final String row0 = "XRP,Buy,1998.8843,35.52 CZK,71000.00 CZK,764.99 CZK,\"13 Apr 2021, 09:54:54\"";
        final List<TransactionCluster> actual = ParserTestUtils.getTransactionClusters(HEADER_CORRECT + row0);

        final TransactionCluster expected = new TransactionCluster(
            new ImportedTransactionBean(
                null,
                Instant.parse("2021-04-13T07:54:54Z"),
                XRP,
                CZK,
                BUY,
                new BigDecimal("1998.8843"),
                new BigDecimal("35.52"),
                null,
                null
            ),
            List.of(
                new FeeRebateImportedTransactionBean(
                    FEE_UID_PART,
                    Instant.parse("2021-04-13T07:54:54Z"),
                    CZK,
                    CZK,
                    FEE,
                    new BigDecimal("764.99000000000000000"),
                    CZK
                )
            )
        );

        TestUtils.testTxs(expected.getMain(), actual.get(0).getMain());
        TestUtils.testTxs(expected.getRelated().get(0), actual.get(0).getRelated().get(0));
    }

    @Test
    void testSell() {
        final String row0 = "BTC,Sell - Revolut X,0.06,$97830.36,$5869.82,$5.29,\"23 Nov 2024, 23:54:47\"";
        final List<TransactionCluster> actual = ParserTestUtils.getTransactionClusters(HEADER_CORRECT + row0);

        final TransactionCluster expected = new TransactionCluster(
            new ImportedTransactionBean(
                null,
                Instant.parse("2024-11-23T22:54:47Z"),
                BTC,
                USD,
                SELL,
                new BigDecimal("0.06"),
                new BigDecimal("97830.33"),
                null,
                null
            ),
            List.of(
                new FeeRebateImportedTransactionBean(
                    FEE_UID_PART,
                    Instant.parse("2024-11-23T22:54:47Z"),
                    USD,
                    USD,
                    FEE,
                    new BigDecimal("5.29000000000000000"),
                    USD
                )
            )
        );

        TestUtils.testTxs(expected.getMain(), actual.get(0).getMain());
        TestUtils.testTxs(expected.getRelated().get(0), actual.get(0).getRelated().get(0));
    }

    @Test
    void testWithdrawal() {
        final String row0 = "USD,Send,,,$4201.00,$0.00,\"4 Dec 2024, 21:34:33\"\n";
        final List<TransactionCluster> actual = ParserTestUtils.getTransactionClusters(HEADER_CORRECT + row0);

        final TransactionCluster expected = new TransactionCluster(
            new ImportedTransactionBean(
                null,
                Instant.parse("2024-12-04T20:34:33Z"),
                USD,
                USD,
                WITHDRAWAL,
                new BigDecimal("4201.00"),
                null,
                null,
                null
            ),
            List.of()
        );

        TestUtils.testTxs(expected.getMain(), actual.get(0).getMain());
    }

    @Test
    void testBuyV2DateFormat() {
        final String row0 = "BTC,Buy,0.00000004,2392643.76 CZK,0.10 CZK,0.01 CZK,\"Jan 2, 2025, 3:42:51 PM\"";
        final List<TransactionCluster> actual = ParserTestUtils.getTransactionClusters(HEADER_CORRECT + row0);

        final TransactionCluster expected = new TransactionCluster(
            new ImportedTransactionBean(
                null,
                Instant.parse("2025-01-02T14:42:51Z"),
                BTC,
                CZK,
                BUY,
                new BigDecimal("0.00000004"),
                new BigDecimal("2500000.00"),
                null,
                null
            ),
            List.of(
                new FeeRebateImportedTransactionBean(
                    FEE_UID_PART,
                    Instant.parse("2025-01-02T14:42:51Z"),
                    CZK,
                    CZK,
                    FEE,
                    new BigDecimal("0.01000000000000000"),
                    CZK
                )
            )
        );

        TestUtils.testTxs(expected.getMain(), actual.get(0).getMain());
        TestUtils.testTxs(expected.getRelated().get(0), actual.get(0).getRelated().get(0));
    }

    @Test
    void testBuyCommaDecimalSeparator() {
        final String row0 = "BTC,Buy,0.00000123,\"71000,00 CZK\",\"0,09 CZK\",\"0,01 CZK\",\"13 Apr 2021, 09:54:54\"";
        final List<TransactionCluster> actual = ParserTestUtils.getTransactionClusters(HEADER_CORRECT + row0);

        final TransactionCluster expected = new TransactionCluster(
            new ImportedTransactionBean(
                null,
                Instant.parse("2021-04-13T07:54:54Z"),
                BTC,
                CZK,
                BUY,
                new BigDecimal("0.00000123"),
                new BigDecimal("73170.73"),
                null,
                null
            ),
            List.of(
                new FeeRebateImportedTransactionBean(
                    FEE_UID_PART,
                    Instant.parse("2021-04-13T07:54:54Z"),
                    CZK,
                    CZK,
                    FEE,
                    new BigDecimal("0.01000000000000000"),
                    CZK
                )
            )
        );

        TestUtils.testTxs(expected.getMain(), actual.get(0).getMain());
        TestUtils.testTxs(expected.getRelated().get(0), actual.get(0).getRelated().get(0));
    }

    @Test
    void testDeposit() {
        final String row0 = "EUR,Receive,,,€8.00,€0.00,\"12 May 2025, 09:19:04\"\n";
        final List<TransactionCluster> actual = ParserTestUtils.getTransactionClusters(HEADER_CORRECT + row0);

        final TransactionCluster expected = new TransactionCluster(
            new ImportedTransactionBean(
                null,
                Instant.parse("2025-05-12T07:19:04Z"),
                EUR,
                EUR,
                DEPOSIT,
                new BigDecimal("8.00"),
                null,
                null,
                null
            ),
            List.of()
        );

        TestUtils.testTxs(expected.getMain(), actual.get(0).getMain());
    }

    @Test
    void testCryptoDepositWithQuantity() {
        final String row0 = "BTC,Receive,0.00000123,,\"1000,00 CZK\",\"0,00 CZK\",\"13 Apr 2021, 09:54:54\"";
        final List<TransactionCluster> actual = ParserTestUtils.getTransactionClusters(HEADER_CORRECT + row0);

        final TransactionCluster expected = new TransactionCluster(
            new ImportedTransactionBean(
                null,
                Instant.parse("2021-04-13T07:54:54Z"),
                BTC,
                CZK,
                DEPOSIT,
                new BigDecimal("0.00000123"),
                null,
                null,
                null
            ),
            List.of()
        );

        TestUtils.testTxs(expected.getMain(), actual.get(0).getMain());
    }
}
