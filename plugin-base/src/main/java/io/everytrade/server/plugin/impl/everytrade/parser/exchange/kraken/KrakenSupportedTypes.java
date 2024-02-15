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
        SUPPORTED_TYPES.add(KrakenConstants.TYPE_TRANSFER.code);
        SUPPORTED_TYPES.add(KrakenConstants.TYPE_SPEND.code);
        SUPPORTED_TYPES.add(KrakenConstants.TYPE_RECEIVE.code);
        SUPPORTED_TYPES.add(KrakenConstants.TYPE_STAKING.code);

        DUPLICABLE_TYPES.add(KrakenConstants.TYPE_WITHDRAWAL.code);
        DUPLICABLE_TYPES.add(KrakenConstants.TYPE_DEPOSIT.code);
        DUPLICABLE_TYPES.add(KrakenConstants.TYPE_TRANSFER.code);
    }
}
