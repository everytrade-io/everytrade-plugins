package io.everytrade.server.plugin.impl.everytrade.etherscan;

import si.mazi.rescu.HttpStatusIOException;
import si.mazi.rescu.RestProxyFactory;

import java.io.IOException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class EtherScanClient {
    private static final String ETHERSCAN_URL = "https://api.etherscan.io/v2/";

    EtherScanV1API api;

    public EtherScanClient() {
        this.api = RestProxyFactory.createProxy(EtherScanV1API.class, ETHERSCAN_URL);
    }

    public EtherScanDto<List<EtherScanTransactionDto>> getNormalTxsByAddress(
        String address, long startBlock, long endBlock, int page, int offset, String sort, String apiKeyToken
    ) throws IOException {
        try {
            return api.getNormalTxsByAddress(
                "1", "account", "txlist", address, startBlock, endBlock, page, offset, sort, apiKeyToken);
        } catch (HttpStatusIOException e) {
            throw explain(e);
        }
    }

    public EtherScanDto<List<EtherScanErc20TransactionDto>> getErc20TxsByAddress(
        String address, String contractAddress, long startBlock, long endBlock, int page, int offset, String sort, String apiKeyToken
    ) throws IOException {
        try {
            return api.getErc20TxsByAddress(
                "1", "account", "tokentx", address, contractAddress, startBlock, endBlock, page, offset, sort, apiKeyToken
            );
        } catch (HttpStatusIOException e) {
            throw explain(e);
        }
    }

    public EtherScanDto<Long> getBlockNumberByTimestamp(String timeStamp, String closest, String apiKeyToken) throws IOException {
        try {
            return api.getBlockNumberByTimestamp("1", "block", "getblocknobytime", timeStamp, closest, apiKeyToken);
        } catch (HttpStatusIOException e) {
            throw explain(e);
        }
    }

    /**
     * Turns Etherscan's way of reporting a problem into a message a human can act on.
     * <p>
     * Etherscan answers HTTP 200 to a rejected request and puts the reason in the body as
     * {@code {"status":"0","message":"NOTOK","result":"Error! Invalid address format"}}. {@link EtherScanDto}
     * declares {@code result} as the payload type, so for the list endpoints that String is handed to Jackson
     * where a List is expected and deserialization throws. rescu wraps that as an
     * {@link HttpStatusIOException} whose own message is the Jackson complaint - which is why a bad address, a
     * spent rate limit and a revoked key all reached the customer as one red error with no text (Sentry issue
     * 152285123, 7 occurrences to 2026-10-08).
     * <p>
     * The body is still on the exception, so the reason can simply be read back out of it. Anything we cannot
     * parse is passed through untouched rather than replaced by a guess.
     */
    private static IOException explain(HttpStatusIOException e) {
        final String body = e.getHttpBody();
        if (body == null) {
            return e;
        }
        final Matcher result = Pattern.compile("\"result\"\\s*:\\s*\"([^\"]*)\"").matcher(body);
        if (result.find() && !result.group(1).isBlank()) {
            return new IOException("Etherscan rejected the request: " + result.group(1), e);
        }
        final Matcher message = Pattern.compile("\"message\"\\s*:\\s*\"([^\"]*)\"").matcher(body);
        if (message.find() && !message.group(1).isBlank()) {
            return new IOException("Etherscan rejected the request: " + message.group(1), e);
        }
        return e;
    }
}
