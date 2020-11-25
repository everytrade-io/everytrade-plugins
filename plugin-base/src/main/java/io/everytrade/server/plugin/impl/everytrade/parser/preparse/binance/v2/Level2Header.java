package io.everytrade.server.plugin.impl.everytrade.parser.preparse.binance.v2;

import java.util.HashMap;
import java.util.Map;


public enum Level2Header {
    DATE("Date"),
    TRADING_PRICE("Trading Price"),
    FILLED("Filled"),
    TOTAL("Total"),
    FEE("Fee");

    private final String value;
    private static final Map<String, Level2Header> lookup = new HashMap<>();
    static {
        for (Level2Header header : Level2Header.values()) {
            lookup.put(header.getValue(), header);
        }
    }

    public static Level2Header get(String abbreviation) {
        if (abbreviation == null) {
            return null;
        }
        if (BinanceExchangeParser.DATE_PATTERN.matcher(abbreviation).find()) {
            return DATE;
        } else {
            return lookup.get(abbreviation);
        }
    }

    Level2Header(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
