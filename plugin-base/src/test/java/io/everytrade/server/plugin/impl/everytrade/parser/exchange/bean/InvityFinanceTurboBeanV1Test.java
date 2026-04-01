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
import static io.everytrade.server.model.TransactionType.REWARD;
import static io.everytrade.server.model.TransactionType.SELL;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class InvityFinanceTurboBeanV1Test {

    private static final String HEADER_RAW =
        "finalizedAt,\"id\",\"identityId\",\"type\",\"fiatCurrency\",\"settledFiatFinalAmount\"," +
            "\"settledFiatAmount\",\"feeAmount\",\"optionContractPremium\",\"cryptoCurrency\",\"collateralCryptoAmount\"," +
            "\"paymentMethodType\",\"externalId\",\"paymentCreated\",\"optionValueCryptoAmount\",\"optionValueFiatAmount\"\n";

    @Test
    void testTurboAddOptionValueWithFee() {
        final String row0 =
            "2025-12-01 01:37:37.29,\"39aef079-5407-4d62-b3e4-a61a88ddb524\",\"5b6eb61c-75ab-4308-8f7f-5affaecc4c71\"," +
                "\"AddOptionValue\",\"CZK\",14882,14663.51,218.49,0,\"BTC\",0.00784692,\"Card\"," +
                "\"pay_qrc2fssn7waebabxzorkcdkfmq\",\"2025-12-01 01:37:08.124\",0.00470815,8798.11\n";
        final List<TransactionCluster> actual = ParserTestUtils.getTransactionClusters(HEADER_RAW + row0);

        final TransactionCluster expectedSell = new TransactionCluster(
            new ImportedTransactionBean(
                "39aef079-5407-4d62-b3e4-a61a88ddb524",
                Instant.parse("2025-12-01T01:37:37.290Z"),
                BTC,
                CZK,
                SELL,
                new BigDecimal("0.00784692"),
                new BigDecimal("1868696.25279727587384604"),
                "Turbo buy - prodej kolaterálu zákazníkovi",
                null,
                "Card",
                "5b6eb61c-75ab-4308-8f7f-5affaecc4c71",
                "pay_qrc2fssn7waebabxzorkcdkfmq"
            ),
            List.of()
        );
        final TransactionCluster expectedReward = new TransactionCluster(
            new ImportedTransactionBean(
                "39aef079-5407-4d62-b3e4-a61a88ddb524",
                Instant.parse("2025-12-01T01:37:37.290Z"),
                CZK,
                CZK,
                REWARD,
                new BigDecimal("218.49"),
                null,
                "Fiat fees from Turbo buy Add Option Value",
                null,
                "Card",
                "5b6eb61c-75ab-4308-8f7f-5affaecc4c71",
                "pay_qrc2fssn7waebabxzorkcdkfmq"
            ),
            List.of()
        );

        assertEquals(2, actual.size(), "Expected 2 transaction clusters");
        ParserTestUtils.checkEqual(expectedSell, actual.get(0));
        ParserTestUtils.checkEqual(expectedReward, actual.get(1));
        assertMetadata(actual.get(0).getMain(), "Card", "5b6eb61c-75ab-4308-8f7f-5affaecc4c71", "pay_qrc2fssn7waebabxzorkcdkfmq");
        assertMetadata(actual.get(1).getMain(), "Card", "5b6eb61c-75ab-4308-8f7f-5affaecc4c71", "pay_qrc2fssn7waebabxzorkcdkfmq");
    }

    @Test
    void testTurboAddOptionValueWithPremiumEqualsFee() {
        final String row0 =
            "2025-12-01 03:31:24.109,\"79ef7947-5e5b-4d2d-994a-8b1705be1bbb\",\"6fe73f1d-6335-45e0-89c5-deeaa6b6d478\"," +
                "\"AddOptionValue\",\"EUR\",30,29.53,0.47,0.47,\"BTC\",0.00038904,\"Card\"," +
                "\"pay_y7xhk6nvc6le7g63l64oudnnb4\",\"2025-12-01 03:31:22.243\",0.00023342,17.72\n";
        final TransactionCluster actual = ParserTestUtils.getTransactionCluster(HEADER_RAW + row0);

        final TransactionCluster expected = new TransactionCluster(
            new ImportedTransactionBean(
                "79ef7947-5e5b-4d2d-994a-8b1705be1bbb",
                Instant.parse("2025-12-01T03:31:24.109Z"),
                BTC,
                EUR,
                SELL,
                new BigDecimal("0.00038904"),
                new BigDecimal("75904.79128110220028789"),
                "Turbo buy - prodej kolaterálu zákazníkovi",
                null,
                "Card",
                "6fe73f1d-6335-45e0-89c5-deeaa6b6d478",
                "pay_y7xhk6nvc6le7g63l64oudnnb4"
            ),
            List.of()
        );

        ParserTestUtils.checkEqual(expected, actual);
        assertMetadata(actual.getMain(), "Card", "6fe73f1d-6335-45e0-89c5-deeaa6b6d478", "pay_y7xhk6nvc6le7g63l64oudnnb4");
    }

    @Test
    void testTurboAddOptionValueWithCalculatedFee() {
        final String row0 =
            "2025-12-01 03:32:01.113,\"571ddacb-de0e-4852-97be-d3f9354b98a1\",\"fa50aae3-2194-4806-a3ff-149f64d88a20\"," +
                "\"AddOptionValue\",\"EUR\",50,48.56,1.44,0.71,\"BTC\",0.00063647,\"ApplePay\"," +
                "\"pay_ilmgvfi5sxjermkx73iyiowe4a\",\"2025-12-01 03:31:59.719\",0.00038188,29.14\n";
        final List<TransactionCluster> actual = ParserTestUtils.getTransactionClusters(HEADER_RAW + row0);

        final TransactionCluster expectedSell = new TransactionCluster(
            new ImportedTransactionBean(
                "571ddacb-de0e-4852-97be-d3f9354b98a1",
                Instant.parse("2025-12-01T03:32:01.113Z"),
                BTC,
                EUR,
                SELL,
                new BigDecimal("0.00063647"),
                new BigDecimal("76295.81912737442456047"),
                "Turbo buy - prodej kolaterálu zákazníkovi",
                null,
                "ApplePay",
                "fa50aae3-2194-4806-a3ff-149f64d88a20",
                "pay_ilmgvfi5sxjermkx73iyiowe4a"
            ),
            List.of()
        );
        final TransactionCluster expectedReward = new TransactionCluster(
            new ImportedTransactionBean(
                "571ddacb-de0e-4852-97be-d3f9354b98a1",
                Instant.parse("2025-12-01T03:32:01.113Z"),
                EUR,
                EUR,
                REWARD,
                new BigDecimal("0.73"),
                null,
                "Fiat fees from Turbo buy Add Option Value",
                null,
                "ApplePay",
                "fa50aae3-2194-4806-a3ff-149f64d88a20",
                "pay_ilmgvfi5sxjermkx73iyiowe4a"
            ),
            List.of()
        );

        ParserTestUtils.checkEqual(expectedSell, actual.get(0));
        ParserTestUtils.checkEqual(expectedReward, actual.get(1));
        assertMetadata(actual.get(0).getMain(), "ApplePay", "fa50aae3-2194-4806-a3ff-149f64d88a20", "pay_ilmgvfi5sxjermkx73iyiowe4a");
        assertMetadata(actual.get(1).getMain(), "ApplePay", "fa50aae3-2194-4806-a3ff-149f64d88a20", "pay_ilmgvfi5sxjermkx73iyiowe4a");
    }

    private static void assertMetadata(ImportedTransactionBean actual, String labels, String partner, String reference) {
        assertEquals(labels, actual.getLabels());
        assertEquals(partner, actual.getPartner());
        assertEquals(reference, actual.getReference());
    }
}
