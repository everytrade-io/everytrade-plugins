package io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean;

import io.everytrade.server.plugin.api.parser.FeeRebateImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.ImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.TransactionCluster;
import io.everytrade.server.plugin.impl.everytrade.parser.EverytradeCsvMultiParser;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static io.everytrade.server.model.Currency.BTC;
import static io.everytrade.server.model.Currency.CNG;
import static io.everytrade.server.model.Currency.EUR;
import static io.everytrade.server.model.TransactionType.BUY;
import static io.everytrade.server.model.TransactionType.DEPOSIT;
import static io.everytrade.server.model.TransactionType.FEE;
import static io.everytrade.server.model.TransactionType.REWARD;
import static io.everytrade.server.model.TransactionType.SELL;
import static io.everytrade.server.model.TransactionType.WITHDRAWAL;

public class ChangeInvestBeanV1Test {

    private static final String HEADER_CORRECT = "created_time;id;order_type;from_currency;from_amount;to_currency;to_amount;fee;" +
        "execution_price;completed_time\n";

    @Test
    void testBuy() {
        final String row = "2021-10-29 20:32:54;3252040;BUY;EURT;700;BTC;0,012901;0;54259,36;2021-10-29 20:32:54\n";
        final TransactionCluster actual1 = ParserTestUtils.getTransactionCluster(HEADER_CORRECT + row);
        final TransactionCluster expected1 = new TransactionCluster(

            new ImportedTransactionBean(
                null,
                Instant.parse("2021-10-29T20:32:54Z"),
                BTC,
                EUR,
                BUY,
                new BigDecimal("0.012901"),
                new BigDecimal("54259.35973955507325014")
            ),
            List.of()
        );
        ParserTestUtils.checkEqual(expected1, actual1);
    }

    @Test
    void testSell() {
        final String row = "2024-05-01 08:59:47;11773524;SELL;BTC;0,222;EURT;11678,05;0;52603,83;2024-05-01 08:59:47\n";
        final TransactionCluster actual1 = ParserTestUtils.getTransactionCluster(HEADER_CORRECT + row);
        final TransactionCluster expected1 = new TransactionCluster(

            new ImportedTransactionBean(
                null,
                Instant.parse("2024-05-01T08:59:47Z"),
                BTC,
                EUR,
                SELL,
                new BigDecimal("0.222"),
                new BigDecimal("52603.82882882882882883")
            ),
            List.of()
        );
        ParserTestUtils.checkEqual(expected1, actual1);
    }

    @Test
    void testReward() {
        final String row = "2022-02-02 16:37:53;3746128;CAMPAIGN_BONUS;CNG;2;CNG;2;0;1;2022-02-02 16:37:53\n";
        final TransactionCluster actual1 = ParserTestUtils.getTransactionCluster(HEADER_CORRECT + row);
        final TransactionCluster expected1 = new TransactionCluster(

            new ImportedTransactionBean(
                null,
                Instant.parse("2022-02-02T16:37:53Z"),
                CNG,
                CNG,
                REWARD,
                new BigDecimal("2"),
                null
            ),
            List.of()
        );
        ParserTestUtils.checkEqual(expected1, actual1);
    }

    @Test
    void testFee() {
        final String row = "2025-01-19 10:50:28;15279599;FEE;EURT;20;EURT;20;0;;2025-01-19 10:50:29\n";
        final TransactionCluster actual1 = ParserTestUtils.getTransactionCluster(HEADER_CORRECT + row);
        final TransactionCluster expected1 = new TransactionCluster(

            new ImportedTransactionBean(
                null,
                Instant.parse("2025-01-19T10:50:29Z"),
                EUR,
                EUR,
                FEE,
                new BigDecimal("20"),
                null
            ),
            List.of()
        );
        ParserTestUtils.checkEqual(expected1, actual1);
    }

    @Test
    void testDeposit() {
        final String fiat_deposit = "2021-10-27 05:24:32;3231151;FIAT_DEPOSIT;EURT;20;EURT;20;0;1;2021-10-27 05:24:33\n";
        final String card_deposit = "2021-10-28 18:47:07;3245437;CARD_DEPOSIT;EURT;780;EURT;780;0;1;2021-10-28 18:49:32\n";
        final String crypto_deposit = "2023-11-28 18:47:05;9637723;CRYPTO_DEPOSIT;BTC;0,044;BTC;0,044;0;34723,5;2023-11-28 19:02:07\n";
        final var actual1 = ParserTestUtils.getTransactionClusters(HEADER_CORRECT + fiat_deposit + card_deposit + crypto_deposit);

        final TransactionCluster fiat_dep = new TransactionCluster(

            new ImportedTransactionBean(
                null,
                Instant.parse("2021-10-27T05:24:33Z"),
                EUR,
                EUR,
                DEPOSIT,
                new BigDecimal("20"),
                null
            ),
            List.of()
        );

        final TransactionCluster card_dep = new TransactionCluster(

            new ImportedTransactionBean(
                null,
                Instant.parse("2021-10-28T18:49:32Z"),
                EUR,
                EUR,
                DEPOSIT,
                new BigDecimal("780"),
                null
            ),
            List.of()
        );

        final TransactionCluster crypto_dep = new TransactionCluster(

            new ImportedTransactionBean(
                null,
                Instant.parse("2023-11-28T19:02:07Z"),
                BTC,
                BTC,
                DEPOSIT,
                new BigDecimal("0.044"),
                null
            ),
            List.of()
        );

        ParserTestUtils.checkEqual(fiat_dep, actual1.get(0));
        ParserTestUtils.checkEqual(card_dep, actual1.get(1));
        ParserTestUtils.checkEqual(crypto_dep, actual1.get(2));
    }

    @Test
    void testWithdraw() {
        final String crypto_withdraw = "2023-02-14 23:36:45;6255313;CRYPTO_WITHDRAW;BTC;0,0381927;BTC;0,03800695;0;20728,69;" +
            "2023-02-15 00:04:01\n";
        final String fiat_withdraw = "2024-05-01 10:36:25;11773731;FIAT_WITHDRAW;EURT;11678,05;EURT;11678,05;1;1;2024-05-01 10:41:25\n";

        final var actual1 = ParserTestUtils.getTransactionClusters(HEADER_CORRECT + crypto_withdraw + fiat_withdraw);

        final TransactionCluster crypto_withdrwl = new TransactionCluster(
            new ImportedTransactionBean(
                null,
                Instant.parse("2023-02-15T00:04:01Z"),
                BTC,
                BTC,
                WITHDRAWAL,
                new BigDecimal("0.03800695"),
                null
            ),
            List.of(
                new FeeRebateImportedTransactionBean(
                    null,
                    Instant.parse("2023-02-15T00:04:01Z"),
                    BTC,
                    BTC,
                    FEE,
                    new BigDecimal("0.00018575000000000"),
                    BTC
                )
            )
        );

        final TransactionCluster fiat_withdrwl = new TransactionCluster(
            new ImportedTransactionBean(
                null,
                Instant.parse("2024-05-01T10:41:25Z"),
                EUR,
                EUR,
                WITHDRAWAL,
                new BigDecimal("11678.05"),
                null
            ),
            List.of()
        );

        ParserTestUtils.checkEqual(crypto_withdrwl, actual1.get(0));
        ParserTestUtils.checkEqual(fiat_withdrwl, actual1.get(1));
    }
}
