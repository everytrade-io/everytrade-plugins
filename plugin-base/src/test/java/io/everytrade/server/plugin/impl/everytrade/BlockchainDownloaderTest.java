package io.everytrade.server.plugin.impl.everytrade;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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

    @Test
    void ltcDepositWithdrawalTest1() throws JsonProcessingException {
        // actual downloader;
        var infos = createDummyAddressInfoLtcData1();
        var downloader = new BlockchainDownloader(
            mockClient(infos),
            null,
            0L,
            emptySet(),
            FIAT,
            LTC,
            false,
            false,
            false,
            true,
            0,
            1000);
        var actualResult = downloader.download(SOURCE);
        var allActualClusters = actualResult.getParseResult().getTransactionClusters();
        var expectedClusters = createExpectedClusters();
        assertTx(expectedClusters.get(0), allActualClusters.get(0));
    }

    @Test
    void ltcDepositWithdrawalTest2() throws JsonProcessingException {
        // actual downloader;
        var infos = createDummyAddressInfoLtcData2();
        var downloaderNoFees = new BlockchainDownloader(
            mockClient(infos),
            null,
            0L,
            emptySet(),
            FIAT,
            LTC,
            false,
            false,
            false,
            false,
            0,
            1000);

        var downloaderWithFees = new BlockchainDownloader(
            mockClient(infos),
            null,
            0L,
            emptySet(),
            FIAT,
            LTC,
            false,
            false,
            false,
            false,
            0,
            1000);
        var allActualClustersNoFees = downloaderNoFees.download(SOURCE).getParseResult().getTransactionClusters();
        var allActualClustersWithFees = downloaderWithFees.download(SOURCE).getParseResult().getTransactionClusters();
        var expectedClusters = createExpectedClusters();
        assertTx(expectedClusters.get(0), allActualClustersNoFees.get(10));
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

    /**
     * Addreess: MUMbouREUxpVs1DZMCVknq9HziM95zTAyZ
     * @return
     * @throws JsonProcessingException
     */
    private AddressInfo createDummyAddressInfoLtcData1() throws JsonProcessingException {
        String dummyAddressInfoJsonData =
            "{\"txInfos\":[{\"blockHash\":\"32151119ca1b080978f3609065195c78d2fa1c0f3db8612d0d60beb08d74958a\"," +
            "\"blockHeight\":1754090,\"receivedTimestamp\":1576490684647,\"size\":140,\"inputInfos\":[{\"address\":\"MUMbouREUxpVs1DZMCVk" +
            "nq9HziM95zTAyZ\",\"index\":1,\"txHash\":\"2c585e14db6ff463d2b3595bd9637a318096df3117d9ede813a192cd7e33f366\",\"value\":1800" +
            "000000}],\"outputInfos\":[{\"address\":\"MJNwHJE1ivBDHNw7PJq3jZdVUYnGh4kqxf\",\"index\":0,\"txHash\":\"ad3a6e0609a4af060ef8" +
            "86c51c24b75c39eb275e5abe19e4d45d2310975d57cf\",\"value\":461404003},{\"address\":\"LgXaRAf8BtnxZ38YjiJpco2XuTrfhu6Gdh\",\"i" +
            "ndex\":1,\"txHash\":\"ad3a6e0609a4af060ef886c51c24b75c39eb275e5abe19e4d45d2310975d57cf\",\"value\":1338595659}],\"confir" +
            "mations\":625253,\"txHash\":\"ad3a6e0609a4af060ef886c51c24b75c39eb275e5abe19e4d45d2310975d57cf\",\"timestamp\":157649095" +
            "3000},{\"blockHash\":\"d4013501a3c8fca41013368437e977a08ac0b412553738f21d2c2ec9fe9b0226\",\"blockHeight\":1754083,\"recei" +
            "vedTimestamp\":1576489324152,\"size\":138,\"inputInfos\":[{\"address\":\"MSKSUEWdBs6wTMtn1eKBTmLX26LULc7Gui\",\"index\":0," +
            "\"txHash\":\"17466dd2fe168b1f8dcfb822dca891c91b584afccc0a71cf1d5b6116742daae3\",\"value\":28935526143}],\"outputInfos\":[{" +
            "\"address\":\"MLooJWUKeyJaR2tbpva6m1kbrvb56HvKPz\",\"index\":0,\"txHash\":\"2c585e14db6ff463d2b3595bd9637a318096df3117d9ed" +
            "e813a192cd7e33f366\",\"value\":27135501243},{\"address\":\"MUMbouREUxpVs1DZMCVknq9HziM95zTAyZ\",\"index\":1,\"txHash\":\"" +
            "2c585e14db6ff463d2b3595bd9637a318096df3117d9ede813a192cd7e33f366\",\"value\":1800000000}],\"confirmations\":625260,\"txHas" +
            "h\":\"2c585e14db6ff463d2b3595bd9637a318096df3117d9ede813a192cd7e33f366\",\"timestamp\":1576489332000},{\"blockHash\":\"fb4" +
            "001b190a8d4d9b1958929ba8f651c2dfc2cebb4dbb9c680a0d0bd626932a7\",\"blockHeight\":1754070,\"receivedTimestamp\":15764875682" +
            "38,\"size\":204,\"inputInfos\":[{\"address\":\"MFTA6ajKkfx7yyoJmF1p2HBSq8YSvraPj9\",\"index\":1,\"txHash\":\"0c9f116565b1" +
            "f0d8ebf594a5a838aada9b0199e3f34177e98e5a2084930c9e07\",\"value\":1770995111},{\"address\":\"MUMbouREUxpVs1DZMCVknq9HziM95" +
            "zTAyZ\",\"index\":1,\"txHash\":\"17466dd2fe168b1f8dcfb822dca891c91b584afccc0a71cf1d5b6116742daae3\",\"value\":3000000000}" +
            "],\"outputInfos\":[{\"address\":\"MHzjjwag7pBaDgbcVKZnGHsU9bcU8dcwTz\",\"index\":0,\"txHash\":\"d5fe56406c02a3cf34ecca47" +
            "12c14c65b187c286d7b23bd7e736164498858d56\",\"value\":530335711},{\"address\":\"LgXg2gguYRET2P8yG5JpvokZJKwvLy2Fda\",\"in" +
            "dex\":1,\"txHash\":\"d5fe56406c02a3cf34ecca4712c14c65b187c286d7b23bd7e736164498858d56\",\"value\":4240658878}],\"confirm" +
            "ations\":625273,\"txHash\":\"d5fe56406c02a3cf34ecca4712c14c65b187c286d7b23bd7e736164498858d56\",\"timestamp\":15764877610" +
            "00},{\"blockHash\":\"49eddb868ec6afa21582b702b5ceddbf4d3a900183c80abf5d828f0b29d67108\",\"blockHeight\":1754064,\"receive" +
            "dTimestamp\":1576486204113,\"size\":138,\"inputInfos\":[{\"address\":\"MUiaRk2vE6hMAkXikhm9irQdBeCHeU51MF\",\"index\":0,\"" +
            "txHash\":\"35150c22803fa9260438abb0ca0b4c7e4f4c0d5651e58d3d56f9ec3208c9f995\",\"value\":31935551043}],\"outputInfos\":[{\"" +
            "address\":\"MSKSUEWdBs6wTMtn1eKBTmLX26LULc7Gui\",\"index\":0,\"txHash\":\"17466dd2fe168b1f8dcfb822dca891c91b584afccc0a71c" +
            "f1d5b6116742daae3\",\"value\":28935526143},{\"address\":\"MUMbouREUxpVs1DZMCVknq9HziM95zTAyZ\",\"index\":1,\"txHash\":\"1" +
            "7466dd2fe168b1f8dcfb822dca891c91b584afccc0a71cf1d5b6116742daae3\",\"value\":3000000000}],\"confirmations\":625279,\"txHas" +
            "h\":\"17466dd2fe168b1f8dcfb822dca891c91b584afccc0a71cf1d5b6116742daae3\",\"timestamp\":1576486274000},{\"blockHash\":\"3d" +
            "3fd722fb5da1177c1a6f345ef71fdb6be37d26fdbb0a2f0e08f2c96ff96770\",\"blockHeight\":1753787,\"receivedTimestamp\":15764416" +
            "66381,\"size\":140,\"inputInfos\":[{\"address\":\"MUMbouREUxpVs1DZMCVknq9HziM95zTAyZ\",\"index\":0,\"txHash\":\"354bf39" +
            "d6700107c563a10362af19896bd1997cb122185e05817b806eb77c0f9\",\"value\":2000000000}],\"outputInfos\":[{\"address\":\"LgXh" +
            "6VpM8kUbPuo6GAM6vMw4PsHhm6nHAT\",\"index\":0,\"txHash\":\"0c9f116565b1f0d8ebf594a5a838aada9b0199e3f34177e98e5a2084930c9" +
            "e07\",\"value\":229004551},{\"address\":\"MFTA6ajKkfx7yyoJmF1p2HBSq8YSvraPj9\",\"index\":1,\"txHash\":\"0c9f116565b1f0" +
            "d8ebf594a5a838aada9b0199e3f34177e98e5a2084930c9e07\",\"value\":1770995111}],\"confirmations\":625556,\"txHash\":\"0c9f" +
            "116565b1f0d8ebf594a5a838aada9b0199e3f34177e98e5a2084930c9e07\",\"timestamp\":1576441694000},{\"blockHash\":\"d3a22384e" +
            "d1bfeca06f3ee30e8d7702941f78bf9a082614553f517d4c8ff46a7\",\"blockHeight\":1753707,\"receivedTimestamp\":15764302834" +
            "22,\"size\":138,\"inputInfos\":[{\"address\":\"MLaBWqMzoyn73A5eV4S9APS9Grbx4bbaVK\",\"index\":1,\"txHash\":\"fd1327e" +
            "e45840d6fe7f3ea0230325fd9b7dcf94a5447e422dd2a997885236cdc\",\"value\":34390393063}],\"outputInfos\":[{\"address\":\"M" +
            "UMbouREUxpVs1DZMCVknq9HziM95zTAyZ\",\"index\":0,\"txHash\":\"354bf39d6700107c563a10362af19896bd1997cb122185e05817b806" +
            "eb77c0f9\",\"value\":2000000000},{\"address\":\"MVdkL9STBr5EVs2SfYFLoypfLg2uiPmA8i\",\"index\":1,\"txHash\":\"354bf39" +
            "d6700107c563a10362af19896bd1997cb122185e05817b806eb77c0f9\",\"value\":32390368163}],\"confirmations\":625636,\"txHash\"" +
            ":\"354bf39d6700107c563a10362af19896bd1997cb122185e05817b806eb77c0f9\",\"timestamp\":1576430646000},{\"blockHash\":\"a61" +
            "c9e9d3fe24d86a9e06f76548b57830a751707570ef0777b3c1ad6d4510a6b\",\"blockHeight\":1744138,\"receivedTimestamp\":157494708" +
            "5150,\"size\":140,\"inputInfos\":[{\"address\":\"MUMbouREUxpVs1DZMCVknq9HziM95zTAyZ\",\"index\":0,\"txHash\":\"e9703acb" +
            "8d88a6210a2210381e01f3b9024449cc249955e7a5f4906a1c9fcbfd\",\"value\":100000000}],\"outputInfos\":[{\"address\":\"MWfsUkt" +
            "JrZGsUcQ619xnQGNa1sfj9BXT49\",\"index\":0,\"txHash\":\"e0284023fea4bd3c2df0a4f3b740c2b77384111489669f1172c35e6ce4fe7301\"" +
            ",\"value\":26135816},{\"address\":\"Lga4BCGieEmvq7VFSN4JJCbvJpzKnRruuN\",\"index\":1,\"txHash\":\"e0284023fea4bd3c2df0" +
            "a4f3b740c2b77384111489669f1172c35e6ce4fe7301\",\"value\":73863846}],\"confirmations\":635205,\"txHash\":\"e0284023fe" +
            "a4bd3c2df0a4f3b740c2b77384111489669f1172c35e6ce4fe7301\",\"timestamp\":1574947182000},{\"blockHash\":\"0b0f271dc656e" +
            "17962b350397517c8be3ba7995e086e7c26e976bcd74fb9398a\",\"blockHeight\":1744117,\"receivedTimestamp\":1574942580344,\"si" +
            "ze\":223,\"inputInfos\":[{\"address\":\"Lg4S7HQNqnyr3rYqfZHbXn2ZAj8ddUMfD2\",\"index\":1,\"txHash\":\"73541ee500ba665" +
            "8412cc3bfa5abc5dc546c362c15fca1c3330b7465e6ccff47\",\"value\":316262638}],\"outputInfos\":[{\"address\":\"MUMbouREUxpV" +
            "s1DZMCVknq9HziM95zTAyZ\",\"index\":0,\"txHash\":\"e9703acb8d88a6210a2210381e01f3b9024449cc249955e7a5f4906a1c9fcbfd\",\"v" +
            "alue\":100000000},{\"address\":\"Lg1e3Dm56WDotRiRKGoA5QwCsZFQjMEtyY\",\"index\":1,\"txHash\":\"e9703acb8d88a6210a221038" +
            "1e01f3b9024449cc249955e7a5f4906a1c9fcbfd\",\"value\":216229038}],\"confirmations\":635226,\"txHash\":\"e9703acb8d88a6" +
            "210a2210381e01f3b9024449cc249955e7a5f4906a1c9fcbfd\",\"timestamp\":1574942706000}],\"address\":\"MUMbouREUxpVs1DZMCVkn" +
            "q9HziM95zTAyZ\",\"finalBalance\":0,\"totalReceived\":6900000000,\"totalSent\":6900000000,\"numberOfTransactions\":8}";
        return new ObjectMapper().readValue(dummyAddressInfoJsonData, AddressInfo.class);
    }

    /**
     * Addreess: MKwF9PdJSVCox7gzCzPeRkMNhSwG7iUEHE
     * @return
     * @throws JsonProcessingException
     */
    private AddressInfo createDummyAddressInfoLtcData2() throws JsonProcessingException {
        String dummyAddressInfoJsonData =
            "{\"address\":\"MKwF9PdJSVCox7gzCzPeRkMNhSwG7iUEHE\",\"numberOfTransactions\":12,\"finalBalance\":0,\"totalReceived\":" +
                "14730930128,\"totalSent\":14730930128,\"txInfos\":[{\"txHash\":\"299455c619b825b6dab2b3e8cd45308febeab321cfd965560f" +
                "d7f79e9eff493b\",\"blockHash\":\"2103dcb9e763bde69d7c1713e12223fb2e1d34985e055f68f52416e7ce46c474\",\"timestamp\":1" +
                "590156673000,\"receivedTimestamp\":1590156588989,\"size\":138,\"inputInfos\":[{\"txHash\":\"12cf8dbd40b452404e94586" +
                "dae922e7b0782d38f27666caad6e9bda5a8a9040a\",\"index\":0,\"address\":\"MFkvyZJ28JDfAeB31SnNwfBCaeUpTG9b1A\",\"value\"" +
                ":10083321433}],\"outputInfos\":[{\"txHash\":\"299455c619b825b6dab2b3e8cd45308febeab321cfd965560fd7f79e9eff493b\",\"" +
                "index\":0,\"address\":\"MKwF9PdJSVCox7gzCzPeRkMNhSwG7iUEHE\",\"value\":1000000000},{\"txHash\":\"299455c619b825b6dab" +
                "2b3e8cd45308febeab321cfd965560fd7f79e9eff493b\",\"index\":1,\"address\":\"MGbpkCB7M8oFvooCX9DzLyJ1n3ZBsvYaaP\",\"val" +
                "ue\":9083296533}],\"blockHeight\":1845664,\"confirmations\":592854},{\"txHash\":\"32b5f4c9b55b5c1a8f437fc6c8d29545f25" +
                "3bf19e894dad2a6722817510b80fc\",\"blockHash\":\"4c07b398dafa267a6a602b0bfe6d1ab3a9d37dac711c018f0274c988f5a2b7d5\",\"" +
                "timestamp\":1595244946000,\"receivedTimestamp\":1595244777630,\"size\":268,\"inputInfos\":[{\"txHash\":\"17a717041547" +
                "6a8a28ab023d779ea8448ded790febf66d696825c1754619c656\",\"index\":0,\"address\":\"MX5MPuvmbCvgNPJ3LRPpsFBjEKbGh3tLPz\"" +
                ",\"value\":2582814682},{\"txHash\":\"299455c619b825b6dab2b3e8cd45308febeab321cfd965560fd7f79e9eff493b\",\"index\":0," +
                "\"address\":\"MKwF9PdJSVCox7gzCzPeRkMNhSwG7iUEHE\",\"value\":1000000000},{\"txHash\":\"e74e3ae5d445f04f12f51eb76b1d6" +
                "cdf661fb8f9b8c527e8bcc8785c54774b29\",\"index\":0,\"address\":\"MVMNE1hPokdTAi4X7jHPzuGeqYR2umpLJS\",\"value\":19392" +
                "65574}],\"outputInfos\":[{\"txHash\":\"32b5f4c9b55b5c1a8f437fc6c8d29545f253bf19e894dad2a6722817510b80fc\",\"index\":" +
                "0,\"address\":\"MSxdYmacs9VsnW2HEeiBr1WtscjxKD8hHR\",\"value\":130782673},{\"txHash\":\"32b5f4c9b55b5c1a8f437fc6c8d2" +
                "9545f253bf19e894dad2a6722817510b80fc\",\"index\":1,\"address\":\"LNZ57vc6ywnJnhuqPTATZjrZCHHMgQSu2u\",\"value\":5391" +
                "297230}],\"blockHeight\":1880198,\"confirmations\":558320},{\"txHash\":\"701381b77bff444bc2883c072f3c5d5c7bf2f3fb74b" +
                "5e286597943b6ff2863e4\",\"blockHash\":\"950a51c7ac7e77827342526452548ea8c643e54ac5f5d213b09e90b61e65f097\",\"timesta" +
                "mp\":1597000784000,\"receivedTimestamp\":1597000695034,\"size\":221,\"inputInfos\":[{\"txHash\":\"a1193a86d22b818a5" +
                "516b85286d7fdafebae8ae8b318e43dc522a28e16dd509a\",\"index\":0,\"address\":\"LddosDRg6ZPsXkWshoCKaaWkAS2udVLKzK\",\"" +
                "value\":1879000000}],\"outputInfos\":[{\"txHash\":\"701381b77bff444bc2883c072f3c5d5c7bf2f3fb74b5e286597943b6ff286" +
                "3e4\",\"index\":0,\"address\":\"MDpcBGDcgJHc6FNrzG76TWiRkNG9ZadCzJ\",\"value\":193240030},{\"txHash\":\"701381b77" +
                "bff444bc2883c072f3c5d5c7bf2f3fb74b5e286597943b6ff2863e4\",\"index\":1,\"address\":\"MKwF9PdJSVCox7gzCzPeRkMNhSwG" +
                "7iUEHE\",\"value\":1685726820}],\"blockHeight\":1891852,\"confirmations\":546666},{\"txHash\":\"fe0b4bee17ec03b01a" +
                "5ed440a325ddbc0206c5a99682f0d05abd2d09da3863db\",\"blockHash\":\"69099fa0789a80e3affbfb29ef8cbc8f125b88719f377f6" +
                "0db7ac940f0ddf4e9\",\"timestamp\":1598205349000,\"receivedTimestamp\":1598205300006,\"size\":140,\"inputInfos\":" +
                "[{\"txHash\":\"701381b77bff444bc2883c072f3c5d5c7bf2f3fb74b5e286597943b6ff2863e4\",\"index\":1,\"address\":\"MKwF" +
                "9PdJSVCox7gzCzPeRkMNhSwG7iUEHE\",\"value\":1685726820}],\"outputInfos\":[{\"txHash\":\"fe0b4bee17ec03b01a5ed440" +
                "a325ddbc0206c5a99682f0d05abd2d09da3863db\",\"index\":0,\"address\":\"MTi9Apkja1yg4uEJjBvbTShe7xBwG6eL4F\",\"valu" +
                "e\":388454906},{\"txHash\":\"fe0b4bee17ec03b01a5ed440a325ddbc0206c5a99682f0d05abd2d09da3863db\",\"index\":1,\"ad" +
                "dress\":\"LP7NVyTWfAkgWzFpQkAspmB32eSmMj2dFb\",\"value\":1297271745}],\"blockHeight\":1899847,\"confirmations\":538" +
                "671},{\"txHash\":\"aea15dc9eeafff24250d5b6d2f926542121c157d56c117844e7c0d3acfb7c726\",\"blockHash\":\"252c57354a07d" +
                "9ec259c17a969c38aa783294b36886011b2e15c310bf9c26ce0\",\"timestamp\":1598380461000,\"receivedTimestamp\":15983801552" +
                "76,\"size\":221,\"inputInfos\":[{\"txHash\":\"3503f3f4d766e6feab35adbed109f305f62b8751cf3aec27dfec1c667f5a693d\",\"" +
                "index\":0,\"address\":\"LguvMTGnbbHmn74aPUFbLzeGJn19thrntT\",\"value\":11551701492}],\"outputInfos\":[{\"txHash\":\"a" +
                "ea15dc9eeafff24250d5b6d2f926542121c157d56c117844e7c0d3acfb7c726\",\"index\":0,\"address\":\"MDmzFQq1ABirxuQF3jXgjjGA" +
                "C9VZwpWThK\",\"value\":10251708342},{\"txHash\":\"aea15dc9eeafff24250d5b6d2f926542121c157d56c117844e7c0d3acfb7c7" +
                "26\",\"index\":1,\"address\":\"MKwF9PdJSVCox7gzCzPeRkMNhSwG7iUEHE\",\"value\":1299960000}],\"blockHeight\":19010" +
                "24,\"confirmations\":537494},{\"txHash\":\"2dd0f68a7ab04c405d3eeb8d64654c14f276c67621f2d88d3b84d471c58c3dc4\",\"bl" +
                "ockHash\":\"0eedb3a80f9476acbe0451fb792a16bb87147f207a9fe8f7beafb11eb8a54ff6\",\"timestamp\":1598875679000,\"receiv" +
                "edTimestamp\":1598875636113,\"size\":285,\"inputInfos\":[{\"txHash\":\"e73f22f78dd579a79d8cfbef90455dbfd1950fbc4ef" +
                "0bca86d0b435023b67877\",\"index\":0,\"address\":\"MHcG6Tg1MBpW59Bm4yya1euQq881RXmwC6\",\"value\":703500343},{\"txHa" +
                "sh\":\"16005359c5dd95a4c2caf8cd364779420f7c89897f3dc3f7c34f5189f01d9324\",\"index\":0,\"address\":\"LPmZTP3rtyt5t" +
                "cCYhCML1NHfR3qyXoHCyw\",\"value\":1306059990}],\"outputInfos\":[{\"txHash\":\"2dd0f68a7ab04c405d3eeb8d64654c14f2" +
                "76c67621f2d88d3b84d471c58c3dc4\",\"index\":0,\"address\":\"MKwF9PdJSVCox7gzCzPeRkMNhSwG7iUEHE\",\"value\":180498" +
                "0649},{\"txHash\":\"2dd0f68a7ab04c405d3eeb8d64654c14f276c67621f2d88d3b84d471c58c3dc4\",\"index\":1,\"address\":\"" +
                "MSpHsPLCLKGvzfkMGkj8kmvVSW3amtL2UP\",\"value\":204532734}],\"blockHeight\":1904332,\"confirmations\":534186},{\"txHa" +
                "sh\":\"07a1ca430faf8ea25ac595d32af7b5a66001121ce82ed0515c95950b7b8e11e4\",\"blockHash\":\"524ec85aa8f890aa47d8c1" +
                "ec5ab418ced40640da01c03a36deea6bff7bead21d\",\"timestamp\":1599070328000,\"receivedTimestamp\":1599070196130,\"si" +
                "ze\":204,\"inputInfos\":[{\"txHash\":\"2dd0f68a7ab04c405d3eeb8d64654c14f276c67621f2d88d3b84d471c58c3dc4\",\"inde" +
                "x\":0,\"address\":\"MKwF9PdJSVCox7gzCzPeRkMNhSwG7iUEHE\",\"value\":1804980649},{\"txHash\":\"aea15dc9eeafff24250" +
                "d5b6d2f926542121c157d56c117844e7c0d3acfb7c726\",\"index\":1,\"address\":\"MKwF9PdJSVCox7gzCzPeRkMNhSwG7iUEHE\",\"v" +
                "alue\":1299960000}],\"outputInfos\":[{\"txHash\":\"07a1ca430faf8ea25ac595d32af7b5a66001121ce82ed0515c95950b7b8e11" +
                "e4\",\"index\":0,\"address\":\"MKJj5fDYhjLLmkSXTJVY59BxA1mWLff8Fh\",\"value\":842959458},{\"txHash\":\"07a1ca430f" +
                "af8ea25ac595d32af7b5a66001121ce82ed0515c95950b7b8e11e4\",\"index\":1,\"address\":\"LPJBBQmktfP1AMXMKskvRCH9xftH574" +
                "fYo\",\"value\":2261980930}],\"blockHeight\":1905691,\"confirmations\":532827},{\"txHash\":\"ed2bd7f2112e601a8287" +
                "97c7b87f117a2cb5d851202a862efc1cb216968eaab8\",\"blockHash\":\"8b34c54b1ef41b57a6e619d407b06d54676a0da894132f618c5" +
                "6c436e5001bac\",\"timestamp\":1599682580000,\"receivedTimestamp\":1599682415393,\"size\":221,\"inputInfos\":[{\"txH" +
                "ash\":\"e8da22d0f1807adccd4043d81eb1e684ffd7500efb93d71a0c7215fbef6619a4\",\"index\":0,\"address\":\"Lbab8WcB9XMuyWS" +
                "ZCiDyPjJdHWMpu2JEob\",\"value\":5000000000}],\"outputInfos\":[{\"txHash\":\"ed2bd7f2112e601a828797c7b87f117a2cb5d851" +
                "202a862efc1cb216968eaab8\",\"index\":0,\"address\":\"MW8gW7uSzjNuNiF6txb9afZWChFBJ9iWxf\",\"value\":2222055273},{\"" +
                "txHash\":\"ed2bd7f2112e601a828797c7b87f117a2cb5d851202a862efc1cb216968eaab8\",\"index\":1,\"address\":\"MKwF9PdJSVCo" +
                "x7gzCzPeRkMNhSwG7iUEHE\",\"value\":2777911577}],\"blockHeight\":1909947,\"confirmations\":528571},{\"txHash\":\"6d4" +
                "de8ae8fbe9f549c271f5e72f8ca73d64fbcae792f41ce944491ba6307a2e7\",\"blockHash\":\"95024566cc164e10ca4424b1ee15da4cadf" +
                "27619bc372784fbecd7f8762220a3\",\"timestamp\":1601987253000,\"receivedTimestamp\":1601987209339,\"size\":140,\"input" +
                "Infos\":[{\"txHash\":\"ed2bd7f2112e601a828797c7b87f117a2cb5d851202a862efc1cb216968eaab8\",\"index\":1,\"address\":\"" +
                "MKwF9PdJSVCox7gzCzPeRkMNhSwG7iUEHE\",\"value\":2777911577}],\"outputInfos\":[{\"txHash\":\"6d4de8ae8fbe9f549c271f5e" +
                "72f8ca73d64fbcae792f41ce944491ba6307a2e7\",\"index\":0,\"address\":\"LhiwEJvCv8DCqmRjhBRsV5G8GpX9KpVFtN\",\"value\":" +
                "256269102},{\"txHash\":\"6d4de8ae8fbe9f549c271f5e72f8ca73d64fbcae792f41ce944491ba6307a2e7\",\"index\":1,\"addres" +
                "s\":\"MSM9qEqXst8VfQ5ejE6LJqwvjAm6MNGi7w\",\"value\":2521642306}],\"blockHeight\":1925106,\"confirmations\":5134" +
                "12},{\"txHash\":\"955d1983b91efcc7e1d6551541a2f298f507812766e6786ca9fc739709335be2\",\"blockHash\":\"3c9b0361d" +
                "424e6b1273e6bacfe5c4f598e77be256e228567413b22ba95608115\",\"timestamp\":1602145440000,\"receivedTimestamp\":160" +
                "2145406168,\"size\":138,\"inputInfos\":[{\"txHash\":\"ef4a5c663f81c534a2dbb607df2b55394fa878bb794cef74e5c3817385" +
                "03bcac\",\"index\":1,\"address\":\"MU4Nr7EfY6K4ttmxvSPoK8KiFJHi4FrwXf\",\"value\":25503864016}],\"outputInfos\":" +
                "[{\"txHash\":\"955d1983b91efcc7e1d6551541a2f298f507812766e6786ca9fc739709335be2\",\"index\":0,\"address\":\"MKwF" +
                "9PdJSVCox7gzCzPeRkMNhSwG7iUEHE\",\"value\":3237351082},{\"txHash\":\"955d1983b91efcc7e1d6551541a2f298f507812766e" +
                "6786ca9fc739709335be2\",\"index\":1,\"address\":\"MJjTVo8KCkiJir2Y5yVqnYfVgpTPNxkKgV\",\"value\":22266488034}],\"" +
                "blockHeight\":1926209,\"confirmations\":512309},{\"txHash\":\"68515dd3eddda7a91f4ea2e86990d45153cc81cd0379f02792e" +
                "85b564c4b9abe\",\"blockHash\":\"9927f82fb20689ba08277c604b3f6563d0eaadd3801a5858beffdaee93ca6417\",\"timestamp\":16" +
                "02947731000,\"receivedTimestamp\":1602947291416,\"size\":578,\"inputInfos\":[{\"txHash\":\"0a63b08828f1a41ef5bbfd5" +
                "6484717702ed6f87f0e5f98ac242c33117ecf0e62\",\"index\":0,\"address\":\"LPmZTP3rtyt5tcCYhCML1NHfR3qyXoHCyw\",\"value" +
                "\":1031671353},{\"txHash\":\"d7609a6a04e290b40a13d20404a4d22edda2a0e4de2230e4b79ff8e9268c095c\",\"index\":0,\"addr" +
                "ess\":\"LQokjxnmEv11dtebi9U6yCuT5EfvaAJSNm\",\"value\":758477626},{\"txHash\":\"918b5b700b925be31cf9bfe894f502b1fd" +
                "33e6548e70af64f3b4993f6b550797\",\"index\":0,\"address\":\"LPmZTP3rtyt5tcCYhCML1NHfR3qyXoHCyw\",\"value\":10321855" +
                "68},{\"txHash\":\"c5bbd4f848ab47fd2ae65f774925702848354ca919d0d80c0c4bc66428b70b29\",\"index\":0,\"address\":\"MB" +
                "K6BJmStRiek5RhXGS6pcM1c9Wt7k3dw8\",\"value\":244000000}],\"outputInfos\":[{\"txHash\":\"68515dd3eddda7a91f4ea2e869" +
                "90d45153cc81cd0379f02792e85b564c4b9abe\",\"index\":0,\"address\":\"MKwF9PdJSVCox7gzCzPeRkMNhSwG7iUEHE\",\"value\":2" +
                "925000000},{\"txHash\":\"68515dd3eddda7a91f4ea2e86990d45153cc81cd0379f02792e85b564c4b9abe\",\"index\":1,\"addres" +
                "s\":\"MR5VhFtU9DW1JP34ghiDbJGA41Ndxjdmmu\",\"value\":141243497}],\"blockHeight\":1931542,\"confirmations\":506976" +
                "},{\"txHash\":\"820176bb1be4875e940727a5c1e01ac0d8cdc145db09550b4c991f4b201e7db8\",\"blockHash\":\"8a6994834ecb5a" +
                "caa4dc9f3df17a37d35fcd45dd6124bd86974d4dc735e0cff4\",\"timestamp\":1603474312000,\"receivedTimestamp\":1603474220" +
                "408,\"size\":204,\"inputInfos\":[{\"txHash\":\"68515dd3eddda7a91f4ea2e86990d45153cc81cd0379f02792e85b564c4b9abe\",\"i" +
                "ndex\":0,\"address\":\"MKwF9PdJSVCox7gzCzPeRkMNhSwG7iUEHE\",\"value\":2925000000},{\"txHash\":\"955d1983b91efcc7" +
                "e1d6551541a2f298f507812766e6786ca9fc739709335be2\",\"index\":0,\"address\":\"MKwF9PdJSVCox7gzCzPeRkMNhSwG7iUEHE\",\"v" +
                "alue\":3237351082}],\"outputInfos\":[{\"txHash\":\"820176bb1be4875e940727a5c1e01ac0d8cdc145db09550b4c991f4b201e7" +
                "db8\",\"index\":0,\"address\":\"MLuwChPPMdhjCVs5s9quDYSDVqoh3NMw2Z\",\"value\":1162350821},{\"txHash\":\"820176" +
                "bb1be4875e940727a5c1e01ac0d8cdc145db09550b4c991f4b201e7db8\",\"index\":1,\"address\":\"LSrYFwRBU1bs3tu5g1gyXb8s6a" +
                "ueudLTcr\",\"value\":5000000000}],\"blockHeight\":1934937,\"confirmations\":503581}]}";
        return new ObjectMapper().readValue(dummyAddressInfoJsonData, AddressInfo.class);
    }

}
