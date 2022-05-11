package io.everytrade.server.util;

import io.everytrade.server.model.Currency;

import java.util.HashMap;
import java.util.Map;

public class KrakenCurrencyUtil {

    private static final Map<String, Currency> CURRENCY_SHORT_CODES = new HashMap<>();
    private static final Map<String, Currency> CURRENCY_LONG_CODES = new HashMap<>();

    static {
        CURRENCY_SHORT_CODES.put("XBT", Currency.BTC);
        CURRENCY_LONG_CODES.put("XXBT", Currency.BTC);
        CURRENCY_SHORT_CODES.put("XDG", Currency.DOGE);
        CURRENCY_LONG_CODES.put("XXDG", Currency.DOGE);

        for (Currency value : Currency.values()) {
            if (value.equals(Currency.BTC)) {
                continue;
            }
            CURRENCY_SHORT_CODES.put(value.code(), value);
            if (value.isFiat()) {
                CURRENCY_LONG_CODES.put("Z" + value.code(), value);
            } else {
                CURRENCY_LONG_CODES.put("X" + value.code(), value);
            }
        }
    }

    public static Currency findCurrencyByCode(String code) {
        final Currency currencyLong = CURRENCY_LONG_CODES.get(code);
        if (currencyLong != null) {
            return currencyLong;
        }
        final Currency currencyShort = CURRENCY_SHORT_CODES.get(code);
        if (currencyShort != null) {
            return currencyShort;
        }
        throw new IllegalStateException(String.format("Currency not found for code %s.", code));
    }

}
