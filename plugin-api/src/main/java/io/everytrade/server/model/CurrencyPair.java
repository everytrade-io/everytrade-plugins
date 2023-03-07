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

import static lombok.AccessLevel.PRIVATE;

@Getter
@FieldDefaults(level = PRIVATE, makeFinal = true)
public final class CurrencyPair implements Comparable<CurrencyPair> {

    @NonNull Currency base;
    @NonNull Currency quote;

    private static final Set<CurrencyPair> CURRENCY_PAIRS = new HashSet<>();

    static {
        for (Currency base : Currency.values()) {
            for (Currency quote : Currency.values()) {
                CURRENCY_PAIRS.add(new CurrencyPair(base, quote));
            }
        }
    }

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
        return new ArrayList<>(CURRENCY_PAIRS);
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
}
