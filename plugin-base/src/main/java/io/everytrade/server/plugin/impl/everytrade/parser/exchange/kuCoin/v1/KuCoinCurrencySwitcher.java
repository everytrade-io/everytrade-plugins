package io.everytrade.server.plugin.impl.everytrade.parser.exchange.kuCoin.v1;

import io.everytrade.server.model.Currency;

import java.util.HashMap;
import java.util.Map;

import static io.everytrade.server.model.Currency.GALA;

public class KuCoinCurrencySwitcher {
    public static final Map<String, Currency> SWITCHER = new HashMap<>();

    static {
        SWITCHER.put("GALAX", GALA);
    }
}
