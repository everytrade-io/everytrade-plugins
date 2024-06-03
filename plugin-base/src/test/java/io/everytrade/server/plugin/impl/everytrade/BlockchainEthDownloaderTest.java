package io.everytrade.server.plugin.impl.everytrade;

import io.everytrade.server.model.Currency;
import io.everytrade.server.model.TransactionType;
import io.everytrade.server.plugin.api.connector.DownloadResult;
import io.everytrade.server.plugin.api.parser.FeeRebateImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.TransactionCluster;
import io.everytrade.server.plugin.impl.everytrade.etherscan.EtherScanClient;
import io.everytrade.server.plugin.impl.everytrade.etherscan.EtherScanDto;
import io.everytrade.server.plugin.impl.everytrade.etherscan.EtherScanErc20TransactionDto;
import io.everytrade.server.plugin.impl.everytrade.etherscan.EtherScanTransactionDto;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static io.everytrade.server.model.Currency.ETH;
import static io.everytrade.server.model.Currency.USD;
import static io.everytrade.server.model.TransactionType.BUY;
import static io.everytrade.server.model.TransactionType.DEPOSIT;
import static io.everytrade.server.model.TransactionType.SELL;
import static io.everytrade.server.model.TransactionType.WITHDRAWAL;
import static io.everytrade.server.test.TestUtils.bigDecimalEquals;
import static io.everytrade.server.test.TestUtils.findOneCluster;
import static java.math.BigDecimal.ONE;
import static java.math.BigDecimal.TEN;
import static java.util.Collections.emptyList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BlockchainEthDownloaderTest {

    private static final String ADDRESS = "address0";
    private static final String FIAT = USD.code();
    private static final BigDecimal ONE_ETH = new BigDecimal(1000000000000000000L);

    @Test
    void ethBuySellWithoutFeesTest() throws Exception {
        var txs = List.of(
            depositOnAddress(ADDRESS, ONE_ETH.multiply(TEN)),
            withdrawFromAddress(ADDRESS, ONE_ETH)
        );

        var downloader = new BlockchainEthDownloader(
            ADDRESS,
            "apiKey",
            FIAT,
            true,
            true,
            false,
            false,
            mockClient(txs, emptyList())
        );

        DownloadResult result = downloader.download(null);

        var buyCluster = findOneCluster(result, BUY);
        assertEquals(0, buyCluster.getRelated().size());
        assertBuySell(buyCluster, BUY, TEN);

        var sellCluster = findOneCluster(result, SELL);
        assertEquals(0, sellCluster.getRelated().size());
        assertBuySell(sellCluster, SELL, ONE);
    }

    @Test
    void ethBuySellWithFeesTest() throws Exception {
        var txs = List.of(
            depositOnAddress(ADDRESS, ONE_ETH.multiply(TEN)),
            withdrawFromAddress(ADDRESS, ONE_ETH)
        );

        var downloader = new BlockchainEthDownloader(
            ADDRESS,
            "apiKey",
            FIAT,
            true,
            true,
            true,
            true,
            mockClient(txs, emptyList())
        );

        DownloadResult result = downloader.download(null);

        var buyCluster = findOneCluster(result, BUY);
        assertBuySell(buyCluster, BUY, TEN);
        assertFees(buyCluster);

        var sellCluster = findOneCluster(result, SELL);
        assertBuySell(sellCluster, SELL, ONE);
        assertFees(sellCluster);
    }

    @Test
    void ethDepositWithdrawalWithoutFeesTest() throws Exception {
        var txs = List.of(
            depositOnAddress(ADDRESS, ONE_ETH.multiply(TEN)),
            withdrawFromAddress(ADDRESS, ONE_ETH)
        );

        var downloader = new BlockchainEthDownloader(
            ADDRESS,
            "apiKey",
            FIAT,
            false,
            false,
            false,
            false,
            mockClient(txs, emptyList())
        );

        DownloadResult result = downloader.download(null);

        var depositCluster = findOneCluster(result, DEPOSIT);
        assertEquals(0, depositCluster.getRelated().size());
        assertDepositWithdrawal(depositCluster, DEPOSIT, TEN);

        var withdrawalCluster = findOneCluster(result, WITHDRAWAL);
        assertEquals(0, withdrawalCluster.getRelated().size());
        assertDepositWithdrawal(withdrawalCluster, WITHDRAWAL, ONE);
    }

    @Test
    void ethDepositWithdrawalWithFeesTest() throws Exception {
        var txs = List.of(
            depositOnAddress(ADDRESS, ONE_ETH.multiply(TEN)),
            withdrawFromAddress(ADDRESS, ONE_ETH)
        );

        var downloader = new BlockchainEthDownloader(
            ADDRESS,
            "apiKey",
            FIAT,
            false,
            false,
            true,
            true,
            mockClient(txs, emptyList())
        );

        DownloadResult result = downloader.download(null);

        var depositCluster = findOneCluster(result, DEPOSIT);
        assertDepositWithdrawal(depositCluster, DEPOSIT, TEN);
        assertFees(depositCluster);

        var withdrawalCluster = findOneCluster(result, WITHDRAWAL);
        assertDepositWithdrawal(withdrawalCluster, WITHDRAWAL, ONE);
        assertFees(withdrawalCluster);
    }

    private void assertDepositWithdrawal(TransactionCluster cluster, TransactionType type, BigDecimal volume) {
        assertEquals(0, cluster.getIgnoredFeeTransactionCount());
        assertNull(cluster.getIgnoredFeeReason());
        var tx = cluster.getMain();
        bigDecimalEquals(volume, tx.getVolume());
        assertNotNull(tx.getAddress());
        assertNotNull(tx.getUid());
        assertNotNull(tx.getExecuted());
        assertEquals(ETH, tx.getBase());
        assertEquals(ETH, tx.getQuote());
        assertEquals(type, tx.getAction());
        assertNotNull(tx.getImported());
    }

    private void assertBuySell(TransactionCluster cluster, TransactionType type, BigDecimal volume) {
        assertEquals(0, cluster.getIgnoredFeeTransactionCount());
        assertNull(cluster.getIgnoredFeeReason());
        var tx = cluster.getMain();
        bigDecimalEquals(volume, tx.getVolume());
        assertNotNull(tx.getUid());
        assertNotNull(tx.getExecuted());
        assertEquals(Currency.ETH, tx.getBase());
        assertEquals(USD, tx.getQuote());
        assertEquals(type, tx.getAction());
        assertNotNull(tx.getImported());
    }

    private void assertFees(TransactionCluster cluster) {
        assertEquals(1, cluster.getRelated().size());
        var fee = (FeeRebateImportedTransactionBean) cluster.getRelated().get(0);
        assertNotNull(fee.getVolume());
        assertEquals(Currency.ETH, fee.getFeeRebateCurrency());
        assertNotNull(fee.getUid());
        assertNotNull(fee.getExecuted());
        assertNotNull(fee.getAction());
        assertNotNull(fee.getImported());
    }

    private EtherScanClient mockClient(List<EtherScanTransactionDto> txs, List<EtherScanErc20TransactionDto> erc20Txs) throws Exception {
        var mock = mock(EtherScanClient.class);

        when(mock.getBlockNumberByTimestamp(anyString(), anyString(), anyString())).thenReturn(successResponse(1_000_000L));
        when(
            mock
                .getNormalTxsByAddress(anyString(), anyLong(), anyLong(), anyInt(), anyInt(), anyString(), anyString()))
                .thenReturn(successResponse(txs))
                .thenReturn(successResponse(emptyList())
        ); // return empty list in second call to stop downloading

        when(
            mock
                .getErc20TxsByAddress(anyString(), isNull(), anyLong(), anyLong(), anyInt(), anyInt(), anyString(), anyString()))
            .thenReturn(successResponse(erc20Txs))
            .thenReturn(successResponse(emptyList())
        ); // return empty list in second call to stop downloading
        return mock;
    }

    private <T> EtherScanDto<T> successResponse(T val) {
        return EtherScanDto.<T>builder()
            .message(null)
            .status("success")
            .result(val)
            .build();
    }

    private EtherScanTransactionDto depositOnAddress(String address, BigDecimal value) {
        return tx(UUID.randomUUID().toString(), address, value);
    }

    private EtherScanTransactionDto withdrawFromAddress(String address, BigDecimal value) {
        return tx(address, UUID.randomUUID().toString(), value);
    }

    private EtherScanTransactionDto tx(String fromA, String toA, BigDecimal value) {
        return EtherScanTransactionDto.builder()
            .blockNumber(100_000)
            .timeStamp(Instant.now().getEpochSecond())
            .hash(UUID.randomUUID().toString())
            .nonce(0)
            .blockHash(UUID.randomUUID().toString())
            .transactionIndex(0)
            .from(fromA)
            .to(toA)
            .value(value)
            .gas(new BigDecimal("1000000"))
            .gasUsed(new BigDecimal("1000000"))
            .gasPrice(ONE)
            .txreceiptStatus("")
            .input("")
            .contractAddress(null)
            .confirmations(100)
            .isError(0)
            .build();
    }
}
