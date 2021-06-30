package io.everytrade.server.plugin.api.rateprovider;

import io.everytrade.server.model.Currency;
import io.everytrade.server.model.CurrencyPair;
import io.everytrade.server.plugin.utils.ComparableUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Objects;

public class Rate {
    private final BigDecimal value;
    private final Currency base;
    private final Currency quote;
    private final Instant validityStartIncl;
    private final Instant validityEndExcl;
    private final RateSourceType sourceType;
    private final CachingStrategy cachingStrategy;

    public Rate(
        BigDecimal value,
        CurrencyPair pair,
        Instant validityStartIncl,
        Instant validityEndExcl,
        RateSourceType sourceType,
        CachingStrategy cachingStrategy
    ) {
        this(value, pair.getBase(), pair.getQuote(), validityStartIncl, validityEndExcl, sourceType, cachingStrategy);
    }

    public Rate(
        BigDecimal value,
        String base,
        String quote,
        Instant validityStartIncl,
        Instant validityEndExcl,
        RateSourceType sourceType,
        CachingStrategy cachingStrategy
    ) {
        this(
            value,
            Currency.fromCode(base),
            Currency.fromCode(quote),
            validityStartIncl,
            validityEndExcl,
            sourceType,
            cachingStrategy
        );
    }

    public Rate(
        BigDecimal value,
        Currency base,
        Currency quote,
        Instant validityStartIncl,
        Instant validityEndExcl,
        RateSourceType sourceType,
        CachingStrategy cachingStrategy
    ) {
        Objects.requireNonNull(this.value = value);
        Objects.requireNonNull(this.base = base);
        Objects.requireNonNull(this.quote = quote);
        Objects.requireNonNull(this.validityStartIncl = validityStartIncl);
        Objects.requireNonNull(this.validityEndExcl = validityEndExcl);
        Objects.requireNonNull(this.sourceType = sourceType);
        Objects.requireNonNull(this.cachingStrategy = cachingStrategy);
    }

    public BigDecimal getValue() {
        return value;
    }

    public Currency getBase() {
        return base;
    }

    public Currency getQuote() {
        return quote;
    }

    public Instant getValidityStartIncl() {
        return validityStartIncl;
    }

    public Instant getValidityEndExcl() {
        return validityEndExcl;
    }

    public RateSourceType getSourceType() {
        return sourceType;
    }

    public CachingStrategy getCachingStrategy() {
        return cachingStrategy;
    }

    public boolean isValidFor(Instant instant) {
        return isValidFor(instant, false);
    }

    private boolean isValidFor(Instant instant, boolean includingValidityEnd) {
        final boolean greaterThanOrEqualToFrom = validityStartIncl.compareTo(instant) <= 0;
        final int endComparison = instant.compareTo(validityEndExcl);
        return greaterThanOrEqualToFrom && (includingValidityEnd ? endComparison <= 0 : endComparison < 0);
    }

    public Rate invert() {
        return invert(IRateProvider.DECIMAL_DIGITS);
    }

    public Rate invert(int scale) {
        final BigDecimal newRateValue;
        if (value.compareTo(BigDecimal.ZERO) == 0) {
            newRateValue = value;
        } else {
            newRateValue = BigDecimal.ONE.divide(value, scale, RoundingMode.HALF_UP);
        }
        return new Rate(newRateValue, quote, base, validityStartIncl, validityEndExcl, sourceType, cachingStrategy);
    }

    public Rate withValidity(Instant validFrom, Instant validTill) {
        if (this.validityStartIncl.equals(validFrom) && this.validityEndExcl.equals(validTill)) {
            return this;
        }
        return new Rate(value, base, quote, validFrom, validTill, sourceType, cachingStrategy);
    }

    public BigDecimal convert(BigDecimal value) {
        return value.multiply(this.value).stripTrailingZeros();
    }

    public Rate multiply(Rate other) {
        Objects.requireNonNull(other);
        final Instant validityIntersectStart = ComparableUtils.max(validityStartIncl, other.validityStartIncl);
        final Instant validityIntersectEnd = ComparableUtils.max(validityEndExcl, other.validityEndExcl);
        if (validityIntersectStart.compareTo(validityIntersectEnd) >= 0) {
            throw new IllegalArgumentException(
                String.format(
                    "Intersect ['%s' - '%s') of validity ranges ['%s' - '%s') and ['%s' - '%s') is empty.",
                    validityIntersectStart,
                    validityIntersectEnd,
                    validityStartIncl,
                    validityEndExcl,
                    other.validityStartIncl,
                    other.validityEndExcl
                )
            );
        }
        return multiplyLax(other, validityStartIncl);
    }

    private Rate multiplyLax(Rate other, Instant instant) {
        if (quote != other.base) {
            throw new IllegalArgumentException(
                String.format("Invalid rate base (expected: '%s', actual '%s').", quote, other.base)
            );
        }
        return new Rate(
            value.multiply(other.value),
            new CurrencyPair(base, other.quote),
            instant,
            ComparableUtils.min(validityEndExcl, other.validityEndExcl),
            sourceType.combine(other.sourceType),
            ComparableUtils.min(cachingStrategy, other.cachingStrategy)
        );
    }

    public void checkCompatible(
        Currency desiredBase,
        Currency desiredQuote,
        Instant timestamp,
        boolean includingValidityEnd
    ) {
        if (!(base.equals(desiredBase) && quote.equals(desiredQuote))) {
            throw new IllegalArgumentException(
                String.format(
                    "Incompatible rate (expected: '%s/%s', actual: '%s/%s').",
                    desiredBase,
                    desiredQuote,
                    base,
                    quote
                )
            );
        }
        if (!isValidFor(timestamp, includingValidityEnd)) {
            throw new IllegalStateException(
                String.format(
                    "Illegal timestamp (expected: ['%s'-'%s'%c, actual: '%s').",
                    this.validityStartIncl,
                    this.validityEndExcl,
                    includingValidityEnd ? ']' : ')',
                    timestamp
                )
            );
        }
    }

    @Override
    public String toString() {
        return "Rate{" +
            "value=" + value +
            ", base=" + base +
            ", quote=" + quote +
            ", validityStartIncl=" + validityStartIncl +
            ", validityEndExcl=" + validityEndExcl +
            ", sourceType=" + sourceType +
            ", cachingStrategy=" + cachingStrategy +
            '}';
    }

}
