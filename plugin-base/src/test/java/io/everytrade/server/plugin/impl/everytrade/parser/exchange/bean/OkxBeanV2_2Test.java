package io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean;

import io.everytrade.server.plugin.api.parser.FeeRebateImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.ImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.TransactionCluster;
import io.everytrade.server.test.TestUtils;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static io.everytrade.server.model.Currency.ADA;
import static io.everytrade.server.model.Currency.BTC;
import static io.everytrade.server.model.Currency.EUR;
import static io.everytrade.server.model.TransactionType.BUY;
import static io.everytrade.server.model.TransactionType.DEPOSIT;
import static io.everytrade.server.model.TransactionType.FEE;
import static io.everytrade.server.model.TransactionType.SELL;
import static io.everytrade.server.model.TransactionType.WITHDRAWAL;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.ExchangeBean.FEE_UID_PART;

public class OkxBeanV2_2Test {

    private static final String HEADER_BUY_SELL = "id,Order id,Time,Trade Type,Symbol,Action,Amount,Trading Unit,Filled Price," +
        "PnL,Fee,Fee Unit,Position Change,Position Balance,Balance Change,Balance,Balance Unit\n";

    private static final String HEADER_DEP_WDRL = "id,Time,Type,Amount,Before Balance,After Balance,Symbol\n";

    @Test
    void testWithdrawal() {
        final String row0 = "10021312703,2025-10-31 11:55:15,Withdrawal,-0.011478630000000000,0.011478630000000000,0E-18,BTC";
        final List<TransactionCluster> actual = ParserTestUtils.getTransactionClusters(HEADER_DEP_WDRL + row0);

        final TransactionCluster expected = new TransactionCluster(
            new ImportedTransactionBean(
                null,
                Instant.parse("2025-10-31T11:55:15Z"),
                BTC,
                BTC,
                WITHDRAWAL,
                new BigDecimal("0.011478630000000000"),
                null,
                null,
                null
            ),
            List.of()
        );

        TestUtils.testTxs(expected.getMain(), actual.get(0).getMain());
    }

    @Test
    void testDeposit() {
        final String row0 = "10021312314,2025-10-31 11:54:20,Deposit,0.000100000000000000,0.011478630000000000,0.011578630000000000,BTC";
        final List<TransactionCluster> actual = ParserTestUtils.getTransactionClusters(HEADER_DEP_WDRL + row0);

        final TransactionCluster expected = new TransactionCluster(
            new ImportedTransactionBean(
                null,
                Instant.parse("2025-10-31T11:54:20Z"),
                BTC,
                BTC,
                DEPOSIT,
                new BigDecimal("0.000100000000000000"),
                null,
                null,
                null
            ),
            List.of()
        );

        TestUtils.testTxs(expected.getMain(), actual.get(0).getMain());
    }

    @Test
    void testBuy() {
        final String row0 = """
            2968046098898771968,2968046098831663104,2025-10-20 10:44:26,Spot,BTC-EUR,Buy,0.06436375,EUR,95264.80000000,\
            0.00000000,-0.00016091,BTC,0.00000000,0.00000000,0.06420284,0.12011841,BTC
            2968046098898771969,2968046098831663104,2025-10-20 10:44:26,Spot,BTC-EUR,Sell,6131.59977100,EUR,95264.80000000,\
            0.00000000,0.00000000,EUR,0.00000000,0.00000000,-6131.59977100,0.09340798,EUR
            """;
        final List<TransactionCluster> actual = ParserTestUtils.getTransactionClusters(HEADER_BUY_SELL + row0);

        final TransactionCluster expected = new TransactionCluster(
            new ImportedTransactionBean(
                null,
                Instant.parse("2025-10-20T10:44:26Z"),
                BTC,
                EUR,
                BUY,
                new BigDecimal("0.06436375"),
                new BigDecimal("95264.80000000000000000"),
                null,
                null
            ),
            List.of(
                new FeeRebateImportedTransactionBean(
                    FEE_UID_PART,
                    Instant.parse("2025-10-20T10:44:26Z"),
                    BTC,
                    BTC,
                    FEE,
                    new BigDecimal("0.00016091"),
                    BTC
                )
            )
        );

        TestUtils.testTxs(expected.getMain(), actual.get(0).getMain());
        TestUtils.testTxs(expected.getRelated().get(0), actual.get(0).getRelated().get(0));
    }

