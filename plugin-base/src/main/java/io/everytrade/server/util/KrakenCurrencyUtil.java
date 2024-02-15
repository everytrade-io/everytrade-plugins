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
        CURRENCY_SHORT_CODES.put("XETC", Currency.ETC);


        CURRENCY_SHORT_CODES.put("XDG", Currency.DOGE);
        CURRENCY_LONG_CODES.put("XXDG", Currency.DOGE);

        CURRENCY_EXCEPTION_CODES.put("ATOM21", Currency.ATOM);
        CURRENCY_EXCEPTION_CODES.put("KAVA21", Currency.KAVA);
        CURRENCY_EXCEPTION_CODES.put("DOT28", Currency.DOT);
        CURRENCY_EXCEPTION_CODES.put("ETH2", Currency.ETH);

        CURRENCY_SHORT_CODES.put("ZUSD", Currency.USD);
        CURRENCY_SHORT_CODES.put("ZJPY", Currency.JPY);
        CURRENCY_SHORT_CODES.put("ZGBP", Currency.GBP);
        CURRENCY_SHORT_CODES.put("ZEUR", Currency.EUR);
        CURRENCY_SHORT_CODES.put("ZCHF", Currency.CHF);
        CURRENCY_SHORT_CODES.put("ZCAD", Currency.CAD);
        CURRENCY_SHORT_CODES.put("ZAUD", Currency.AUD);
        CURRENCY_SHORT_CODES.put("ZRX", Currency.ZRX);
        CURRENCY_SHORT_CODES.put("XZEC", Currency.ZEC);
        CURRENCY_LONG_CODES.put("XXVN", Currency.VEN);
        CURRENCY_LONG_CODES.put("XXTZ", Currency.XTZ);
        CURRENCY_LONG_CODES.put("XXRP", Currency.XRP);
        CURRENCY_LONG_CODES.put("XXMR", Currency.XMR);
        CURRENCY_LONG_CODES.put("XXLM", Currency.XLM);
        CURRENCY_LONG_CODES.put("XXDG", Currency.DOGE);
        CURRENCY_LONG_CODES.put("XXBT", Currency.BTC);
        CURRENCY_SHORT_CODES.put("XBT", Currency.BTC);
        CURRENCY_LONG_CODES.put("XREPV2", Currency.REPV2);
        CURRENCY_LONG_CODES.put("XREP", Currency.REP);
        CURRENCY_LONG_CODES.put("XMLN", Currency.MLN);
        CURRENCY_LONG_CODES.put("XLTC", Currency.LTC);
        CURRENCY_LONG_CODES.put("XETH", Currency.ETH);
        CURRENCY_LONG_CODES.put("XETC", Currency.ETC);
        CURRENCY_LONG_CODES.put("XXLTC", Currency.LTC);
        CURRENCY_SHORT_CODES.put("XDG", Currency.DOGE);

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
            .replace(".P", "")
            .replace("ETHW", "ETH");

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
            try {
                code = removeDigitsFromEndOfString(code);
                return Currency.fromCode(code);

            } catch (IllegalArgumentException ignore) {
            }
        }
        throw new IllegalStateException(String.format("Currency not found for code %s.", code));
    }

    private static String removeDigitsFromEndOfString(String value) {
        int i;
        for (i = value.length(); i > 0; --i) {
            char[] chars = value.substring(0, i).toCharArray();
            if (Character.isDigit(chars[i-1])) {
                value = value.substring(0, i - 1);
            } else {
                break;
            }
        }
        return value;
    }

    public static Currency fromCode(String code) {
        final Currency currencyLong = CURRENCY_LONG_CODES.get(code);
        if (currencyLong != null) {
            return currencyLong;
        }
        final Currency currencyShort = CURRENCY_SHORT_CODES.get(code);
        if (currencyShort != null) {
            return currencyShort;
        }
        return Currency.fromCode(code);
    }

}
