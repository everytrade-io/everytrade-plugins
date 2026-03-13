package io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean;

import io.everytrade.server.model.Currency;
import io.everytrade.server.plugin.api.parser.ImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.TransactionCluster;
import io.everytrade.server.test.TestUtils;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static io.everytrade.server.model.Currency.BTC;
import static io.everytrade.server.model.Currency.USDT;
import static io.everytrade.server.model.Currency.XMR;
import static io.everytrade.server.model.TransactionType.DEPOSIT;
import static io.everytrade.server.model.TransactionType.WITHDRAWAL;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class KuCoinWithdrawalDepositTest {
    public static final String HEADER_V2 = "UID,Account Type,Time(UTC+02:00),Remarks,Status,Fee,Amount,Coin,Transfer Network\n";
    public static final String HEADER_V3 = "UID,Account Type,Currency,Side,Amount,Fee,Time(UTC+08:00),Remark,Type\n";
    public static final String HEADER_V3_UTC2 = "UID,Account Type,Currency,Side,Amount,Fee,Time(UTC+02:00),Remark,Type\n";

    @Test
    void testDeposit() {
        final String row0 = "****4228,mainAccount,2023-12-04 06:21:52,Deposit,SUCCESS,0,1497,USDT,TRX\n";
        final var actual = ParserTestUtils.getParseResult(HEADER_V2 + row0);

        final TransactionCluster expected = new TransactionCluster(
            new ImportedTransactionBean(
                "****4228",
                Instant.parse("2023-12-04T06:21:52Z"),
                USDT,
                USDT,
                DEPOSIT,
                new BigDecimal("1497"),
                null,
                null,
                null
            ),
            List.of()
        );

        TestUtils.testTxs(expected.getMain(), actual.getTransactionClusters().get(0).getMain());
    }

    @Test
    void testWithdrawal() {
        final String row0 = "****4228,mainAccount,2023-12-04 06:21:52,Withdrawal,SUCCESS,0,1497,USDT,TRX\n";
        final var actual = ParserTestUtils.getParseResult(HEADER_V2 + row0);

        final TransactionCluster expected = new TransactionCluster(
            new ImportedTransactionBean(
                "****4228",
                Instant.parse("2023-12-04T06:21:52Z"),
                USDT,
                USDT,
                WITHDRAWAL,
                new BigDecimal("1497"),
                null,
                null,
                null
            ),
            List.of()
        );

        TestUtils.testTxs(expected.getMain(), actual.getTransactionClusters().get(0).getMain());
    }

    @Test
    void testDepositV3() {
        final String row0 = "*****3866,mainAccount,XMR,Deposit,15.197539,0,2025-01-23 21:24:57,Deposit,Deposit\n";
        final var actual = ParserTestUtils.getParseResult(HEADER_V3 + row0);

        final TransactionCluster expected = new TransactionCluster(
            new ImportedTransactionBean(
                "*****3866",
                Instant.parse("2025-01-23T21:24:57Z"),
                XMR,
                XMR,
                DEPOSIT,
                new BigDecimal("15.197539"),
                null,
                null,
                null
            ),
            List.of()
        );

        TestUtils.testTxs(expected.getMain(), actual.getTransactionClusters().get(0).getMain());
    }

    @Test
    void testWithdrawalV3WithFee() {
        final String row0 = "*****3866,mainAccount,BTC,Withdrawal,0.030015,0.000015,2025-01-24 05:04:14,,Withdraw\n";
        final var actual = ParserTestUtils.getParseResult(HEADER_V3 + row0);

        final TransactionCluster expected = new TransactionCluster(
            new ImportedTransactionBean(
                "*****3866",
                Instant.parse("2025-01-24T05:04:14Z"),
                BTC,
                BTC,
                WITHDRAWAL,
                new BigDecimal("0.030015"),
                null,
                null,
                null
            ),
            List.of()
        );

        TestUtils.testTxs(expected.getMain(), actual.getTransactionClusters().get(0).getMain());
        assertEquals(1, actual.getTransactionClusters().get(0).getRelated().size());
    }

    @Test
    void testTransferV3() {
        final String row0 = "*****3866,mainAccount,XMR,Withdrawal,0.2,0,2025-01-23 20:36:07,Trading Account,Transfer\n";
        final var actual = ParserTestUtils.getParseResult(HEADER_V3 + row0);

        final TransactionCluster expected = new TransactionCluster(
            new ImportedTransactionBean(
                "*****3866",
                Instant.parse("2025-01-23T20:36:07Z"),
                XMR,
                XMR,
                WITHDRAWAL,
                new BigDecimal("0.2"),
                null,
                null,
                null
            ),
            List.of()
        );

        TestUtils.testTxs(expected.getMain(), actual.getTransactionClusters().get(0).getMain());
    }

    @Test
    void testDepositSpotV3() {
        final String row0 = "226751814,mainAccount,LDO,Deposit,13.3249,0,2025-01-01 01:02:11,,Spot\n";
        final var actual = ParserTestUtils.getParseResult(HEADER_V3_UTC2 + row0);

        final TransactionCluster expected = new TransactionCluster(
            new ImportedTransactionBean(
                "226751814",
                Instant.parse("2025-01-01T01:02:11Z"),
                Currency.fromCode("LDO"),
                Currency.fromCode("LDO"),
                DEPOSIT,
                new BigDecimal("13.3249"),
                null,
                null,
                null
            ),
            List.of()
        );

        TestUtils.testTxs(expected.getMain(), actual.getTransactionClusters().get(0).getMain());
    }

    @Test
    void testWithdrawalSpotV3WithFee() {
        final String row0 = "226751814,mainAccount,USDT,Withdrawal,23.34989650994,0.02332656994,2025-01-01 01:02:11,,Spot\n";
        final var actual = ParserTestUtils.getParseResult(HEADER_V3_UTC2 + row0);

        final TransactionCluster expected = new TransactionCluster(
            new ImportedTransactionBean(
                "226751814",
                Instant.parse("2025-01-01T01:02:11Z"),
                USDT,
                USDT,
                WITHDRAWAL,
                new BigDecimal("23.34989650994"),
                null,
                null,
                null
            ),
            List.of()
        );

        TestUtils.testTxs(expected.getMain(), actual.getTransactionClusters().get(0).getMain());
        assertEquals(1, actual.getTransactionClusters().get(0).getRelated().size());
    }
}
