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
import io.everytrade.server.plugin.api.parser.ImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.TransactionCluster;
import io.everytrade.server.test.TestUtils;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static io.everytrade.server.model.Currency.USD;
import static io.everytrade.server.model.TransactionType.BUY;
import static io.everytrade.server.model.TransactionType.DEPOSIT;
import static io.everytrade.server.model.TransactionType.FEE;
import static io.everytrade.server.model.TransactionType.SELL;
import static io.everytrade.server.model.TransactionType.WITHDRAWAL;
import static io.everytrade.server.test.TestUtils.bigDecimalEquals;
import static io.everytrade.server.test.TestUtils.findOneCluster;
import static java.time.Instant.now;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphanumeric;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BlockchainDownloaderTest {

    private static final String BTC = "BTC";
    private static final String FIAT = "USD";
    private static final String ADDRESS = "addr1";
    private static final String SOURCE = "MUMbouREUxpVs1DZMCVknq9HziM95zTAyZ";
    private static final String LTC = "LTC";

    @Test
    void blockChainBtcXpubTest() {
        List<TxInfo> txs = List.of();
        String address = "xpub6CLuyGaJwJngMH6H7v7NGV4jtjwN7JS7QNH6p9TJ2SPEVCvwSaeL9nm6y3zjvV5M4eKPJEzRHyiTLq2probsxzdyxEj2yb17HiEsBXbJXQc";
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
        DownloadResult actualResult = blockchainDownloader.download(address);
        var expected = new ImportedTransactionBean("301d1025e7704e94a7a505a74647a5ecd5b12ad8f66dc5cd6394fe2bf906d8d8",
            Instant.parse("2016-05-16T19:04:00Z"), Currency.BTC, USD, BUY, new BigDecimal("0.01"),
            null, null, null);

        assertEquals(7, actualResult.getParseResult().getTransactionClusters().size());
        TestUtils.testTxs(expected, actualResult.getParseResult().getTransactionClusters().get(0).getMain());
    }

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
        assertBuySell(sellCluster, SELL, new BigDecimal("0.000998"));
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
        assertBuySell(sellCluster, SELL, new BigDecimal("0.000998"));
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
        assertDepositWithdrawal(withdrawalCluster, WITHDRAWAL, new BigDecimal("0.000998"));
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
        assertDepositWithdrawal(withdrawalCluster, WITHDRAWAL, new BigDecimal("0.000998"));
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
        assertEquals(USD, tx.getQuote());
        assertEquals(type, tx.getAction());
        assertNotNull(tx.getImported());
    }

    private void assertTx(TransactionCluster expected, TransactionCluster actual) {
        var mainExTx = expected.getMain();
        var mainAcTx = actual.getMain();
        assertEquals(mainExTx.getVolume(), mainAcTx.getVolume());
        assertEquals(mainExTx.getAddress(), mainAcTx.getAddress());
        assertEquals(mainExTx.getBase(), mainAcTx.getBase());
        assertEquals(mainExTx.getExecuted(), mainAcTx.getExecuted());
        assertEquals(mainExTx.getNote(), mainAcTx.getNote());
        assertEquals(mainExTx.getLabels(), mainAcTx.getLabels());
        assertEquals(mainExTx.getAction(), mainAcTx.getAction());
        assertEquals(mainExTx.getUid(), mainAcTx.getUid());

        var feeExTx = expected.getRelated().get(0);
        var feeAcTx = actual.getRelated().get(0);
        assertEquals(feeExTx.getUid(), feeAcTx.getUid());
        assertEquals(feeExTx.getAction(), feeAcTx.getAction());
        assertEquals(feeExTx.getAddress(), feeAcTx.getAddress());
        assertEquals(feeExTx.getBase(), feeAcTx.getBase());
        assertEquals(feeExTx.getVolume(), feeAcTx.getVolume());
    }

    private void assertBuySell(TransactionCluster cluster, TransactionType type, BigDecimal volume) {
        assertEquals(0, cluster.getIgnoredFeeTransactionCount());
        assertNull(cluster.getIgnoredFeeReason());
        var tx = cluster.getMain();
        bigDecimalEquals(volume, tx.getVolume());
        assertNotNull(tx.getUid());
        assertNotNull(tx.getExecuted());
        assertEquals(Currency.BTC, tx.getBase());
        assertEquals(USD, tx.getQuote());
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
        when(clientMock.getAddressesInfoFromXpub(anyString(), anyLong(), anyInt(), anyInt()))
            .thenReturn(mockXpubResponse());
        return clientMock;
    }

    private Client mockClient(AddressInfo addressInfo) {
        var clientMock = mock(Client.class);
        when(clientMock.getAddressInfo(anyString(), anyInt(), anyInt(), anyInt())).thenReturn(addressInfo);
        return clientMock;
    }

    private Collection<AddressInfo> mockXpubResponse() {
        Collection<AddressInfo> allData = new BlockchainDummyData().getAllData();
        return allData;
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
        tx.addInputInfo(inputInfo(0, randomAlphanumeric(32), value));
        tx.addOutputInfo(outputInfo(0, address, value - 100));
        return tx;
    }

    private TxInfo withdrawalFromAddress(String address, long value) {
        var tx = emptyTxInfo();
        tx.addInputInfo(inputInfo(0, address, value - 100));
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

    private List<TransactionCluster> createExpectedClusters() {
        List<TransactionCluster> clusters = new ArrayList<>();
        var mainTx1 = new ImportedTransactionBean(
            "d5fe56406c02a3cf34ecca4712c14c65b187c286d7b23bd7e736164498858d56",
            Instant.ofEpochMilli(1576487761000L),
            Currency.fromCode("LTC"),
            Currency.fromCode("USD"),
            WITHDRAWAL,
            new BigDecimal("3.33474903"),
            null,
            null,
            "MHzjjwag7pBaDgbcVKZnGHsU9bcU8dcwTz"
        );

        var feeTx1 = new FeeRebateImportedTransactionBean(
            "d5fe56406c02a3cf34ecca4712c14c65b187c286d7b23bd7e736164498858d56-fee",
            Instant.ofEpochMilli(1576487761000L),
            Currency.fromCode("LTC"),
            Currency.fromCode("LTC"),
            FEE,
            new BigDecimal("0.00000036"),
            Currency.fromCode("LTC"),
            null
        );

        clusters.add(new TransactionCluster(mainTx1, emptyList()));
        clusters.add(new TransactionCluster(mainTx1, List.of(feeTx1)));

        var mainTx2 = new ImportedTransactionBean(
            "d5fe56406c02a3cf34ecca4712c14c65b187c286d7b23bd7e736164498858d56",
            Instant.ofEpochMilli(1576487761000L),
            Currency.fromCode("LTC"),
            Currency.fromCode("USD"),
            WITHDRAWAL,
            new BigDecimal("26.66524769"),
            null,
            null,
            "LgXg2gguYRET2P8yG5JpvokZJKwvLy2Fda"
        );

        var feeTx2 = new FeeRebateImportedTransactionBean(
            "d5fe56406c02a3cf34ecca4712c14c65b187c286d7b23bd7e736164498858d56-fee",
            Instant.ofEpochMilli(1574947182000L),
            Currency.fromCode("LTC"),
            Currency.fromCode("LTC"),
            FEE,
            new BigDecimal("0.00000292"),
            Currency.fromCode("LTC"),
            null
        );

        clusters.add(new TransactionCluster(mainTx2, emptyList()));
        clusters.add(new TransactionCluster(feeTx2, List.of(feeTx2)));

        var mainTx3 = new ImportedTransactionBean(
            "2c585e14db6ff463d2b3595bd9637a318096df3117d9ede813a192cd7e33f366",
            Instant.ofEpochMilli(1576489332000L),
            Currency.fromCode("LTC"),
            Currency.fromCode("USD"),
            DEPOSIT,
            new BigDecimal("18.00000000"),
            null,
            null,
            "MSKSUEWdBs6wTMtn1eKBTmLX26LULc7Gui"
        );

        // only with fee in Deposit as true
        var feeTx3 = new FeeRebateImportedTransactionBean(
            "2c585e14db6ff463d2b3595bd9637a318096df3117d9ede813a192cd7e33f366-fee",
            Instant.ofEpochMilli(1576489332000L),
            Currency.fromCode("LTC"),
            Currency.fromCode("LTC"),
            FEE,
            new BigDecimal("0.00001549"),
            Currency.fromCode("LTC"),
            null
        );

        clusters.add(new TransactionCluster(mainTx3, emptyList()));
        clusters.add(new TransactionCluster(mainTx3, List.of(feeTx3)));

        var mainTx4 = new ImportedTransactionBean(
            "68515dd3eddda7a91f4ea2e86990d45153cc81cd0379f02792e85b564c4b9abe",
            Instant.ofEpochMilli(1576489332000L),
            Currency.fromCode("LTC"),
            Currency.fromCode("USD"),
            DEPOSIT,
            new BigDecimal("29.25000000"),
            null,
            null,
            "MKwF9PdJSVCox7gzCzPeRkMNhSwG7iUEHE"
        );

        // only with fee in Deposit as true
        var feeTx4 = new FeeRebateImportedTransactionBean(
            "68515dd3eddda7a91f4ea2e86990d45153cc81cd0379f02792e85b564c4b9abe-fee",
            Instant.ofEpochMilli(1576489332000L),
            Currency.fromCode("LTC"),
            Currency.fromCode("LTC"),
            FEE,
            new BigDecimal("0.00086856"),
            Currency.fromCode("LTC"),
            null
        );

        clusters.add(new TransactionCluster(mainTx4, emptyList()));
        clusters.add(new TransactionCluster(mainTx4, List.of(feeTx4)));
        return clusters;
    }

}
