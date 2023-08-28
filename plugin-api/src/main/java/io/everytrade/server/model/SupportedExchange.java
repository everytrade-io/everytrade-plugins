package io.everytrade.server.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static lombok.AccessLevel.PRIVATE;

@Getter
@AllArgsConstructor
@FieldDefaults(level = PRIVATE, makeFinal = true)
public enum SupportedExchange {
    BITSTAMP("Bitstamp", "bitstamp"),
    BITTREX("Bittrex", "bittrex"),
    GENERAL_BYTES("General Bytes CAS", "generalbytes"),
    KRAKEN("Kraken", "kraken"),
    EVERYTRADE("WhaleBooks", "everytrade"),
    COINMATE("Coinmate", "coinmate"),
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
    OKX("OKX", "okex"),
    COINBASE("Coinbase", "coinbase"),
    BLOCKCHAINBTC("Blockchain BTC", "blockchainbtc"),
    BLOCKCHAINETH("Blockchain ETH", "blockchaineth"),
    BLOCKCHAINLTC("Blockchain LTC", "blockchainltc"),
    DVCHAIN("DV Chain", "dvchain"),
    AQUANOW("Aquanow", "aquanow"),
    SIMPLE_COIN("SimpleCoin", "simplecoin"),
    OPEN_NODE("OpenNode", "opennode"),
    KUCOIN("KuCoin", "kucoin");

    String displayName;
    String internalId;

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

    public static Optional<SupportedExchange> fromId(String id) {
        return Optional.ofNullable(BY_ID.get(id));
    }
}

