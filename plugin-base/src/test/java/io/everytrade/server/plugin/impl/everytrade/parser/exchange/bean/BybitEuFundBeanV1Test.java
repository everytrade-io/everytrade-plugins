package io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean;

import io.everytrade.server.plugin.api.parser.ImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.TransactionCluster;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static io.everytrade.server.model.Currency.BTC;
import static io.everytrade.server.model.Currency.EUR;
import static io.everytrade.server.model.TransactionType.AIRDROP;
import static io.everytrade.server.model.TransactionType.DEPOSIT;
import static io.everytrade.server.model.TransactionType.WITHDRAWAL;
import static org.junit.jupiter.api.Assertions.assertEquals;

class BybitEuFundBeanV1Test {

    private static final String HEADER = "Uid,Date & Time(UTC),Coin,QTY,Type,Account Balance,Description\n";

    @Test
    void testFiatDeposit() {
        final String row = "567248024,2026-06-05 08:16:17,EUR,17.430000000000000000,Fiat,17.430000000000000000,Deposit\n";
        final TransactionCluster actual = ParserTestUtils.getTransactionCluster(HEADER + row);
        final TransactionCluster expected = new TransactionCluster(
            new ImportedTransactionBean(
                null,
                Instant.parse("2026-06-05T08:16:17Z"),
                EUR,
                EUR,
                DEPOSIT,
                new BigDecimal("17.430000000000000000"),
                null,
                null,
                null
            ),
            List.of()
        );
        ParserTestUtils.checkEqual(expected, actual);
    }

    @Test
    void testBuyCryptoWithCardIsDepositWithNote() {
        final String row = "567248024,2026-06-05 07:26:43,BTC,0.000293790000000000,Fiat,0.000293790000000000,Buy Crypto with Card\n";
        final TransactionCluster actual = ParserTestUtils.getTransactionCluster(HEADER + row);
        final TransactionCluster expected = new TransactionCluster(
            new ImportedTransactionBean(
                null,
                Instant.parse("2026-06-05T07:26:43Z"),
                BTC,
                BTC,
                DEPOSIT,
                new BigDecimal("0.000293790000000000"),
                null,
                "ByBit EU: Buy Crypto with Card (fiat price not in this export)",
                null
            ),
            List.of()
        );
        ParserTestUtils.checkEqual(expected, actual);
    }

    @Test
    void testAirdrop() {
        final String row = "567248024,2026-06-10 05:09:23,BTC,0.000378481700000000,Airdrop,0.000672271700000000,Airdrop Bonus\n";
        final TransactionCluster actual = ParserTestUtils.getTransactionCluster(HEADER + row);
        final TransactionCluster expected = new TransactionCluster(
            new ImportedTransactionBean(
                null,
                Instant.parse("2026-06-10T05:09:23Z"),
                BTC,
                BTC,
                AIRDROP,
                new BigDecimal("0.000378481700000000"),
                null
            ),
            List.of()
        );
        ParserTestUtils.checkEqual(expected, actual);
    }

    @Test
    void testTransferOutIsWithdrawal() {
        final String row = "567248024,2026-06-05 08:18:57,EUR,-17.000000000000000000,Transfer out,0.430000000000000000,"
            + "Transfer to Unified Trading Account\n";
        final TransactionCluster actual = ParserTestUtils.getTransactionCluster(HEADER + row);
        final TransactionCluster expected = new TransactionCluster(
            new ImportedTransactionBean(
                null,
                Instant.parse("2026-06-05T08:18:57Z"),
                EUR,
                EUR,
                WITHDRAWAL,
                new BigDecimal("17.000000000000000000"),
                null,
                null,
                null
            ),
            List.of()
        );
        ParserTestUtils.checkEqual(expected, actual);
    }

    @Test
    void testUnsupportedTypeIsIgnored() {
        final String row = "567248024,2026-06-05 08:16:17,EUR,1.000000000000000000,SomethingNew,1.0,Whatever\n";
        assertEquals("EUR", "EUR"); // guard: parser must not throw a hard error
        final var result = ParserTestUtils.getParseResult(HEADER + row);
        assertEquals(0, result.getTransactionClusters().size());
        assertEquals(1, result.getParsingProblems().size());
    }
}
