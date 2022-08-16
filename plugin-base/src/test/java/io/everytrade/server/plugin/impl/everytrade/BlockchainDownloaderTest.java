package io.everytrade.server.plugin.impl.everytrade;

import com.generalbytes.bitrafael.client.Client;
import com.generalbytes.bitrafael.server.api.dto.AddressInfo;
import com.generalbytes.bitrafael.server.api.dto.InputInfo;
import com.generalbytes.bitrafael.server.api.dto.OutputInfo;
import com.generalbytes.bitrafael.server.api.dto.TxInfo;
import io.everytrade.server.model.Currency;
import io.everytrade.server.model.TransactionType;
import io.everytrade.server.plugin.api.connector.DownloadResult;
import io.everytrade.server.plugin.api.parser.FeeRebateImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.TransactionCluster;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static io.everytrade.server.model.TransactionType.BUY;
import static io.everytrade.server.model.TransactionType.DEPOSIT;
import static io.everytrade.server.model.TransactionType.SELL;
import static io.everytrade.server.model.TransactionType.WITHDRAWAL;
import static io.everytrade.server.test.TestUtils.bigDecimalEquals;
import static io.everytrade.server.test.TestUtils.findOneCluster;
import static java.time.Instant.now;
import static java.util.Collections.emptySet;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphanumeric;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BlockchainDownloaderTest {

    private static final String BTC = "BTC";
    private static final String FIAT = "USD";
    private static final String ADDRESS = "addr1";

    @Test
    void btcBuySellWithoutFeesTest() {
        List<TxInfo> txs = List.of(
            depositOnAddress(ADDRESS, 1_000_000),
            withdrawalFromAddress(ADDRESS, 100_000)
        );

        var blockchainDownloader = new BlockchainDownloader(
            mockClient(txs),
            null,
            0,
            emptySet(),
            FIAT,
            BTC,
            true,
            true,
            false,
            false,
            0,
            300
        );

        DownloadResult result = blockchainDownloader.download(ADDRESS);

        var buyCluster = findOneCluster(result, BUY);
        assertEquals(0, buyCluster.getRelated().size());
        assertBuySell(buyCluster, BUY, new BigDecimal("0.009999"));

        var sellCluster = findOneCluster(result, SELL);
        assertEquals(0, sellCluster.getRelated().size());
        assertBuySell(sellCluster, SELL, new BigDecimal("0.000999"));
    }

    @Test
    void btcBuySellWithFeesTest() {
        List<TxInfo> txs = List.of(
            depositOnAddress(ADDRESS, 1_000_000),
            withdrawalFromAddress(ADDRESS, 100_000)
        );

        var blockchainDownloader = new BlockchainDownloader(
            mockClient(txs),
            null,
            0,
            emptySet(),
            FIAT,
            BTC,
            true,
            true,
            true,
            true,
            0,
            300
        );

        DownloadResult result = blockchainDownloader.download(ADDRESS);

        var buyCluster = findOneCluster(result, BUY);
        assertBuySell(buyCluster, BUY, new BigDecimal("0.009999"));
        assertFees(buyCluster);

        var sellCluster = findOneCluster(result, SELL);
        assertBuySell(sellCluster, SELL, new BigDecimal("0.000999"));
        assertFees(sellCluster);
    }

    @Test
    void btcDepositWithdrawalWithoutFeesTest() {
        List<TxInfo> txs = List.of(
            depositOnAddress(ADDRESS, 1_000_000),
            withdrawalFromAddress(ADDRESS, 100_000)
        );

        var blockchainDownloader = new BlockchainDownloader(
            mockClient(txs),
            null,
            0,
            emptySet(),
            FIAT,
            BTC,
            false,
            false,
            false,
            false,
            0,
            300
        );

        DownloadResult result = blockchainDownloader.download(ADDRESS);

        var depositCluster = findOneCluster(result, DEPOSIT);
        assertEquals(0, depositCluster.getRelated().size());
        assertDepositWithdrawal(depositCluster, DEPOSIT, new BigDecimal("0.009999"));

        var withdrawalCluster = findOneCluster(result, WITHDRAWAL);
        assertEquals(0, withdrawalCluster.getRelated().size());
        assertDepositWithdrawal(withdrawalCluster, WITHDRAWAL, new BigDecimal("0.000999"));
    }

    @Test
    void btcDepositWithdrawalWithFeesTest() {
        List<TxInfo> txs = List.of(
            depositOnAddress(ADDRESS, 1_000_000),
            withdrawalFromAddress(ADDRESS, 100_000)
        );

        var blockchainDownloader = new BlockchainDownloader(
            mockClient(txs),
            null,
            0,
            emptySet(),
            FIAT,
            BTC,
            false,
            false,
            true,
            true,
            0,
            300
        );

        DownloadResult result = blockchainDownloader.download(ADDRESS);

        var depositCluster = findOneCluster(result, DEPOSIT);
        assertEquals(1, depositCluster.getRelated().size());
        assertDepositWithdrawal(depositCluster, DEPOSIT, new BigDecimal("0.009999"));

        var withdrawalCluster = findOneCluster(result, WITHDRAWAL);
        assertEquals(1, withdrawalCluster.getRelated().size());
        assertDepositWithdrawal(withdrawalCluster, WITHDRAWAL, new BigDecimal("0.000999"));
    }

    private void assertDepositWithdrawal(TransactionCluster cluster, TransactionType type, BigDecimal volume) {
        assertEquals(0, cluster.getIgnoredFeeTransactionCount());
        assertNull(cluster.getIgnoredFeeReason());
        var tx = cluster.getMain();
        assertEquals(volume, tx.getVolume());
        assertNotNull(tx.getAddress());
        assertNotNull(tx.getUid());
        assertNotNull(tx.getExecuted());
        assertEquals(Currency.BTC, tx.getBase());
        assertEquals(Currency.USD, tx.getQuote());
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
        assertEquals(Currency.BTC, tx.getBase());
        assertEquals(Currency.USD, tx.getQuote());
        assertEquals(type, tx.getAction());
        assertNotNull(tx.getImported());
    }

    private void assertFees(TransactionCluster cluster) {
        assertEquals(1, cluster.getRelated().size());
        var fee = (FeeRebateImportedTransactionBean) cluster.getRelated().get(0);
        assertNotNull(fee.getVolume());
        assertEquals(Currency.BTC, fee.getFeeRebateCurrency());
        assertNotNull(fee.getUid());
        assertNotNull(fee.getExecuted());
        assertNotNull(fee.getAction());
        assertNotNull(fee.getImported());
    }

    private Client mockClient(List<TxInfo> txs) {
        var clientMock = mock(Client.class);

        when(clientMock.getAddressInfo(anyString(), anyInt())).thenReturn(mockResponse(ADDRESS, txs));
        when(clientMock.getAddressInfo(anyString(), anyInt(), anyInt(), anyInt())).thenReturn(mockResponse(ADDRESS, txs));
        return clientMock;
    }

    private AddressInfo mockResponse(String address, List<TxInfo> txs) {
        var response = new AddressInfo();
        response.setAddress(address);
        response.setFinalBalance(10000000);
        response.setTotalReceived(10000000);
        response.setTotalSent(10000000);
        response.setNumberOfTransactions(txs.size());

        txs.forEach(response::addTXInfo);
        return response;
    }

    private TxInfo depositOnAddress(String address, long value) {
        var tx = emptyTxInfo();
        tx.addInputInfo(inputInfo(0,  randomAlphanumeric(32), value));
        tx.addOutputInfo(outputInfo(0, address, value - 100));
        return tx;
    }

    private TxInfo withdrawalFromAddress(String address, long value) {
        var tx = emptyTxInfo();
        tx.addInputInfo(inputInfo(0,  address, value - 100));
        tx.addOutputInfo(outputInfo(0, randomAlphanumeric(32), value));
        return tx;
    }

    private TxInfo emptyTxInfo() {
        long time = now().toEpochMilli();
        var txInfo = new TxInfo("txHAsh", "blockHash", time, time, 300);
        txInfo.setConfirmations(10);
        txInfo.setBlockHeight(10);
        return txInfo;
    }

    private InputInfo inputInfo(int i, String address, long value) {
        var info = new InputInfo();
        info.setAddress(address);
        info.setIndex(i);
        info.setValue(value);
        info.setTxHash("txHash");
        return info;
    }

    private OutputInfo outputInfo(int i, String address, long value) {
        var info = new OutputInfo();
        info.setAddress(address);
        info.setIndex(i);
        info.setValue(value);
        info.setTxHash("txHash");
        return info;
    }
}
