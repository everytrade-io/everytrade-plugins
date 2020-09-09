package io.everytrade.server.plugin.impl.everytrade;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class CoinbaseProDownloadState {
    private final Map<String, Integer> currencyPairLastIds;

    private CoinbaseProDownloadState(Map<String, Integer> currencyPairLastIds) {
        Objects.requireNonNull(this.currencyPairLastIds = currencyPairLastIds);
    }

    public static CoinbaseProDownloadState parseFrom(String lastTransactionId) {
        return new CoinbaseProDownloadState(convertToMap(lastTransactionId));
    }

    public Integer getLastTransactionId(String pair) {
        Objects.requireNonNull(pair);
        return currencyPairLastIds.get(pair);
    }

    public void updateLastTransactionId(String pair, Integer lastId) {
        Objects.requireNonNull(pair);
        currencyPairLastIds.put(pair, lastId);
    }

    public String toLastTransactionId() {
        return convertFromMap(currencyPairLastIds);
    }

    private String convertFromMap(Map<String, Integer> map) {
        return map.keySet().stream()
                .map(key -> key + "=" + map.get(key))
                .collect(Collectors.joining(":"));
    }

    private static Map<String, Integer> convertToMap(String mapAsString) {
        if (mapAsString == null) {
            return new HashMap<>();
        }
        return Arrays.stream(mapAsString.split(":"))
                .map(entry -> entry.split("="))
                .collect(Collectors.toMap(entry -> entry[0], entry -> Integer.parseInt(entry[1])));
    }
}