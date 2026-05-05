package io.everytrade.server.plugin.impl.everytrade;

import io.everytrade.server.model.TransactionType;
import io.everytrade.server.plugin.api.parser.FeeRebateImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.TransactionCluster;
import io.everytrade.server.plugin.impl.everytrade.helius.HeliusBalanceChangeDto;
import io.everytrade.server.plugin.impl.everytrade.helius.HeliusClient;
import io.everytrade.server.plugin.impl.everytrade.helius.HeliusPaginationDto;
import io.everytrade.server.plugin.impl.everytrade.helius.HeliusResponseDto;
import io.everytrade.server.plugin.impl.everytrade.helius.HeliusTransactionDto;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static io.everytrade.server.model.Currency.SOL;
import static io.everytrade.server.model.Currency.USD;
import static io.everytrade.server.model.TransactionType.BUY;
import static io.everytrade.server.model.TransactionType.DEPOSIT;
import static io.everytrade.server.model.TransactionType.SELL;
import static io.everytrade.server.model.TransactionType.WITHDRAWAL;
import static io.everytrade.server.parser.exchange.SolBlockchainTransaction.NATIVE_SOL_MINT;
import static io.everytrade.server.test.TestUtils.bigDecimalEquals;
import static io.everytrade.server.test.TestUtils.findOneCluster;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BlockchainSolDownloaderTest {

    private static final String ADDRESS = "GQPo4rWCpn5MrzgR3UpDbKVVwyjdeFriYu8taSAhTvVU";
    private static final String FIAT = USD.code();
    private static final long FEE_LAMPORTS = 5000L;
    private static final BigDecimal FEE_SOL = new BigDecimal("0.000005000");

    @Test
    void solDepositWithdrawalWithoutFeesTest() throws Exception {
        var txs = List.of(
            solTx("sig1", new BigDecimal("1.5"), FEE_LAMPORTS),
            solTx("sig2", new BigDecimal("-0.5"), FEE_LAMPORTS)
        );
        var downloader = downloader(false, false, false, false, mockClient(txs));

        var result = downloader.download(null);

        var depositCluster = findOneCluster(result, DEPOSIT);
        assertEquals(0, depositCluster.getRelated().size());
        assertDepositWithdrawal(depositCluster, DEPOSIT, new BigDecimal("1.5"));

        var withdrawalCluster = findOneCluster(result, WITHDRAWAL);
        assertEquals(0, withdrawalCluster.getRelated().size());
        assertDepositWithdrawal(withdrawalCluster, WITHDRAWAL, new BigDecimal("0.5"));
    }

    @Test
    void solDepositWithdrawalWithFeesTest() throws Exception {
        var txs = List.of(
            solTx("sig1", new BigDecimal("1.5"), FEE_LAMPORTS),
            solTx("sig2", new BigDecimal("-0.5"), FEE_LAMPORTS)
        );
        var downloader = downloader(false, false, true, true, mockClient(txs));

        var result = downloader.download(null);

        var depositCluster = findOneCluster(result, DEPOSIT);
        assertDepositWithdrawal(depositCluster, DEPOSIT, new BigDecimal("1.5"));
        assertFee(depositCluster, FEE_SOL);

        var withdrawalCluster = findOneCluster(result, WITHDRAWAL);
        assertDepositWithdrawal(withdrawalCluster, WITHDRAWAL, new BigDecimal("0.5"));
        assertFee(withdrawalCluster, FEE_SOL);
    }

    @Test
    void solBuySellWithoutFeesTest() throws Exception {
        var txs = List.of(
            solTx("sig1", new BigDecimal("1.5"), FEE_LAMPORTS),
            solTx("sig2", new BigDecimal("-0.5"), FEE_LAMPORTS)
        );
        var downloader = downloader(true, true, false, false, mockClient(txs));

        var result = downloader.download(null);

        var buyCluster = findOneCluster(result, BUY);
        assertEquals(0, buyCluster.getRelated().size());
        assertBuySell(buyCluster, BUY, new BigDecimal("1.5"));

        var sellCluster = findOneCluster(result, SELL);
        assertEquals(0, sellCluster.getRelated().size());
        assertBuySell(sellCluster, SELL, new BigDecimal("0.5"));
    }

    @Test
    void solBuySellWithFeesTest() throws Exception {
        var txs = List.of(
            solTx("sig1", new BigDecimal("1.5"), FEE_LAMPORTS),
            solTx("sig2", new BigDecimal("-0.5"), FEE_LAMPORTS)
        );
        var downloader = downloader(true, true, true, true, mockClient(txs));

        var result = downloader.download(null);

        var buyCluster = findOneCluster(result, BUY);
        assertBuySell(buyCluster, BUY, new BigDecimal("1.5"));
        assertFee(buyCluster, FEE_SOL);

        var sellCluster = findOneCluster(result, SELL);
        assertBuySell(sellCluster, SELL, new BigDecimal("0.5"));
        assertFee(sellCluster, FEE_SOL);
    }

    @Test
    void downloadStateContainsNewestSignatureTest() throws Exception {
        var txs = List.of(
            solTx("sig-newest", new BigDecimal("1.0"), FEE_LAMPORTS),
            solTx("sig-older", new BigDecimal("0.5"), FEE_LAMPORTS)
        );
        var downloader = downloader(false, false, false, false, mockClient(txs));

        var result = downloader.download(null);

        assertEquals("sig-newest", result.getDownloadStateData());
    }

    @Test
    void incrementalDownloadAdvancesStateTest() throws Exception {
        var txs = List.of(
            solTx("sig-new1", new BigDecimal("1.0"), FEE_LAMPORTS),
            solTx("sig-new2", new BigDecimal("-0.3"), FEE_LAMPORTS),
            solTx("sig-old", new BigDecimal("2.0"), FEE_LAMPORTS),    // last known
            solTx("sig-very-old", new BigDecimal("3.0"), FEE_LAMPORTS) // already imported
        );
        var downloader = downloader(false, false, false, false, mockClient(txs));

        var result = downloader.download("sig-old");

        assertEquals(2, result.getParseResult().getTransactionClusters().size());
        assertEquals("sig-new1", result.getDownloadStateData());
    }

    @Test
    void errorTransactionIsIgnoredTest() throws Exception {
        var errorTx = HeliusTransactionDto.builder()
            .signature("sig-error")
            .timestamp(Instant.now().getEpochSecond())
            .fee(FEE_LAMPORTS)
            .error("Transaction failed")
            .balanceChanges(List.of(solBalanceChange(new BigDecimal("1.0"))))
            .build();
        var downloader = downloader(false, false, false, false, mockClient(List.of(errorTx)));

        var result = downloader.download(null);

        assertEquals(0, result.getParseResult().getTransactionClusters().size());
        assertEquals(1, result.getParseResult().getParsingProblems().size());
    }

    @Test
    void noSolBalanceChangeIsIgnoredTest() throws Exception {
        var splOnlyTx = HeliusTransactionDto.builder()
            .signature("sig-spl")
            .timestamp(Instant.now().getEpochSecond())
            .fee(FEE_LAMPORTS)
            .balanceChanges(List.of(
                HeliusBalanceChangeDto.builder()
                    .mint("SomeSplTokenMint1111111111111111111111111")
                    .amount(new BigDecimal("100.0"))
                    .build()
            ))
            .build();
        var downloader = downloader(false, false, false, false, mockClient(List.of(splOnlyTx)));

        var result = downloader.download(null);

        assertEquals(0, result.getParseResult().getTransactionClusters().size());
        assertEquals(1, result.getParseResult().getParsingProblems().size());
    }

    @Test
    void nullBalanceChangesIsIgnoredTest() throws Exception {
        var nullChangesTx = HeliusTransactionDto.builder()
            .signature("sig-null")
            .timestamp(Instant.now().getEpochSecond())
            .fee(FEE_LAMPORTS)
            .balanceChanges(null)
            .build();
        var downloader = downloader(false, false, false, false, mockClient(List.of(nullChangesTx)));

        var result = downloader.download(null);

        assertEquals(0, result.getParseResult().getTransactionClusters().size());
        assertEquals(1, result.getParseResult().getParsingProblems().size());
    }

    private void assertDepositWithdrawal(TransactionCluster cluster, TransactionType type, BigDecimal amount) {
        assertEquals(0, cluster.getIgnoredFeeTransactionCount());
        assertNull(cluster.getIgnoredFeeReason());
        var tx = cluster.getMain();
        bigDecimalEquals(amount, tx.getVolume());
        assertNotNull(tx.getUid());
        assertNotNull(tx.getExecuted());
        assertEquals(SOL, tx.getBase());
        assertEquals(SOL, tx.getQuote());
        assertEquals(type, tx.getAction());
    }

    private void assertBuySell(TransactionCluster cluster, TransactionType type, BigDecimal amount) {
        assertEquals(0, cluster.getIgnoredFeeTransactionCount());
        assertNull(cluster.getIgnoredFeeReason());
        var tx = cluster.getMain();
        bigDecimalEquals(amount, tx.getVolume());
        assertNotNull(tx.getUid());
        assertNotNull(tx.getExecuted());
        assertEquals(SOL, tx.getBase());
        assertEquals(USD, tx.getQuote());
        assertEquals(type, tx.getAction());
    }

    private void assertFee(TransactionCluster cluster, BigDecimal expectedFee) {
        assertEquals(1, cluster.getRelated().size());
        var fee = (FeeRebateImportedTransactionBean) cluster.getRelated().get(0);
        bigDecimalEquals(expectedFee, fee.getVolume());
        assertEquals(SOL, fee.getFeeRebateCurrency());
        assertNotNull(fee.getUid());
        assertNotNull(fee.getExecuted());
    }

    private HeliusClient mockClient(List<HeliusTransactionDto> txs) throws Exception {
        var mockApi = mock(HeliusClient.class);
        when(mockApi.getTransactionHistory(anyString(), anyString(), anyInt(), isNull()))
            .thenReturn(response(txs, false));
        when(mockApi.getTransactionHistory(anyString(), anyString(), anyInt(), anyString()))
            .thenReturn(response(List.of(), false));
        return mockApi;
    }

    private HeliusResponseDto response(List<HeliusTransactionDto> txs, boolean hasMore) {
        return HeliusResponseDto.builder()
            .data(txs)
            .pagination(HeliusPaginationDto.builder().hasMore(hasMore).build())
            .build();
    }

    private BlockchainSolDownloader downloader(
        boolean importDepositsAsBuys,
        boolean importWithdrawalsAsSells,
        boolean importFeesFromDeposits,
        boolean importFeesFromWithdrawals,
        HeliusClient client
    ) {
        return new BlockchainSolDownloader(
            ADDRESS, "test-api-key", FIAT,
            importDepositsAsBuys, importWithdrawalsAsSells,
            importFeesFromDeposits, importFeesFromWithdrawals,
            client
        );
    }

    private HeliusTransactionDto solTx(String signature, BigDecimal solAmount, long feeLamports) {
        return HeliusTransactionDto.builder()
            .signature(signature)
            .timestamp(Instant.now().getEpochSecond())
            .fee(feeLamports)
            .balanceChanges(List.of(solBalanceChange(solAmount)))
            .build();
    }

    private HeliusBalanceChangeDto solBalanceChange(BigDecimal amount) {
        return HeliusBalanceChangeDto.builder()
            .mint(NATIVE_SOL_MINT)
            .amount(amount)
            .build();
    }
}