    @Test
    void testSellMultipleRows() {
        final String row0 = """
            2968045184439836680,2968045184406282240,2025-10-20 10:43:59,Spot,ADA-EUR,Sell,149.29100000,EUR,0.57490000,0.00000000\
            ,0.00000000,ADA,0.00000000,0.00000000,-149.29100000,1554.15920000,ADA
            2968045184439836681,2968045184406282240,2025-10-20 10:43:59,Spot,ADA-EUR,Buy,85.82739590,EUR,0.57490000,0.00000000,\
            -0.21456849,EUR,0.00000000,0.00000000,85.61282741,10580.73084695,EUR
            2968045184439836678,2968045184406282240,2025-10-20 10:43:59,Spot,ADA-EUR,Sell,149.29100000,EUR,0.57490000,0.00000000,\
            0.00000000,ADA,0.00000000,0.00000000,-149.29100000,1703.45020000,ADA
            2968045184439836673,2968045184406282240,2025-10-20 10:43:59,Spot,ADA-EUR,Buy,643.70551472,EUR,0.57520000,0.00000000,\
            -1.60926379,EUR,0.00000000,0.00000000,642.09625093,8323.62271333,EUR
            2968045184439836679,2968045184406282240,2025-10-20 10:43:59,Spot,ADA-EUR,Buy,85.82739590,EUR,0.57490000,0.00000000,\
            -0.21456849,EUR,0.00000000,0.00000000,85.61282741,10495.11801954,EUR
            2968045184439836675,2968045184406282240,2025-10-20 10:43:59,Spot,ADA-EUR,Buy,911.59902000,EUR,0.57500000,0.00000000,\
            -2.27899755,EUR,0.00000000,0.00000000,909.32002245,9232.94273578,EUR
            2968045184439836683,2968045184406282240,2025-10-20 10:43:59,Spot,ADA-EUR,Buy,893.33070816,EUR,0.57480000,0.00000000,\
            -2.23332677,EUR,0.00000000,0.00000000,891.09738139,11471.82822834,EUR
            2968045184439836676,2968045184406282240,2025-10-20 10:43:59,Spot,ADA-EUR,Sell,2051.68070000,EUR,0.57490000,0.00000000,\
            0.00000000,ADA,0.00000000,0.00000000,-2051.68070000,1852.74120000,ADA
            2968045184439836674,2968045184406282240,2025-10-20 10:43:59,Spot,ADA-EUR,Sell,1585.38960000,EUR,0.57500000,0.00000000,\
            0.00000000,ADA,0.00000000,0.00000000,-1585.38960000,3904.42190000,ADA
            2968045184439836682,2968045184406282240,2025-10-20 10:43:59,Spot,ADA-EUR,Sell,1554.15920000,EUR,0.57480000,0.00000000,\
            0.00000000,ADA,0.00000000,0.00000000,-1554.15920000,0.00000000,ADA
            2968045184439836672,2968045184406282240,2025-10-20 10:43:59,Spot,ADA-EUR,Sell,1119.09860000,EUR,0.57520000,0.00000000,\
            0.00000000,ADA,0.00000000,0.00000000,-1119.09860000,5489.81150000,ADA
            2968045184439836677,2968045184406282240,2025-10-20 10:43:59,Spot,ADA-EUR,Buy,1179.51123443,EUR,0.57490000,0.00000000,\
            -2.94877809,EUR,0.00000000,0.00000000,1176.56245634,10409.50519213,EUR
        """;
        final List<TransactionCluster> actual = ParserTestUtils.getTransactionClusters(HEADER_BUY_SELL + row0);

        final TransactionCluster expected = new TransactionCluster(
            new ImportedTransactionBean(
                null,
                Instant.parse("2025-10-20T10:43:59Z"),
                ADA,
                EUR,
                SELL,
                new BigDecimal("6608.91010000"),
                new BigDecimal("0.57495127208796500"),
                null,
                null
            ),
            List.of(
                new FeeRebateImportedTransactionBean(
                    FEE_UID_PART,
                    Instant.parse("2025-10-20T10:43:59Z"),
                    EUR,
                    EUR,
                    FEE,
                    new BigDecimal("9.49950318"),
                    EUR
                )
            )
        );

        TestUtils.testTxs(expected.getMain(), actual.get(0).getMain());
        TestUtils.testTxs(expected.getRelated().get(0), actual.get(0).getRelated().get(0));
    }
}
