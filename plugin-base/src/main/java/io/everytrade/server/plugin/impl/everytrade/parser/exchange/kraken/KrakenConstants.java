package io.everytrade.server.plugin.impl.everytrade.parser.exchange.kraken;

public enum KrakenConstants {
    // Supported types
    TYPE_TRADE("trade"),
    TYPE_WITHDRAWAL("withdrawal"),
    TYPE_DEPOSIT("deposit");

    public final String code;
    KrakenConstants(String code) {
        this.code = code;
    }

}
