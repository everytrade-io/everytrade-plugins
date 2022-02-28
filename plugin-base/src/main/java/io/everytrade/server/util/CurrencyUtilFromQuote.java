package io.everytrade.server.util;

import io.everytrade.server.model.Currency;
import java.util.HashMap;
import java.util.Map;

public class CurrencyUtilFromQuote {

    private static final Map<String, Currency> CURRENCY_EXCEPTIONS = new HashMap<>();

    static {
        CURRENCY_EXCEPTIONS.put("XDG", Currency.DOGE);
    }

    public Currency currencyCodeSwitcher(String currencyName) {
        return CURRENCY_EXCEPTIONS.get(currencyName) != null ? CURRENCY_EXCEPTIONS.get(currencyName) : Currency.fromCode(currencyName);
    }
}
