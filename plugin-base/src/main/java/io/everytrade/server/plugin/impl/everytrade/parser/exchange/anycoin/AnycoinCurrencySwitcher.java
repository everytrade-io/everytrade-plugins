package io.everytrade.server.plugin.impl.everytrade.parser.exchange.anycoin;

import io.everytrade.server.model.Currency;

import java.util.HashMap;
import java.util.Map;

public class AnycoinCurrencySwitcher {
    public static final Map<String, Currency> switcher = new HashMap<>();

    static {
        switcher.put("XDG", Currency.DOGE);
    }

}
