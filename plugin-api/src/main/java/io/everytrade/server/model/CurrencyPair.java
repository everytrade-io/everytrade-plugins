package io.everytrade.server.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class CurrencyPair implements Comparable<CurrencyPair>{

    private final Currency base;
    private final Currency quote;

    private static final Set<Currency> ALLOWED_CRYPTO_QUOTES =
        Set.of(Currency.USDT, Currency.BTC, Currency.ETH, Currency.BNB);

    public enum CurrencyPosition {BASE, QUOTE}
    public static final Comparator<CurrencyPair> COMPARATOR =
        Comparator
            .comparing(CurrencyPair::getBase)
            .thenComparing(CurrencyPair::getQuote);



    public CurrencyPair(String base, String quote) {
        this(Currency.fromCode(base), Currency.fromCode(quote));
    }

    public CurrencyPair(Currency base, Currency quote) {
        Objects.requireNonNull(this.base = base);
        Objects.requireNonNull(this.quote = quote);
        if(this.base.isFiat() && !this.quote.isFiat()) {
            throw new FiatCryptoCombinationException(this.base, this.quote);
        }
    }

    public Currency getBase() {
        return base;
    }

    public Currency getQuote() {
        return quote;
    }

    public CurrencyPair reverse() {
        return new CurrencyPair(quote, base);
    }

    public Instant getIntroduction() {
        return base.getIntroduction().compareTo(quote.getIntroduction()) >= 0
            ? base.getIntroduction()
            : quote.getIntroduction();
    }

    public static List<CurrencyPair> getTradeablePairs() {
        List<CurrencyPair> currencyPairs = new ArrayList<>();
        for (Currency base : Currency.values()) {
            for (Currency quote : Currency.values()) {
                final boolean baseIsCrypto = !base.isFiat();
                final boolean baseQuoteDiffer = base != quote;
                final boolean quoteIsFiat = quote.isFiat();
                final boolean quoteIsAllowedCrypto = ALLOWED_CRYPTO_QUOTES.contains(quote);
                final boolean quoteIsAllowed = quoteIsFiat || quoteIsAllowedCrypto;
                final boolean isUnsupportedCryptoPair = isUnsupportedCryptoPairs(base, quote);
                if (baseIsCrypto && baseQuoteDiffer && quoteIsAllowed && !isUnsupportedCryptoPair) {
                    currencyPairs.add(new CurrencyPair(base, quote));
                }
            }
        }
        currencyPairs.addAll(getSupportedFiatPairs());
        return currencyPairs;
    }

    public static List<CurrencyPair> getSupportedFiatPairs() {
        List<CurrencyPair> currencyPairs = new ArrayList<>();
        currencyPairs.add(new CurrencyPair(Currency.USD, Currency.CAD));
        currencyPairs.add(new CurrencyPair(Currency.USD, Currency.CZK));

        currencyPairs.add(new CurrencyPair(Currency.CAD, Currency.CZK));

        currencyPairs.add(new CurrencyPair(Currency.EUR, Currency.USD));
        currencyPairs.add(new CurrencyPair(Currency.EUR, Currency.CAD));
        currencyPairs.add(new CurrencyPair(Currency.EUR, Currency.CZK));
        currencyPairs.add(new CurrencyPair(Currency.EUR, Currency.GBP));
        currencyPairs.add(new CurrencyPair(Currency.EUR, Currency.AUD));

        currencyPairs.add(new CurrencyPair(Currency.GBP, Currency.USD));
        currencyPairs.add(new CurrencyPair(Currency.GBP, Currency.CAD));
        currencyPairs.add(new CurrencyPair(Currency.GBP, Currency.CZK));
        currencyPairs.add(new CurrencyPair(Currency.GBP, Currency.AUD));

        currencyPairs.add(new CurrencyPair(Currency.AUD, Currency.USD));
        currencyPairs.add(new CurrencyPair(Currency.AUD, Currency.CAD));
        currencyPairs.add(new CurrencyPair(Currency.AUD, Currency.CZK));

        return currencyPairs;
    }

    @Override
    public int compareTo(CurrencyPair currencyPair) {
        return COMPARATOR.compare(this, currencyPair);
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        CurrencyPair that = (CurrencyPair) o;

        if (!base.equals(that.base)) {
            return false;
        }
        return quote.equals(that.quote);
    }

    @Override
    public int hashCode() {
        int result = base.hashCode();
        result = 31 * result + quote.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return String.format("%s/%s", base, quote);
    }

    public static class FiatCryptoCombinationException extends RuntimeException {

        public static final String INVALID_CURRENCY_PAIR = "Invalid currency pair";

        public FiatCryptoCombinationException(Currency base, Currency quote) {
            super(
                String.format("%s - fiat (%s) to crypto (%s): ", INVALID_CURRENCY_PAIR, base, quote)
            );
        }

    }

    private static boolean isUnsupportedCryptoPairs(Currency base, Currency quote) {
        if (Currency.USDT.equals(base)) {
            return Currency.BTC.equals(quote) || Currency.ETH.equals(quote) || Currency.BNB.equals(quote);
        } else if (Currency.BTC.equals(base)) {
            return  Currency.ETH.equals(quote) || Currency.BNB.equals(quote);
        } else {
            return base.equals(Currency.ETH) && quote.equals(Currency.BNB);
        }
    }
}
