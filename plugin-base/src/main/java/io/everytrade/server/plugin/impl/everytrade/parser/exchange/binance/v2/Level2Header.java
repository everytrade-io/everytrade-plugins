package io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v2;

import java.util.HashMap;
import java.util.Map;


public enum Level2Header {
    DATE("Date"),
    TRADING_PRICE("Trading Price"),
    FILLED("Filled"),
    TOTAL("Total"),
    FEE("Fee");

    private final String value;
    private static final Map<String, Level2Header> LOOKUP = new HashMap<>();
    static {
        for (Level2Header header : Level2Header.values()) {
            LOOKUP.put(header.getValue(), header);
        }
    }

    public static Level2Header get(String abbreviation) {
        if (abbreviation == null) {
            return null;
        }
        if (BinanceExchangeSpecificParser.DATE_PATTERN.matcher(abbreviation).find()) {
            return DATE;
        } else {
            return LOOKUP.get(abbreviation);
        }
    }

    Level2Header(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
