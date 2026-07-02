package io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean;

import com.univocity.parsers.common.DataValidationException;
import io.everytrade.server.model.Currency;
import io.everytrade.server.model.CurrencyPair;

import java.util.List;

import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.ExchangeBean.UNSUPPORTED_CURRENCY_PAIR;

/**
 * Shared helpers for the ByBit EU CSV beans.
 */
public final class BybitEuSupport {

    /**
     * Quote currencies ByBit EU spot markets are denominated in. Longer codes first so a symbol can never
     * match a shorter candidate that is a suffix of a longer one.
     */
    private static final List<Currency> QUOTE_CANDIDATES = List.of(Currency.USDC, Currency.USDT, Currency.EUR, Currency.BTC);

    private BybitEuSupport() {
    }

    /**
     * Splits a concatenated ByBit spot symbol (e.g. "BTCEUR") into a currency pair by matching a known quote
     * suffix and validating the remaining prefix as the base currency.
     *
     * <p>Deterministic by design — a lookup map keyed by concatenated pair names built from the full
     * currency cross product is ambiguous for symbols like "USDCEUR" (USDC/EUR vs USD/CEUR) and its
     * resolution would depend on hash iteration order.
     */
    public static CurrencyPair parseSpotPair(String symbol) {
        if (symbol != null) {
            for (Currency quote : QUOTE_CANDIDATES) {
                final String quoteCode = quote.code();
                if (symbol.endsWith(quoteCode) && symbol.length() > quoteCode.length()) {
                    try {
                        return new CurrencyPair(Currency.fromCode(symbol.substring(0, symbol.length() - quoteCode.length())), quote);
                    } catch (IllegalArgumentException ignored) {
                        // prefix is not a known currency - try the next quote candidate
                    }
                }
            }
        }
        throw new DataValidationException(UNSUPPORTED_CURRENCY_PAIR.concat(String.valueOf(symbol)));
    }
}
