package io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v2;

import java.util.HashMap;
import java.util.Map;

public enum Level1Header {
    DATE("Date"),
    PAIR("Pair"),
    TYPE("Type"),
    STATUS("status");

    private final String value;
    private static final Map<String, Level1Header> lookup = new HashMap<>();

    static {
        for (Level1Header header : Level1Header.values()) {
            lookup.put(header.getValue(), header);
        }
    }

    public static Level1Header get(String abbreviation) {
        if (abbreviation == null) {
            return null;
        }
        if (BinanceExchangeSpecificParser.DATE_PATTERN.matcher(abbreviation).find()) {
            return DATE;
        } else {
            return lookup.get(abbreviation);
        }
    }

    Level1Header(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

}

