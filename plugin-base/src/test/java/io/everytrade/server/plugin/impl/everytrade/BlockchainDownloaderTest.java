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
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
        assertEquals(Currency.USD, tx.getQuote());
        assertEquals(type, tx.getAction());
        assertNotNull(tx.getImported());
    }

    @Test
    void ltcDepositWithdrawalTest() throws JsonProcessingException {
        // actual downloader;
        var infos = createDummyData();
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

        // withdrawals
        var expWithdrawals = expectedClusters.get(WITHDRAWAL);
        var firstExpWithdrawal = expWithdrawals.get(0);
        var secondExpWithdrawal = expWithdrawals.get(1);
        assertTxs(firstExpWithdrawal, allActualClusters.get(3), true);
        assertTxs(secondExpWithdrawal, allActualClusters.get(4), true);

        // deposits
        var expDeposits = expectedClusters.get(DEPOSIT);
        var expDeposit = expDeposits.get(0);
        assertTxs(expDeposit, allActualClusters.get(2), false);

    }

    private void assertTxs(TransactionCluster expected, TransactionCluster actual, boolean checkFee) {
        // fee
        if (checkFee) {
            var feeExTx = expected.getRelated().get(0);
            var feeAcTx = actual.getRelated().get(0);
            assertEquals(feeExTx.getUid(), feeAcTx.getUid());
            assertEquals(feeExTx.getAction(), feeAcTx.getAction());
            assertEquals(feeExTx.getAddress(), feeAcTx.getAddress());
            assertEquals(feeExTx.getBase(), feeAcTx.getBase());
            assertEquals(feeExTx.getVolume(), feeAcTx.getVolume());
        }
        // main tx
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

    private Client mockClient(AddressInfo addressInfo) {
        var clientMock = mock(Client.class);
        when(clientMock.getAddressInfo(anyString(), anyInt(), anyInt(), anyInt())).thenReturn(addressInfo);
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

    private Map<TransactionType, List<TransactionCluster>> createExpectedClusters() {
        Map<TransactionType, List<TransactionCluster>> clusters = new HashMap<>();

        // withdrawal
        List<TransactionCluster> withdrawals = new ArrayList<>();

        var firstWithTx = new ImportedTransactionBean(
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

        var firstWithFee = new FeeRebateImportedTransactionBean(
            "d5fe56406c02a3cf34ecca4712c14c65b187c286d7b23bd7e736164498858d56-fee",
            Instant.ofEpochMilli(1576487761000L),
            Currency.fromCode("LTC"),
            Currency.fromCode("LTC"),
            FEE,
            new BigDecimal("0.00000036"),
            Currency.fromCode("LTC"),
            null
        );

        var secondWithTx = new ImportedTransactionBean(
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

        var secondWithFee = new FeeRebateImportedTransactionBean(
            "d5fe56406c02a3cf34ecca4712c14c65b187c286d7b23bd7e736164498858d56-fee",
            Instant.ofEpochMilli(1574947182000L),
            Currency.fromCode("LTC"),
            Currency.fromCode("LTC"),
            FEE,
            new BigDecimal("0.00000292"),
            Currency.fromCode("LTC"),
            null
        );
        var firstWithdrawal = new TransactionCluster(firstWithTx, List.of(firstWithFee));
        var secondWithdrawal = new TransactionCluster(secondWithTx, List.of(secondWithFee));
        withdrawals.add(firstWithdrawal);
        withdrawals.add(secondWithdrawal);

        // DEPOSIT
        List<TransactionCluster> deposits = new ArrayList<>();

        var firstDepTx = new ImportedTransactionBean(
            "2c585e14db6ff463d2b3595bd9637a318096df3117d9ede813a192cd7e33f366",
            Instant.ofEpochMilli(1576489332000L),
            Currency.fromCode("LTC"),
            Currency.fromCode("USD"),
            DEPOSIT,
            new BigDecimal("18"),
            null,
            null,
            "MSKSUEWdBs6wTMtn1eKBTmLX26LULc7Gui"
        );

        // only with fee in Deposit as true
        var firstDepFee = new FeeRebateImportedTransactionBean(
            "2c585e14db6ff463d2b3595bd9637a318096df3117d9ede813a192cd7e33f366",
            Instant.ofEpochMilli(1576489332000L),
            Currency.fromCode("LTC"),
            Currency.fromCode("LTC"),
            FEE,
            new BigDecimal("0.00001549"),
            Currency.fromCode("LTC"),
            null
        );

        var firstDeposit = new TransactionCluster(firstDepTx, emptyList());
        deposits.add(firstDeposit);
        clusters.put(DEPOSIT, deposits);
        clusters.put(WITHDRAWAL, withdrawals);

        return clusters;
    }

    private AddressInfo createDummyData() throws JsonProcessingException {
        String dummyJsonData = "{\"txInfos\":[{\"blockHash\":\"32151119ca1b080978f3609065195c78d2fa1c0f3db8612d0d60beb08d74958a\"," +
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
        return new ObjectMapper().readValue(dummyJsonData, AddressInfo.class);

    }

}
