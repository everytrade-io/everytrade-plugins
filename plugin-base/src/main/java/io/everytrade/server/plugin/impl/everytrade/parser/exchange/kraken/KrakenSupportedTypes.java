package io.everytrade.server.plugin.impl.everytrade.parser.exchange.kraken;

import io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v4.BinanceConstantsV4;

import java.util.ArrayList;
import java.util.List;

import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v4.BinanceSupportedOperations.SUPPORTED_OPERATION_TYPES;

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
