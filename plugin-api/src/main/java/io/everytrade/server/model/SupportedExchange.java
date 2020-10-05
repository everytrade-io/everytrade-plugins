package io.everytrade.server.model;

//TODO: duplicate information with Exchange?
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
    COINBASE("Coinbase Pro", "coinbase"),
    BITMEX("BitMEX", "bitmex"),
    BITFLYER("bitFlyer", "bitflyer");


    private final String displayName;
    private final String internalId;

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
}
