package io.everytrade.server.plugin.impl.everytrade.parser.exchange.kraken;

import java.util.ArrayList;
import java.util.List;

public enum KrakenAssetCodeType {
    STAKED(".S"),
    STAKED_BEARING(".B"),
    STAKING_REWARDS(".M"),
    EARNING_REWARDS(".F"),
    STAKING("03.S");


    String code;

    KrakenAssetCodeType(String code) {
        this.code = code;
    }

    public static KrakenAssetCodeType getEnumByCode(String code) {
        for (KrakenAssetCodeType value : KrakenAssetCodeType.values()) {
            if (value.code.equals(code)) {
                return value;
            }
        }
        throw new IllegalArgumentException("Unknown enum code: " + code);
    }

    public static List<String> getAllAssetCodes() {
        List<String> allCodes = new ArrayList<>();
        for (KrakenAssetCodeType value : KrakenAssetCodeType.values()) {
            allCodes.add(value.code);
        }
        return allCodes;
    }

    public static KrakenAssetCodeType findAssetCodeByAsset(String asset) {
        KrakenAssetCodeType res = null;
        for (String code : getAllAssetCodes()) {
            if (asset.endsWith(code)) {
                res = getEnumByCode(code);
            }
        }
        return res;
    }

}
