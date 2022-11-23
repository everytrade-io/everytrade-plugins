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
import io.everytrade.server.plugin.api.parser.ParseResult;
import io.everytrade.server.plugin.api.parser.ParsingProblem;
import io.everytrade.server.plugin.api.parser.TransactionCluster;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
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
    private static final String LTC = "LTC";



    private List<TxInfo> createDummyData() throws JsonProcessingException {
        List<TxInfo> result = new ArrayList<>();
        String dummyJsonData = "{\"txHash\":\"e0284023fea4bd3c2df0a4f3b740c2b77384111489669f1172c35e6ce4fe7301\"," +
            "\"blockHash\":\"a61c9e9d3fe24d86a9e06f76548b57830a751707570ef0777b3c1ad6d4510a6b\",\"timestamp\":1574947182000," +
            "\"receivedTimestamp\":1574947085150,\"size\":140,\"inputInfos\":[{\"txHash\":\"e9703acb8d88a6210a2210381e01f3b9024" +
            "449cc249955e7a5f4906a1c9fcbfd\",\"index\":0,\"address\":\"MUMbouREUxpVs1DZMCVknq9HziM95zTAyZ\",\"value\":100000000}]," +
            "\"outputInfos\":[{\"txHash\":\"e0284023fea4bd3c2df0a4f3b740c2b77384111489669f1172c35e6ce4fe7301\",\"index\":0,\"address" +
            "\":\"MWfsUktJrZGsUcQ619xnQGNa1sfj9BXT49\",\"value\":26135816},{\"txHash\":\"e0284023fea4bd3c2df0a4f3b740c2b773841114896" +
            "69f1172c35e6ce4fe7301\",\"index\":1,\"address\":\"Lga4BCGieEmvq7VFSN4JJCbvJpzKnRruuN\",\"value\":73863846}],\"blockHeig" +
            "ht\":1744138,\"confirmations\":629963}";
        TxInfo txInfoExampleData = new ObjectMapper().readValue(dummyJsonData, TxInfo.class);
        result.add(txInfoExampleData);
        return result;
    }


    @Test
    void ltcBuySplitWithdrawalAndSell() throws JsonProcessingException {
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
        var actualResult = downloader.download(ADDRESS);


        // expected
        var addressInfo = new AddressInfo(ADDRESS, 1L, 0L, 6900000000L, 6900000000L);
        addressInfo.setTxInfos(infos);
        var firstTx = new ImportedTransactionBean(
            "e0284023fea4bd3c2df0a4f3b740c2b77384111489669f1172c35e6ce4fe7301",
            Instant.ofEpochMilli(1574947182000L),
            Currency.fromCode("LTC"),
            Currency.fromCode("USD"),
            WITHDRAWAL,
            new BigDecimal("0.26135816"),
            null,
            null,
            "MWfsUktJrZGsUcQ619xnQGNa1sfj9BXT49"
        );

        var firstFee = new FeeRebateImportedTransactionBean(
            "e0284023fea4bd3c2df0a4f3b740c2b77384111489669f1172c35e6ce4fe7301",
            Instant.ofEpochMilli(1574947182000L),
            Currency.fromCode("LTC"),
            Currency.fromCode("LTC"),
            TransactionType.FEE,
            new BigDecimal("0.00000088339356667"),
            Currency.fromCode("LTC"),
            null
        );

        var secondTx = new ImportedTransactionBean(
            "e0284023fea4bd3c2df0a4f3b740c2b77384111489669f1172c35e6ce4fe7301",
            Instant.ofEpochMilli(1574947182000L),
            Currency.fromCode("LTC"),
            Currency.fromCode("USD"),
            WITHDRAWAL,
            new BigDecimal("0.73863846"),
            null,
            null,
            "Lga4BCGieEmvq7VFSN4JJCbvJpzKnRruuN"
        );

        var secondFee = new FeeRebateImportedTransactionBean(
            "e0284023fea4bd3c2df0a4f3b740c2b77384111489669f1172c35e6ce4fe7301",
            Instant.ofEpochMilli(1574947182000L),
            Currency.fromCode("LTC"),
            Currency.fromCode("LTC"),
            TransactionType.FEE,
            new BigDecimal("0.00000249660643333"),
            Currency.fromCode("LTC"),
            null
        );

        List<TransactionCluster> cluster = new ArrayList<>();
        cluster.add(new TransactionCluster(firstTx, List.of(firstFee)));
        cluster.add(new TransactionCluster(secondTx, List.of(secondFee)));

        var results = new ParseResult(cluster, Collections.emptyList());
        var result = new DownloadResult(results, null);

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
}
