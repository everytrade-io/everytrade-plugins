package io.everytrade.server.plugin.impl.everytrade.parser.exchange.coibasePro.v2;

import java.util.Arrays;
import java.util.Optional;

public enum CoinbaseProConstantsV2 {
    // Supported account types
    TYPE_DEPOSIT("deposit"),
    TYPE_WITHDRAWAL("withdrawal"),
    TYPE_MATCH("match"),
    TYPE_FEE("fee");

    public final String code;

    CoinbaseProConstantsV2(String code) {
        this.code = code;
    }

    public static Optional<CoinbaseProConstantsV2> getEnum(String s) {
        return Arrays.stream(CoinbaseProConstantsV2.values()).filter(e -> e.code.equals(s))
            .findFirst();
    }
}
