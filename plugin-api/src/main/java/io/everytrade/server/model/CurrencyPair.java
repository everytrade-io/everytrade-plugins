package io.everytrade.server.model;

import lombok.Getter;
import lombok.NonNull;
import lombok.experimental.FieldDefaults;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static io.everytrade.server.model.Currency.BNB;
import static io.everytrade.server.model.Currency.BTC;
import static io.everytrade.server.model.Currency.BUSD;
import static io.everytrade.server.model.Currency.DAI;
import static io.everytrade.server.model.Currency.ETH;
import static io.everytrade.server.model.Currency.USDC;
import static io.everytrade.server.model.Currency.USDT;
import static lombok.AccessLevel.PRIVATE;

@Getter
@FieldDefaults(level = PRIVATE)
public final class CurrencyPair implements Comparable<CurrencyPair> {

    private static final Set<Currency> ALLOWED_CRYPTO_QUOTES = Set.of(USDT, BTC, ETH, BNB, BUSD, USDC, DAI);

    @NonNull Currency base;
    @NonNull Currency quote;

    public enum CurrencyPosition {BASE, QUOTE}
    public static final Comparator<CurrencyPair> COMPARATOR =
        Comparator
            .comparing(CurrencyPair::getBase)
            .thenComparing(CurrencyPair::getQuote);

    public CurrencyPair(String base, String quote) {
        this(Currency.fromCode(base), Currency.fromCode(quote));
    }

    public CurrencyPair(Currency base, Currency quote) {
        this.base = base;
        this.quote = quote;
        if(this.base.isFiat() && !this.quote.isFiat()) {
            throw new FiatCryptoCombinationException(this.base, this.quote);
        }
    }

    public CurrencyPair reverse() {
        return new CurrencyPair(quote, base);
    }

    public Instant getIntroduction() {
        return base.getIntroduction().compareTo(quote.getIntroduction()) >= 0
            ? base.getIntroduction()
            : quote.getIntroduction();
    }

    public Instant getEndDate() {
        if (base.getEndDate() == null) {
            return quote.getEndDate();
        }
        if (quote.getEndDate() == null) {
            return base.getEndDate();
        }
        return base.getEndDate().isBefore(quote.getEndDate()) ? base.getEndDate() : quote.getEndDate();
    }

    public static List<CurrencyPair> getTradeablePairs() {
        Set<CurrencyPair> currencyPairs = new HashSet<>();
        for (Currency base : Currency.values()) {
            for (Currency quote : Currency.values()) {
                var baseIsCrypto = !base.isFiat();
                var quoteIsFiat = quote.isFiat();
                var quoteIsAllowedCrypto = ALLOWED_CRYPTO_QUOTES.contains(quote);
                var quoteIsAllowed = quoteIsFiat || quoteIsAllowedCrypto;
                var isUnsupportedCryptoPair = isUnsupportedCryptoPairs(base, quote);
                if ((baseIsCrypto && quoteIsAllowed && !isUnsupportedCryptoPair) || (base == quote)) {
                    currencyPairs.add(new CurrencyPair(base, quote));
                }
            }
        }
        currencyPairs.addAll(getSupportedFiatPairs());
        return new ArrayList<>(currencyPairs);
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

    private static boolean isUnsupportedCryptoPairs(Currency base, Currency quote) {
        if (USDT.equals(base)) {
            return BTC.equals(quote) || ETH.equals(quote) || BNB.equals(quote);
        } else if (BTC.equals(base)) {
            return  ETH.equals(quote) || BNB.equals(quote);
        } else {
            return base.equals(ETH) && quote.equals(BNB);
        }
    }

    public static class FiatCryptoCombinationException extends RuntimeException {

        public static final String INVALID_CURRENCY_PAIR = "Invalid currency pair";

        public FiatCryptoCombinationException(Currency base, Currency quote) {
            super(
                String.format("%s - fiat (%s) to crypto (%s): ", INVALID_CURRENCY_PAIR, base, quote)
            );
        }
    }
}
