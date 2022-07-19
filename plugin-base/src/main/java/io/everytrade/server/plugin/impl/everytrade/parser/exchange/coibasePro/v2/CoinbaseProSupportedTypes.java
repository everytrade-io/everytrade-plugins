package io.everytrade.server.plugin.impl.everytrade.parser.exchange.coibasePro.v2;

import java.util.ArrayList;
import java.util.List;

public class CoinbaseProSupportedTypes {

    public static final List<String> SUPPORTED_TYPES = new ArrayList<>();

    static {
        // supported types
        SUPPORTED_TYPES.add(CoinbaseProConstantsV2.TYPE_DEPOSIT.code);
        SUPPORTED_TYPES.add(CoinbaseProConstantsV2.TYPE_WITHDRAWAL.code);
        SUPPORTED_TYPES.add(CoinbaseProConstantsV2.TYPE_MATCH.code);
        SUPPORTED_TYPES.add(CoinbaseProConstantsV2.TYPE_FEE.code);
    }
}
