package io.everytrade.server.plugin.impl.everytrade;

import io.everytrade.server.model.Currency;
import io.everytrade.server.model.TransactionType;
import io.everytrade.server.plugin.api.connector.DownloadResult;
import io.everytrade.server.plugin.api.parser.FeeRebateImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.ParsingProblemType;
import io.everytrade.server.plugin.api.parser.TransactionCluster;
import io.everytrade.server.plugin.impl.everytrade.etherscan.EtherScanClient;
import io.everytrade.server.plugin.impl.everytrade.etherscan.EtherScanDto;
import io.everytrade.server.plugin.impl.everytrade.etherscan.EtherScanErc20TransactionDto;
import io.everytrade.server.plugin.impl.everytrade.etherscan.EtherScanTransactionDto;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
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
    /** {importFeesFromDeposits, importFeesFromWithdrawals}: every combination a saved connection can carry. */
    private static final boolean[][] FEE_TOGGLES = {{false, false}, {false, true}, {true, false}, {true, true}};

    /**
     * Owner decision 2026-10-20 (final): the outgoing leg the wallet sent carries its gas with importFeesFromWithdrawals
     * on, the incoming one carries the sender's gas only with importFeesFromDeposits on - for every combination.
     */
    @Test
    void ethBuySellCarryTheGasTheFeeOptionsAskForTest() throws Exception {
        for (boolean[] toggles : FEE_TOGGLES) {
            var txs = List.of(
                depositOnAddress(ADDRESS, ONE_ETH.multiply(TEN)),
                withdrawFromAddress(ADDRESS, ONE_ETH)
            );
            var downloader = downloader(true, true, toggles[0], toggles[1], mockClient(txs, emptyList()));

            DownloadResult result = downloader.download(null);

            var buyCluster = findOneCluster(result, BUY);
            assertBuySell(buyCluster, BUY, TEN);
            assertFeesIf(toggles[0], buyCluster);

            var sellCluster = findOneCluster(result, SELL);
            assertBuySell(sellCluster, SELL, ONE);
            assertFeesIf(toggles[1], sellCluster);
        }
    }

    @Test
    void ethDepositWithdrawalCarryTheGasTheFeeOptionsAskForTest() throws Exception {
        for (boolean[] toggles : FEE_TOGGLES) {
            var txs = List.of(
                depositOnAddress(ADDRESS, ONE_ETH.multiply(TEN)),
                withdrawFromAddress(ADDRESS, ONE_ETH)
            );
            var downloader = downloader(false, false, toggles[0], toggles[1], mockClient(txs, emptyList()));

            DownloadResult result = downloader.download(null);

            var depositCluster = findOneCluster(result, DEPOSIT);
            assertDepositWithdrawal(depositCluster, DEPOSIT, TEN);
            assertFeesIf(toggles[0], depositCluster);

            var withdrawalCluster = findOneCluster(result, WITHDRAWAL);
            assertDepositWithdrawal(withdrawalCluster, WITHDRAWAL, ONE);
            assertFeesIf(toggles[1], withdrawalCluster);
        }
    }

    @Test
    void fullPageTriggersSupplementaryLastBlockFetchTest() throws Exception {
        // A full page must trigger the supplementary last-block fetch, else txs past the 1000-record cap are dropped.
        final int pageSize = 1000;
        List<EtherScanTransactionDto> fullPage = new ArrayList<>();
        for (int i = 0; i < pageSize; i++) {
            fullPage.add(depositOnAddress(ADDRESS, ONE_ETH));
        }
        // the whole page sits in one block; the re-read of that block answers every row of it, the spilled-over one too
        var spilledOverTx = depositOnAddress(ADDRESS, ONE_ETH);
        List<EtherScanTransactionDto> lastBlockTxs = new ArrayList<>(fullPage);
        lastBlockTxs.add(spilledOverTx);

        var mock = mock(EtherScanClient.class);
        when(mock.getBlockNumberByTimestamp(anyString(), anyString(), anyString())).thenReturn(successResponse(1_000_000L));
        when(mock.getNormalTxsByAddress(anyString(), anyLong(), anyLong(), anyInt(), anyInt(), anyString(), anyString()))
            .thenReturn(successResponse(fullPage))
            .thenReturn(successResponse(lastBlockTxs));

        var downloader = new BlockchainEthDownloader(
            ADDRESS, "apiKey", FIAT, false, false, true, false, mock);

        DownloadResult result = downloader.download(null);

        // full page + the spilled-over tx: the rows of the last block are REPLACED by the re-read, never doubled
        assertEquals(pageSize + 1, result.getParseResult().getTransactionClusters().size());
    }

    @Test
    void erc20UnknownTokenSymbolIsIgnoredNotFailed() throws Exception {
        // ERC20 token with a homoglyph symbol mapping to no known Currency must be ignored, not failed.
        var homoglyphUsdc = "ÚЅDС";
        var erc20Txs = List.of(erc20DepositOnAddress(ADDRESS, homoglyphUsdc, ONE_ETH));

        var downloader = new BlockchainEthDownloader(
            ADDRESS,
            "apiKey",
            FIAT,
            false,
            false,
            false,
            true,
            mockClient(emptyList(), erc20Txs)
        );

        DownloadResult result = downloader.download(null);

        var parseResult = result.getParseResult();
        assertEquals(0, parseResult.getTransactionClusters().size());
        assertEquals(1, parseResult.getParsingProblems().size());
        assertEquals(
            ParsingProblemType.PARSED_ROW_IGNORED,
            parseResult.getParsingProblems().get(0).getParsingProblemType()
        );
    }

    private BlockchainEthDownloader downloader(boolean depositsAsBuys, boolean withdrawalsAsSells,
                                               boolean feesFromDeposits, boolean feesFromWithdrawals, EtherScanClient client) {
        return new BlockchainEthDownloader(ADDRESS, "apiKey", FIAT, depositsAsBuys, withdrawalsAsSells, feesFromDeposits,
            feesFromWithdrawals, true, true, client);
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

    private void assertFeesIf(boolean expected, TransactionCluster cluster) {
        if (expected) {
            assertFees(cluster);
        } else {
            assertEquals(0, cluster.getRelated().size(), "" + cluster.getRelated());
        }
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

    private EtherScanErc20TransactionDto erc20DepositOnAddress(String toAddress, String tokenSymbol, BigDecimal value) {
        return EtherScanErc20TransactionDto.builder()
            .blockNumber(100_000)
            .timeStamp(Instant.now().getEpochSecond())
            .hash(UUID.randomUUID().toString())
            .nonce(0)
            .blockHash(UUID.randomUUID().toString())
            .transactionIndex(0)
            .from(UUID.randomUUID().toString())
            .to(toAddress)
            .value(value)
            .gas(new BigDecimal("1000000"))
            .gasUsed(new BigDecimal("1000000"))
            .gasPrice(ONE)
            .txreceipt_status("")
            .input("")
            .contractAddress(null)
            .confirmations(100)
            .isError(0)
            .tokenName("Fake USD Coin")
            .tokenSymbol(tokenSymbol)
            .tokenDecimal(6)
            .build();
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
            .txreceipt_status("")
            .input("")
            .contractAddress(null)
            .confirmations(100)
            .isError(0)
            .build();
    }
}
