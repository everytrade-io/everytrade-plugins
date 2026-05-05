package io.everytrade.server.plugin.impl.everytrade.helius;

import si.mazi.rescu.RestProxyFactory;

import java.io.IOException;

public class HeliusClient {
    private static final String HELIUS_URL = "https://api.helius.xyz/";

    private final HeliusWalletAPI api;

    public HeliusClient() {
        this.api = RestProxyFactory.createProxy(HeliusWalletAPI.class, HELIUS_URL);
    }

    public HeliusResponseDto getTransactionHistory(String address, String apiKey, int limit, String before)
        throws IOException {
        return api.getTransactionHistory(address, apiKey, limit, before);
    }
}
