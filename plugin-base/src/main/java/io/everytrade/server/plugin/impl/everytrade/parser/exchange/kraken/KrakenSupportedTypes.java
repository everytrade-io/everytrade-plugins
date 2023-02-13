package io.everytrade.server.plugin.impl.everytrade.parser.exchange.kraken;

import java.util.ArrayList;
import java.util.List;

public class KrakenSupportedTypes {

    public static final List<String> SUPPORTED_TYPES = new ArrayList<>();
    // types with duplicate rows
    public static final List<String> DUPLICABLE_TYPES = new ArrayList<>();

    static {
        // supported accounts
        SUPPORTED_TYPES.add(KrakenConstants.TYPE_TRADE.code);
        SUPPORTED_TYPES.add(KrakenConstants.TYPE_DEPOSIT.code);
        SUPPORTED_TYPES.add(KrakenConstants.TYPE_WITHDRAWAL.code);

        DUPLICABLE_TYPES.add(KrakenConstants.TYPE_WITHDRAWAL.code);
        DUPLICABLE_TYPES.add(KrakenConstants.TYPE_DEPOSIT.code);
    }
}
