package io.everytrade.server.util;

import com.univocity.parsers.common.DataValidationException;
import io.everytrade.server.model.Currency;
import io.everytrade.server.model.CurrencyPair;

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
        CURRENCY_LONG_CODES.put("EUR.HOLD", Currency.EUR);
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

    /**
     * Resolves any Kraken pair string to a {@link CurrencyPair} without guessing when the answer is already given:
     * <ol>
     *     <li>if the pair carries the {@code /} delimiter that modern Kraken exports provide (e.g. {@code USDC/EUR}),
     *     split on it - that is the authoritative answer;</li>
     *     <li>otherwise split a delimiter-less code (e.g. {@code USDCEUR}, {@code XXBTZUSD}) preferring the
     *     <b>longest</b> valid base, resolving each side through {@link #resolvePairCode(String)} which also
     *     knows Kraken's own codes ({@code XBT -> BTC}, {@code XDG -> DOGE}, {@code ZEUR -> EUR}, ...).</li>
     * </ol>
     * Both paths avoid the old greedy first-match that mis-parsed {@code USDCEUR} as {@code USD + CEUR}.
     */
    public static CurrencyPair parseKrakenPair(String rawPair) {
        if (rawPair == null) {
            throw new DataValidationException("Pair code is null.");
        }
        final String pair = rawPair.trim().replace("\"", "");
        if (pair.isEmpty()) {
            throw new DataValidationException("Pair code is empty.");
        }
        if (pair.contains("/")) {
            final String[] parts = pair.split("/", 2);
            if (parts[0].isEmpty() || parts[1].isEmpty()) {
                throw new DataValidationException(String.format("Can not parse pair %s.", pair));
            }
            try {
                return new CurrencyPair(resolvePairCode(parts[0].trim()), resolvePairCode(parts[1].trim()));
            } catch (IllegalArgumentException e) {
                throw new DataValidationException(String.format("Can not parse pair %s.", pair));
            }
        }
        return findStandardPair(pair);
    }

    /**
     * Splits a delimiter-less pair code preferring the split that yields the <b>longest</b> valid base, so
     * {@code USDCEUR} resolves to {@code USDC + EUR} and never to the greedy first match {@code USD + CEUR}.
     * Each side is resolved via {@link #resolvePairCode(String)}, so Kraken's own codes (e.g. {@code XXBTZUSD},
     * {@code AAVEXBT}) are handled too.
     */
    public static CurrencyPair findStandardPair(String pair) {
        for (int i = pair.length() - 1; i >= 1; i--) {
            try {
                final Currency base = resolvePairCode(pair.substring(0, i));
                final Currency quote = resolvePairCode(pair.substring(i));
                return new CurrencyPair(base, quote);
            } catch (IllegalArgumentException e) {
                // Not a valid split at this position - try a shorter base.
            }
        }
        throw new DataValidationException(String.format("Can not parse pair %s.", pair));
    }

    /**
     * Resolves a single pair-side code to a {@link Currency} using Kraken's own code maps first
     * (e.g. {@code XBT -> BTC}, {@code XDG -> DOGE}, {@code ZEUR -> EUR}) and then a plain
     * {@link Currency#fromCode(String)}. Unlike {@link #findCurrencyByCode(String)} it applies no staking-suffix
     * or trailing-digit stripping, so it never mis-maps a pair side (e.g. it keeps {@code ETHW} as {@code ETHW}).
     * Throws {@link IllegalArgumentException} when the code is unknown.
     */
    private static Currency resolvePairCode(String code) {
        final Currency longCode = CURRENCY_LONG_CODES.get(code);
        if (longCode != null) {
            return longCode;
        }
        final Currency shortCode = CURRENCY_SHORT_CODES.get(code);
        if (shortCode != null) {
            return shortCode;
        }
        return Currency.fromCode(code);
    }
}
