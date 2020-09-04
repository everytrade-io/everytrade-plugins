package io.everytrade.server.plugin.impl.everytrade;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class BinanceDownloadState {
    private final Map<String, String> pairLastIds;

    private BinanceDownloadState(Map<String, String> pairLastIds) {
        this.pairLastIds = pairLastIds;
    }

    public static BinanceDownloadState parseFrom(String lastTransactionId) {
        return new BinanceDownloadState(convertToMap(lastTransactionId));
    }

    public String getLastTransactionId(String pair) {
        Objects.requireNonNull(pair);
        return pairLastIds.get(pair);
    }

    public void updateLastTransactionId(String pair, String lastId) {
        Objects.requireNonNull(pair);
        pairLastIds.put(pair, lastId);
    }

    public String toLastTransactionId() {
        return convertFromMap(pairLastIds);
    }

    private String convertFromMap(Map<String, String> map) {
        return map.keySet().stream()
            .map(key -> key + "=" + map.get(key))
            .collect(Collectors.joining(":"));
    }

    private static Map<String, String> convertToMap(String mapAsString) {
        if (mapAsString == null) {
            return new HashMap<>();
        }
        return Arrays.stream(mapAsString.split(":"))
            .map(entry -> entry.split("="))
            .collect(Collectors.toMap(entry -> entry[0], entry -> entry[1]));
    }
}