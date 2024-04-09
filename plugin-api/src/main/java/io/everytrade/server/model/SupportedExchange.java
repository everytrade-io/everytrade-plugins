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
    ANYCOIN("Anycoin", "anycoin"),
    AQUANOW("Aquanow", "aquanow"),
    BINANCE("Binance", "binance"),
    BITSTAMP("Bitstamp", "bitstamp"),
    BITTREX("Bittrex", "bittrex"),
    BITMEX("BitMEX", "bitmex"),
    BITFLYER("bitFlyer", "bitflyer"),
    BITFINEX("Bitfinex", "bitfinex"),
    BLOCKCHAINBTC("Blockchain BTC", "blockchainbtc"),
    BLOCKCHAINETH("Blockchain ETH", "blockchaineth"),
    BLOCKCHAINLTC("Blockchain LTC", "blockchainltc"),
    COINBASE("Coinbase", "coinbase"),
    COINBASE_PRO("Coinbase Pro", "coinbasepro"),
    COINMATE("Coinmate", "coinmate"),
    COINSQUARE("Coinsquare", "coinsquare"),
    DVCHAIN("DV Chain", "dvchain"),
    EVERYTRADE("WhaleBooks", "everytrade"),
    GENERAL_BYTES("General Bytes CAS", "generalbytes"),
    HITBTC("HitBTC", "hitbtc"),
    HUOBI("Huobi", "huobi"),
    KUCOIN("KuCoin", "kucoin"),
    KRAKEN("Kraken", "kraken"),
    LOCALBITCOINS("LocalBitcoins", "localbitcoins"),
    OKX("OKX", "okex"),
    OPEN_NODE("OpenNode", "opennode"),
    PAXFUL("Paxful", "paxful"),
    POLONIEX("Poloniex", "poloniex"),
    SHAKEPAY("ShakePay", "shakepay"),
    SIMPLE_COIN("SimpleCoin", "simplecoin");


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

