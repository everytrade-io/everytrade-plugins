package io.everytrade.server.plugin.impl.everytrade.parser.exchange.kraken;

public enum KrakenConstants {
    // Supported types
    TYPE_TRADE("trade"),
    TYPE_WITHDRAWAL("withdrawal"),
    TYPE_DEPOSIT("deposit"),
    TYPE_TRANSFER("transfer"),
    TYPE_SPEND("spend"),
    TYPE_RECEIVE("receive"),
    TYPE_STAKING("staking");

    public final String code;
    KrakenConstants(String code) {
        this.code = code;
    }

}
