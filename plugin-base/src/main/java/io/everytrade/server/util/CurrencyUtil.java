package io.everytrade.server.util;

import io.everytrade.server.model.Currency;
import java.util.HashMap;
import java.util.Map;

public class CurrencyUtil {

    private static final Map<String, Currency> CURRENCY_EXCEPTIONS = new HashMap<>();

    static {
        CURRENCY_EXCEPTIONS.put("XDG", Currency.DOGE);
        CURRENCY_EXCEPTIONS.put("RNDR", Currency.RENDER);
        CURRENCY_EXCEPTIONS.put("BEAMX", Currency.BEAM);
    }

    public static Currency fromString(String currencyName) {
        return CURRENCY_EXCEPTIONS.get(currencyName) != null ? CURRENCY_EXCEPTIONS.get(currencyName) : Currency.fromCode(currencyName);
    }

    public static String fromStringToString(String currencyName) {
        return CURRENCY_EXCEPTIONS.get(currencyName) != null ? CURRENCY_EXCEPTIONS.get(currencyName).getCode() : currencyName;
    }
}
