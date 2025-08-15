package io.everytrade.server.plugin.impl.everytrade.etherscan;

import si.mazi.rescu.RestProxyFactory;

import java.io.IOException;
import java.util.List;

public class EtherScanClient {
    private static final String ETHERSCAN_URL = "https://api.etherscan.io/v2/";

    EtherScanV1API api;

    public EtherScanClient() {
        this.api = RestProxyFactory.createProxy(EtherScanV1API.class, ETHERSCAN_URL);
    }

    public EtherScanDto<List<EtherScanTransactionDto>> getNormalTxsByAddress(
        String address, long startBlock, long endBlock, int page, int offset, String sort, String apiKeyToken
    ) throws IOException {
        return api.getNormalTxsByAddress("1", "account", "txlist", address, startBlock, endBlock, page, offset, sort, apiKeyToken);
    }

    public EtherScanDto<List<EtherScanErc20TransactionDto>> getErc20TxsByAddress(
        String address, String contractAddress, long startBlock, long endBlock, int page, int offset, String sort, String apiKeyToken
    ) throws IOException {
        return api.getErc20TxsByAddress(
            "1", "account", "tokentx", address, contractAddress, startBlock, endBlock, page, offset, sort, apiKeyToken
        );
    }

    public EtherScanDto<Long> getBlockNumberByTimestamp(String timeStamp, String closest, String apiKeyToken) throws IOException {
        return api.getBlockNumberByTimestamp("1", "block", "getblocknobytime", timeStamp, closest, apiKeyToken);
    }
}
