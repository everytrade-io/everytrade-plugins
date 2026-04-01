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
import static io.everytrade.server.model.TransactionType.REWARD;
import static io.everytrade.server.model.TransactionType.SELL;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class InvityFinanceBuySellBeanV1Test {

    private static final String HEADER_CORRECT_RAW =
        "\"finalizedAt,\"\"id\"\",\"\"identityId\"\",\"\"type\"\",\"\"fiatCurrency\"\"," +
            "\"\"settledFiatFinalAmount\"\",\"\"settledFiatAmount\"\",\"\"feeAmount\"\",\"\"cryptoCurrency\"\"," +
            "\"\"settledCryptoAmount\"\",\"\"paymentMethodType\"\",\"\"externalId\"\",\"\"paymentCreated\"\"\"\n";

    private static final String HEADER_NONCUSTODIAL_RAW =
        "\"finalizedAt,\"\"id\"\",\"\"identityId\"\",\"\"type\"\",\"\"fiatCurrency\"\"," +
            "\"\"settledFiatFinalAmount\"\",\"\"settledFiatAmount\"\",\"\"feeAmount\"\",\"\"cryptoCurrency\"\"," +
            "\"\"settledCryptoAmount\"\",\"\"blockchainTxid\"\",\"\"paymentMethodType\"\",\"\"externalId\"\",\"\"paymentCreated\"\"\"\n";

    @Test
    void testCustodialBuy() {
        final String row0 =
            "\"2025-12-01 00:30:25.196,\"\"b20795b4-580f-4888-8865-cf4998e9d5cb\"\"," +
                "\"\"04a97eea-ef3b-4b4e-b5b8-17f9ee66be06\"\",\"\"Buy\"\",\"\"CZK\"\",1000,985.32,14.68," +
                "\"\"BTC\"\",0.0005222,\"\"ApplePay\"\",\"\"pay_ijozaxsttunenpiy4amkudabii\"\"," +
                "\"\"2025-12-01 00:30:23.384\"\"\"\n";
        final List<TransactionCluster> actual = ParserTestUtils.getTransactionClusters(HEADER_CORRECT_RAW + row0);

        final TransactionCluster expectedSell = new TransactionCluster(
            new ImportedTransactionBean(
                "b20795b4-580f-4888-8865-cf4998e9d5cb",
                Instant.parse("2025-12-01T00:30:25.196Z"),
                BTC,
                CZK,
                SELL,
                new BigDecimal("0.0005222"),
                new BigDecimal("1886863.27077747989276139"),
                "Custodial buy",
                null,
                "ApplePay",
                "04a97eea-ef3b-4b4e-b5b8-17f9ee66be06",
                "pay_ijozaxsttunenpiy4amkudabii"
            ),
            List.of()
        );
        final TransactionCluster expectedReward = new TransactionCluster(
            new ImportedTransactionBean(
                "b20795b4-580f-4888-8865-cf4998e9d5cb",
                Instant.parse("2025-12-01T00:30:25.196Z"),
                CZK,
                CZK,
                REWARD,
                new BigDecimal("14.68"),
                null,
                "Fiat fees from custodial buy",
                null,
                "ApplePay",
                "04a97eea-ef3b-4b4e-b5b8-17f9ee66be06",
                "pay_ijozaxsttunenpiy4amkudabii"
            ),
            List.of()
        );

        ParserTestUtils.checkEqual(expectedSell, actual.get(0));
        ParserTestUtils.checkEqual(expectedReward, actual.get(1));
        assertMetadata(actual.get(0).getMain(), "ApplePay", "04a97eea-ef3b-4b4e-b5b8-17f9ee66be06", "pay_ijozaxsttunenpiy4amkudabii");
        assertMetadata(actual.get(1).getMain(), "ApplePay", "04a97eea-ef3b-4b4e-b5b8-17f9ee66be06", "pay_ijozaxsttunenpiy4amkudabii");
    }

    @Test
    void testCustodialSell() {
        final String row0 =
            "\"2025-12-02 00:46:17.113,\"\"becb8ebf-07d8-4ea0-b5b2-aad3972a22f7\"\",\"\"1821682d-3301-4433-b3ac-62780f06ab88\"\"," +
                "\"\"Sell\"\",\"\"EUR\"\",50,50,0,\"\"BTC\"\",0.00063612,\"\"GooglePay\"\",\"\"pay_wkljrfdwjhlero6h72lkk2egka\"\"," +
                "\"\"2025-12-02 00:45:47.146\"\"\"\n";
        final TransactionCluster actual = ParserTestUtils.getTransactionCluster(HEADER_CORRECT_RAW + row0);

        final TransactionCluster expected = new TransactionCluster(
            new ImportedTransactionBean(
                "becb8ebf-07d8-4ea0-b5b2-aad3972a22f7",
                Instant.parse("2025-12-02T00:46:17.113Z"),
                BTC,
                EUR,
                BUY,
                new BigDecimal("0.00063612"),
                new BigDecimal("78601.52172546060491731"),
                "Custodial sell",
                null,
                "GooglePay",
                "1821682d-3301-4433-b3ac-62780f06ab88",
                "pay_wkljrfdwjhlero6h72lkk2egka"
            ),
            List.of()
        );

        ParserTestUtils.checkEqual(expected, actual);
        assertMetadata(actual.getMain(), "GooglePay", "1821682d-3301-4433-b3ac-62780f06ab88", "pay_wkljrfdwjhlero6h72lkk2egka");
    }

    @Test
    void testCustodialBuyWithoutFee() {
        final String row0 =
            "\"2025-12-03 03:27:15.458,\"\"8a243465-228c-4123-8b02-6fcdea99b78d\"\",\"\"4a9bba5d-8487-48f3-816b-612dd6e426fc\"\"," +
                "\"\"Buy\"\",\"\"EUR\"\",101.49,100,0,\"\"BTC\"\",0.00130824,\"\"ApplePay\"\",\"\"pay_eeyz6zbqqure3byplpsvdrrzm4\"\"," +
                "\"\"2025-12-03 03:27:13.395\"\"\"\n";
        final TransactionCluster actual = ParserTestUtils.getTransactionCluster(HEADER_CORRECT_RAW + row0);

        final TransactionCluster expected = new TransactionCluster(
            new ImportedTransactionBean(
                "8a243465-228c-4123-8b02-6fcdea99b78d",
                Instant.parse("2025-12-03T03:27:15.458Z"),
                BTC,
                EUR,
                SELL,
                new BigDecimal("0.00130824"),
                new BigDecimal("76438.57396196416559653"),
                "Custodial buy",
                null,
                "ApplePay",
                "4a9bba5d-8487-48f3-816b-612dd6e426fc",
                "pay_eeyz6zbqqure3byplpsvdrrzm4"
            ),
            List.of()
        );

        ParserTestUtils.checkEqual(expected, actual);
        assertMetadata(actual.getMain(), "ApplePay", "4a9bba5d-8487-48f3-816b-612dd6e426fc", "pay_eeyz6zbqqure3byplpsvdrrzm4");
    }

    @Test
    void testNoncustodialBuy() {
        final String row0 =
            "\"2025-12-01 02:51:44.234,\"\"2a1f814a-f3c6-45be-bf89-3d861dcac9f5\"\",\"\"c849b708-d977-4735-b2c6-8d0c43140d10\"\"," +
                "\"\"Buy\"\",\"\"EUR\"\",250,242.74,7.26,\"\"BTC\"\",0.00324706," +
                "\"\"acddd931da958c55f097f4ca78592d1469518e0a985577a21e35d943d1ce73a0\"\",\"\"GooglePay\"\"," +
                "\"\"pay_isdonkm5aiqejgylidjsqoi4nu\"\",\"\"2025-12-01 02:47:48.214\"\"\"\n";
        final List<TransactionCluster> actual = ParserTestUtils.getTransactionClusters(HEADER_NONCUSTODIAL_RAW + row0);

        final TransactionCluster expectedSell = new TransactionCluster(
            new ImportedTransactionBean(
                "2a1f814a-f3c6-45be-bf89-3d861dcac9f5",
                Instant.parse("2025-12-01T02:51:44.234Z"),
                BTC,
                EUR,
                SELL,
                new BigDecimal("0.00324706"),
                new BigDecimal("74756.85697215327095896"),
                "Non-custodial buy",
                null,
                "GooglePay",
                "c849b708-d977-4735-b2c6-8d0c43140d10",
                "pay_isdonkm5aiqejgylidjsqoi4nu"
            ),
            List.of()
        );
        final TransactionCluster expectedReward = new TransactionCluster(
            new ImportedTransactionBean(
                "2a1f814a-f3c6-45be-bf89-3d861dcac9f5",
                Instant.parse("2025-12-01T02:51:44.234Z"),
                EUR,
                EUR,
                REWARD,
                new BigDecimal("7.26"),
                null,
                "Fiat fees from non-custodial buy",
                null,
                "GooglePay",
                "c849b708-d977-4735-b2c6-8d0c43140d10",
                "pay_isdonkm5aiqejgylidjsqoi4nu"
            ),
            List.of()
        );

        ParserTestUtils.checkEqual(expectedSell, actual.get(0));
        ParserTestUtils.checkEqual(expectedReward, actual.get(1));
        assertMetadata(actual.get(0).getMain(), "GooglePay", "c849b708-d977-4735-b2c6-8d0c43140d10", "pay_isdonkm5aiqejgylidjsqoi4nu");
        assertMetadata(actual.get(1).getMain(), "GooglePay", "c849b708-d977-4735-b2c6-8d0c43140d10", "pay_isdonkm5aiqejgylidjsqoi4nu");
    }

    @Test
    void testNoncustodialBuyWithoutFee() {
        final String row0 =
            "\"2025-12-01 08:24:57.437,\"\"a48ab564-eacf-490f-8e22-a608dfaa96a7\"\",\"\"fd4c5085-c052-4b47-96e1-07b1e18dcf96\"\"," +
                "\"\"Buy\"\",\"\"EUR\"\",500,499,1,\"\"BTC\"\",0.00663101," +
                "\"\"843c6352b74ba0f614ab8a307f1f64932f49f3fbbe8815d34ecbbe6203b88206\"\",\"\"Sepa\"\",\"\"12776720\"\"," +
                "\"\"2025-12-01 08:19:02.05\"\"\"\n";
        final TransactionCluster actual = ParserTestUtils.getTransactionCluster(HEADER_NONCUSTODIAL_RAW + row0);

        final TransactionCluster expected = new TransactionCluster(
            new ImportedTransactionBean(
                "a48ab564-eacf-490f-8e22-a608dfaa96a7",
                Instant.parse("2025-12-01T08:24:57.437Z"),
                BTC,
                EUR,
                SELL,
                new BigDecimal("0.00663101"),
                new BigDecimal("75252.48793170271195489"),
                "Non-custodial buy",
                null,
                "Sepa",
                "fd4c5085-c052-4b47-96e1-07b1e18dcf96",
                "12776720"
            ),
            List.of()
        );

        ParserTestUtils.checkEqual(expected, actual);
        assertMetadata(actual.getMain(), "Sepa", "fd4c5085-c052-4b47-96e1-07b1e18dcf96", "12776720");
    }

    private static void assertMetadata(ImportedTransactionBean actual, String labels, String partner, String reference) {
        assertEquals(labels, actual.getLabels());
        assertEquals(partner, actual.getPartner());
        assertEquals(reference, actual.getReference());
    }
}
