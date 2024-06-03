package io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean;

import io.everytrade.server.plugin.api.parser.ImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.TransactionCluster;
import io.everytrade.server.plugin.impl.everytrade.parser.EverytradeCsvMultiParser;
import io.everytrade.server.test.TestUtils;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static io.everytrade.server.model.Currency.BTC;
import static io.everytrade.server.model.Currency.ETH;
import static io.everytrade.server.model.TransactionType.BUY;
import static io.everytrade.server.model.TransactionType.DEPOSIT;
import static io.everytrade.server.model.TransactionType.EARNING;
import static io.everytrade.server.model.TransactionType.REWARD;
import static io.everytrade.server.model.TransactionType.WITHDRAWAL;

public class BlockFiBeanV1Test {
    public static final String HEADER = "Cryptocurrency,Amount,Transaction Type,Confirmed At\n";

    @Test
    void testBuy() {
        final String row0 = """
            BTC,-0.01003332,Trade,2021-04-17 07:21:04
            ETH,0.25100000,Trade,2021-04-17 07:21:04""";
        final List<TransactionCluster> actual = ParserTestUtils.getTransactionClusters(HEADER + row0);

        final TransactionCluster expected = new TransactionCluster(
            new ImportedTransactionBean(
                null,
                Instant.parse("2021-04-17T07:21:04Z"),
                ETH,
                BTC,
                BUY,
                new BigDecimal("0.25100000"),
                new BigDecimal("0.03997338645418327"),
                null,
                null
            ),
            List.of()
        );

        TestUtils.testTxs(expected.getMain(), actual.get(0).getMain());
    }

    @Test
    void testEarn() {
        final String row0 = "ETH,0.00091574,Interest Payment,2022-10-31 23:59:59\n";
        final List<TransactionCluster> actual = ParserTestUtils.getTransactionClusters(HEADER + row0);

        final TransactionCluster expected = new TransactionCluster(
            new ImportedTransactionBean(
                null,
                Instant.parse("2022-10-31T23:59:59Z"),
                ETH,
                ETH,
                EARNING,
                new BigDecimal("0.00091574"),
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
        final String row0 = "BTC,0.05000000,Crypto Transfer,2021-03-13 23:41:09\n";
        final List<TransactionCluster> actual = ParserTestUtils.getTransactionClusters(HEADER + row0);

        final TransactionCluster expected = new TransactionCluster(
            new ImportedTransactionBean(
                null,
                Instant.parse("2021-03-13T23:41:09Z"),
                BTC,
                BTC,
                DEPOSIT,
                new BigDecimal("0.05000000"),
                null,
                null,
                null
            ),
            List.of()
        );

        TestUtils.testTxs(expected.getMain(), actual.get(0).getMain());
    }

    @Test
    void testWithdrawal() {
        final String row0 = "BTC,-0.05000000,Crypto Transfer,2021-03-13 23:41:09\n";
        final List<TransactionCluster> actual = ParserTestUtils.getTransactionClusters(HEADER + row0);

        final TransactionCluster expected = new TransactionCluster(
            new ImportedTransactionBean(
                null,
                Instant.parse("2021-03-13T23:41:09Z"),
                BTC,
                BTC,
                WITHDRAWAL,
                new BigDecimal("0.05000000"),
                null,
                null,
                null
            ),
            List.of()
        );

        TestUtils.testTxs(expected.getMain(), actual.get(0).getMain());
    }

    @Test
    void testReward() {
        final String row0 = "BTC,0.00017064,Referral Bonus,2021-04-09 23:59:59\n";
        final String row1 = "GUSD,2.31000000,Bonus Payment,2022-10-20 23:59:59\n";
        final List<TransactionCluster> actual = ParserTestUtils.getTransactionClusters(HEADER + row0.concat(row1));

        final TransactionCluster expected = new TransactionCluster(
            new ImportedTransactionBean(
                null,
                Instant.parse("2021-04-09T23:59:59Z"),
                BTC,
                BTC,
                REWARD,
                new BigDecimal("0.00017064"),
                null,
                null,
                null
            ),
            List.of()
        );

        TestUtils.testTxs(expected.getMain(), actual.get(0).getMain());
    }
}
