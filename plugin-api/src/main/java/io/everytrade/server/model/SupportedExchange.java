package io.everytrade.server.model;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public enum SupportedExchange {
    BITSTAMP("Bitstamp", "bitstamp"),
    BITTREX("Bittrex", "bittrex"),
    GENERAL_BYTES("General Bytes", "generalbytes"),
    KRAKEN("Kraken", "kraken"),
    EVERYTRADE("EveryTrade", "everytrade"),
    COINMATE("CoinMate", "coinmate"),
    HITBTC("HitBTC", "hitbtc"),
    SHAKEPAY("ShakePay", "shakepay"),
    POLONIEX("Poloniex", "poloniex"),
    HUOBI("Huobi", "huobi"),
    LOCALBITCOINS("LocalBitcoins", "localbitcoins"),
    PAXFUL("Paxful", "paxful"),
    BITFINEX("Bitfinex", "bitfinex"),
    COINSQUARE("Coinsquare", "coinsquare"),
    BINANCE("Binance", "binance"),
    COINBASE_PRO("Coinbase Pro", "coinbasepro"),
    BITMEX("BitMEX", "bitmex"),
    BITFLYER("bitFlyer", "bitflyer"),
    OKEX("OKEX", "okex");

    private final String displayName;
    private final String internalId;

    private static final Map<String, SupportedExchange> BY_ID;

    static {
        BY_ID = new HashMap<>();
        for (SupportedExchange value : values()) {
            final SupportedExchange previousMapping = BY_ID.put(value.internalId, value);
            if (previousMapping != null) {
                throw new IllegalStateException(
                    String.format(
                        "Multiple constants map to '%s': '%s', '%s'.", value.internalId, value, previousMapping)
                );
            }
        }
    }

    SupportedExchange(String displayName, String internalId) {
        this.displayName = displayName;
        this.internalId = internalId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getInternalId() {
        return internalId;
    }

    public static Optional<SupportedExchange> fromId(String id) {
        return Optional.ofNullable(BY_ID.get(id));
    }
}
