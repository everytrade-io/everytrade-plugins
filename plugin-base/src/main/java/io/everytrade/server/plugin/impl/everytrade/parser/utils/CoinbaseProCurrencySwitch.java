package io.everytrade.server.plugin.impl.everytrade.parser.utils;

import io.everytrade.server.model.Currency;

import java.util.HashMap;
import java.util.Map;

public class CoinbaseProCurrencySwitch {
    private static Map<String,Currency> CURRENCY_EXCEPTION = new HashMap<>();

    static {
        CURRENCY_EXCEPTION.put("CGLD", Currency.CELO);
   }

    public static Currency getCurrency(String currency) {
        if(CURRENCY_EXCEPTION.get(currency.toUpperCase()) != null) {
            return CURRENCY_EXCEPTION.get(currency.toUpperCase());
        } else {
            return Currency.fromCode(currency.toUpperCase());
        }
    }
}
