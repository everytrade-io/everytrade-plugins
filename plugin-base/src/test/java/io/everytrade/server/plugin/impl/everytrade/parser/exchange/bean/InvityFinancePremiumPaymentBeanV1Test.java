package io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean;

import io.everytrade.server.plugin.api.parser.ImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.TransactionCluster;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static io.everytrade.server.model.Currency.BTC;
import static io.everytrade.server.model.Currency.EUR;
import static io.everytrade.server.model.TransactionType.REWARD;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class InvityFinancePremiumPaymentBeanV1Test {

    private static final String HEADER_RAW =
        "finalizedAt,id,identityId,type,fiatCurrency,cryptoCurrency,premiumCryptoAmount," +
            "premiumFiatAmount,paymentMethodType,externalId,paymentCreated\n";

    @Test
    void testPremiumPaymentCard() {
        final String row0 =
            "2025-12-01 03:31:24.345,34d64489-93cd-46d0-8fa1-cb3802d0df54,6fe73f1d-6335-45e0-89c5-deeaa6b6d478," +
                "PremiumPayment,EUR,BTC,0.00000619,0.47,Card,pay_y7xhk6nvc6le7g63l64oudnnb4,2025-12-01 03:31:22.243\n";
        final List<TransactionCluster> actual = ParserTestUtils.getTransactionClusters(HEADER_RAW + row0);

        final TransactionCluster expected = new TransactionCluster(
            new ImportedTransactionBean(
                "34d64489-93cd-46d0-8fa1-cb3802d0df54",
                Instant.parse("2025-12-01T03:31:24.345Z"),
                EUR,
                EUR,
                REWARD,
                new BigDecimal("0.47"),
                null,
                "Premium payment fee od zakaznika - FIAT fee",
                null,
                "Card PremiumPayment",
                "6fe73f1d-6335-45e0-89c5-deeaa6b6d478",
                "pay_y7xhk6nvc6le7g63l64oudnnb4"
            ),
            List.of()
        );

        assertEquals(1, actual.size(), "Expected 1 transaction cluster");
        ParserTestUtils.checkEqual(expected, actual.get(0));
        assertMetadata(actual.get(0).getMain(), "Card PremiumPayment", "6fe73f1d-6335-45e0-89c5-deeaa6b6d478",
            "pay_y7xhk6nvc6le7g63l64oudnnb4");
    }

    @Test
    void testPremiumPaymentSepa() {
        final String row0 =
            "2025-12-01 06:50:00.37,768e10eb-fb3b-4ee2-ad87-b8d40da65c79,0749e1c9-df36-406e-9e6a-8fb7dca4abec," +
                "PremiumPayment,EUR,BTC,0.00000981,0.73,Sepa,12773995,2025-12-01 06:49:01.871\n";
        final List<TransactionCluster> actual = ParserTestUtils.getTransactionClusters(HEADER_RAW + row0);

        final TransactionCluster expected = new TransactionCluster(
            new ImportedTransactionBean(
                "768e10eb-fb3b-4ee2-ad87-b8d40da65c79",
                Instant.parse("2025-12-01T06:50:00.370Z"),
                EUR,
                EUR,
                REWARD,
                new BigDecimal("0.73"),
                null,
                "Premium payment fee od zakaznika - FIAT fee",
                null,
                "Sepa PremiumPayment",
                "0749e1c9-df36-406e-9e6a-8fb7dca4abec",
                "12773995"
            ),
            List.of()
        );

        assertEquals(1, actual.size(), "Expected 1 transaction cluster");
        ParserTestUtils.checkEqual(expected, actual.get(0));
        assertMetadata(actual.get(0).getMain(), "Sepa PremiumPayment", "0749e1c9-df36-406e-9e6a-8fb7dca4abec", "12773995");
    }

    @Test
    void testPremiumPaymentCryptoCZK() {
        final String row0 =
            "2025-12-01 08:12:10.231,fd5ced1d-a8a1-463b-9297-956e1d05a091,519a8d9f-1a58-4b57-af9c-596f73f048cd," +
                "PremiumPayment,CZK,BTC,0.00000321,5.78,,,\n";
        final List<TransactionCluster> actual = ParserTestUtils.getTransactionClusters(HEADER_RAW + row0);

        final TransactionCluster expected = new TransactionCluster(
            new ImportedTransactionBean(
                "fd5ced1d-a8a1-463b-9297-956e1d05a091",
                Instant.parse("2025-12-01T08:12:10.231Z"),
                BTC,
                BTC,
                REWARD,
                new BigDecimal("0.00000321"),
                null,
                "Premium payment fee od zakaznika - strzeni BTC z kolateralu",
                null,
                "PremiumPayment",
                "519a8d9f-1a58-4b57-af9c-596f73f048cd",
                null
            ),
            List.of()
        );

        assertEquals(1, actual.size(), "Expected 1 transaction cluster (crypto reward)");
        ParserTestUtils.checkEqual(expected, actual.get(0));
        assertMetadata(actual.get(0).getMain(), "PremiumPayment", "519a8d9f-1a58-4b57-af9c-596f73f048cd", null);
    }

    @Test
    void testPremiumPaymentCryptoEUR() {
        final String row0 =
            "2025-12-01 20:01:00.169,c26df2b9-6e48-4dd6-9bd8-9ca6005be6ef,4a2e1244-358f-4811-8033-e67a4bb45bbe," +
                "PremiumPayment,EUR,BTC,0.0000154,1.13,,,\n";
        final List<TransactionCluster> actual = ParserTestUtils.getTransactionClusters(HEADER_RAW + row0);

        final TransactionCluster expected = new TransactionCluster(
            new ImportedTransactionBean(
                "c26df2b9-6e48-4dd6-9bd8-9ca6005be6ef",
                Instant.parse("2025-12-01T20:01:00.169Z"),
                BTC,
                BTC,
                REWARD,
                new BigDecimal("0.0000154"),
                null,
                "Premium payment fee od zakaznika - strzeni BTC z kolateralu",
                null,
                "PremiumPayment",
                "4a2e1244-358f-4811-8033-e67a4bb45bbe",
                null
            ),
            List.of()
        );

        assertEquals(1, actual.size(), "Expected 1 transaction cluster (crypto reward)");
        ParserTestUtils.checkEqual(expected, actual.get(0));
        assertMetadata(actual.get(0).getMain(), "PremiumPayment", "4a2e1244-358f-4811-8033-e67a4bb45bbe", null);
    }

    @Test
    void testPremiumPaymentMultipleRows() {
        final String rows =
            "2025-12-01 03:32:39.19,113a8352-a584-46f3-beed-0147b9bd6525,f839870e-eb99-4e1f-8afe-0e5e847b5f5d," +
                "PremiumPayment,EUR,BTC,0.00000498,0.38,GooglePay,pay_jyfakcpredye3ber2m7abyktgm,2025-12-01 03:32:37.977\n" +
                "2025-12-01 03:32:53.414,6202533a-f53d-4d13-9fb9-863015bae548,06271c4c-dd26-446a-b6fa-dcad09de3536," +
                "PremiumPayment,EUR,BTC,0.00000577,0.44,Card,pay_bh4mgc7xjk7u3ewze6papq6moi,2025-12-01 03:32:51.379\n";
        final List<TransactionCluster> actual = ParserTestUtils.getTransactionClusters(HEADER_RAW + rows);

        assertEquals(2, actual.size(), "Expected 2 transaction clusters (2 rows)");
    }

    private static void assertMetadata(ImportedTransactionBean actual, String labels, String partner, String reference) {
        assertEquals(labels, actual.getLabels());
        assertEquals(partner, actual.getPartner());
        assertEquals(reference, actual.getReference());
    }
}
