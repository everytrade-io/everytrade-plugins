package io.everytrade.server.model;

import java.time.Instant;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public enum Currency {
    USD(true, Instant.parse("1792-04-02T00:00:00Z")),
    CAD(true, Instant.parse("1871-04-01T00:00:00Z")),
    EUR(true, Instant.parse("1999-01-01T00:00:00Z")),
    CZK(true, Instant.parse("1993-01-01T00:00:00Z")),
    GBP(true, Instant.parse("2008-01-01T00:00:00Z")),
    AUD(true, Instant.parse("2008-01-01T00:00:00Z")),

    USDT(false, Instant.parse("2015-07-01T00:00:00Z")),
    BTC(false, Instant.parse("2009-01-03T00:00:00Z")),
    ETH(false, Instant.parse("2015-07-30T00:00:00Z")),
    BNB(false, Instant.parse("2017-09-01T00:00:00Z")),
    LTC(false, Instant.parse("2011-10-07T00:00:00Z")),
    BCH(false, Instant.parse("2017-08-01T00:00:00Z")),
    XMR(false, Instant.parse("2014-04-18T00:00:00Z")),
    XRP(false, Instant.parse("2012-01-01T00:00:00Z")), // can't find exact date
    DAI(false, Instant.parse("2017-12-19T00:00:00Z")),
    DASH(false, Instant.parse("2014-01-18T00:00:00Z")),
    LINK(false, Instant.parse("2017-09-20T00:00:00Z"));

    private final int decimalDigits;
    private final boolean fiat;
    private final Instant introduction;

    Currency(boolean fiat, Instant introduction) {
        this(fiat ? 2 : 6, fiat, introduction);
    }

    Currency(int decimalDigits, boolean fiat, Instant introduction) {
        this.decimalDigits = decimalDigits;
        this.fiat = fiat;
        this.introduction = introduction;
    }

    public int getDecimalDigits() {
        return decimalDigits;
    }

    public boolean isFiat() {
        return fiat;
    }

    public Instant getIntroduction() {
        return introduction;
    }

    public static List<Currency> getFiats() {
        return Arrays
            .stream(values())
            .filter(Currency::isFiat)
            .collect(Collectors.toList());
    }

    public static Set<Currency> getFiatsExcept(Currency exception) {
        return Arrays
            .stream(values())
            .filter(Currency::isFiat)
            .filter(it -> !exception.equals(it))
            .collect(Collectors.toCollection(() -> EnumSet.noneOf(Currency.class)));
    }
}
