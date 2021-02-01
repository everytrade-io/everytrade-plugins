package io.everytrade.server.model;

import java.time.Instant;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public enum Currency {
    USD(true, Instant.parse("1792-04-02T00:00:00Z"), "U.S. dollar"),
    CAD(true, Instant.parse("1871-04-01T00:00:00Z"), "Canadian dollar"),
    EUR(true, Instant.parse("1999-01-01T00:00:00Z"), "Euro"),
    CZK(true, Instant.parse("1993-01-01T00:00:00Z"), "Czech koruna"),
    //free-floating GBP// (Wikipedia: August 1971)
    GBP(true, Instant.parse("1971-08-01T00:00:00Z"), "British pound"),
    AUD(true, Instant.parse("1966-02-14T00:00:00Z"), "Australian dollar"),
    HKD(true, Instant.parse("1937-01-01T00:00:00Z"), "Hong Kong dollar"),
    RON(true, Instant.parse("2005-01-01T00:00:00Z"), "Romanian New Leu"),

    USDT(false, Instant.parse("2015-07-01T00:00:00Z"), "Tether"),
    BTC(false, Instant.parse("2009-01-03T00:00:00Z"), "Bitcoin"),
    ETH(false, Instant.parse("2015-07-30T00:00:00Z"), "Ethereum"),
    BNB(false, Instant.parse("2017-09-01T00:00:00Z"), "Binance coin"),
    LTC(false, Instant.parse("2011-10-07T00:00:00Z"), "Litecoin"),
    BCH(false, Instant.parse("2017-08-01T00:00:00Z"), "Bitcoin Cash"),
    XMR(false, Instant.parse("2014-04-18T00:00:00Z"), "Monero"),
    XRP(false, Instant.parse("2012-01-01T00:00:00Z"), "Ripple"), // can't find exact date
    DAI(false, Instant.parse("2017-12-19T00:00:00Z"), "Dai"),
    DASH(false, Instant.parse("2014-01-18T00:00:00Z"), "Dash"),
    LINK(false, Instant.parse("2017-09-21T00:00:00Z"), "Chainlink"),
    IOTA(false, Instant.parse("2017-07-01T00:00:00Z"), "IOTA"),
    TRX(false, Instant.parse("2017-08-30T00:00:00Z"), "TRON"),
    USDC(false, Instant.parse("2018-10-10T00:00:00Z"), "USD Coin"),
    XTZ(false, Instant.parse("2017-07-01T00:00:00Z"), "Tezos"),
    XLM(false, Instant.parse("2013-07-19T00:00:00Z"), "Stellar"),
    ADA(false, Instant.parse("2017-09-29T00:00:00Z"), "Cardano"),
    EOS(false, Instant.parse("2017-05-06T00:00:00Z"), "EOS"),
    DOT(false, Instant.parse("2020-08-22T00:00:00Z"), "Polkadot"),
    ETC(false, Instant.parse("2016-07-20T00:00:00Z"), "Ethereum Classic");

    private final int decimalDigits;
    private final boolean fiat;
    private final Instant introduction;
    private final String description;

    Currency(boolean fiat, Instant introduction, String description) {
        this(fiat ? 2 : 6, fiat, introduction, description);
    }

    Currency(int decimalDigits, boolean fiat, Instant introduction, String description) {
        this.decimalDigits = decimalDigits;
        this.fiat = fiat;
        this.introduction = introduction;
        this.description = description;
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

    public String getDescription() {
        return description;
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
