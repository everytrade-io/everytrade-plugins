package io.everytrade.server.util;

import io.everytrade.server.model.Currency;

import java.util.HashMap;
import java.util.Map;

public class KrakenCurrencyUtil {

    public static final Map<String, Currency> CURRENCY_SHORT_CODES = new HashMap<>();
    public static final Map<String, Currency> CURRENCY_LONG_CODES = new HashMap<>();
    public static final Map<String, Currency> CURRENCY_EXCEPTION_CODES = new HashMap<>();

    static {
        CURRENCY_SHORT_CODES.put("XBT", Currency.BTC);
        CURRENCY_LONG_CODES.put("XXBT", Currency.BTC);

        CURRENCY_LONG_CODES.put("XXLT", Currency.LTC);
        CURRENCY_SHORT_CODES.put("XLTC", Currency.LTC);
        CURRENCY_LONG_CODES.put("XXLTC", Currency.LTC);

        CURRENCY_SHORT_CODES.put("XDG", Currency.DOGE);
        CURRENCY_LONG_CODES.put("XXDG", Currency.DOGE);

        CURRENCY_EXCEPTION_CODES.put("ATOM21", Currency.ATOM);
        CURRENCY_EXCEPTION_CODES.put("KAVA21", Currency.KAVA);
        CURRENCY_EXCEPTION_CODES.put("DOT28", Currency.DOT);
        CURRENCY_EXCEPTION_CODES.put("ETH2", Currency.ETH);

        for (Currency value : Currency.values()) {
            if (value.equals(Currency.BTC)) {
                continue;
            }
            if (value.equals(Currency.LTC)) {
                continue;
            }
            if (value.equals(Currency.DOGE)) {
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
        // Staking
        code = code.replace(".M", "")
            .replace(".S", "")
            .replace(".P", "");

        final Currency currencyLong = CURRENCY_LONG_CODES.get(code);
        if (currencyLong != null) {
            return currencyLong;
        }
        final Currency currencyShort = CURRENCY_SHORT_CODES.get(code);
        if (currencyShort != null) {
            return currencyShort;
        }
        final Currency exceptionCurrency = CURRENCY_EXCEPTION_CODES.get(code);
        if (exceptionCurrency != null) {
            return exceptionCurrency;
        }
        try {
            return Currency.fromCode(code);
        } catch (IllegalArgumentException e) {

        }
        throw new IllegalStateException(String.format("Currency not found for code %s.", code));
    }

}
