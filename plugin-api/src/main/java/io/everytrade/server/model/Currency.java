package io.everytrade.server.model;

import java.time.Instant;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
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
    RON(true, Instant.parse("2005-07-01T00:00:00Z"), "Romanian New Leu"),
    DOP(true, Instant.parse("1844-01-01T00:00:00Z"), "Dominican peso"),

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
    ETC(false, Instant.parse("2016-07-20T00:00:00Z"), "Ethereum Classic"),
    UNI(false,Instant.parse("2020-09-01T00:00:00Z"), "Uniswap"), // token; can't find exact date
    DOGE(false, Instant.parse("2013-12-06T00:00:00Z"), "Dogecoin"),
    STORJ(false, Instant.parse("2015-11-01T00:00:00Z"), "Storj"), // token; can't find exact date
    ZEC(false, Instant.parse("2016-10-28T00:00:00Z"), "Zcash"),
    KAVA(false, Instant.parse("2019-10-01T00:00:00Z"), "Kava"), // can't find exact date
    YFI(false, Instant.parse("2020-07-01T00:00:00Z"), "yearn.finance"), // token; can't find exact date
    AAVE(false, Instant.parse("2020-10-13T00:00:00Z"), "Aave"), // token
    LSK(false, Instant.parse("2016-02-01T00:00:00Z"), "Lisk"),
    OMG(false, Instant.parse("2017-06-23T00:00:00Z"), "OMG Network"), // token
    BAND(false, Instant.parse("2019-09-01T00:00:00Z"), "Band Protocol"), // token; can't find exact date
    MATIC(false, Instant.parse("2019-04-01T00:00:00Z"), "Matic Network"), // token; can't find exact date
    DNT(false, Instant.parse("2017-08-01T00:00:00Z"), "district0x"), // token; can't find exact date
    SC(false, Instant.parse("2015-03-17T00:00:00Z"), "Siacoin"),
    KMD(false, Instant.parse("2016-09-14T00:00:00Z"), "Komodo"),
    DGB(false, Instant.parse("2014-01-10T00:00:00Z"), "DigiByte"),
    NEO(false, Instant.parse("2016-09-09T00:00:00Z"), "NEO"),
    DAT(false, Instant.parse("2017-08-11T00:00:00Z"), "Datum"),
    FUN(false, Instant.parse("2017-02-01T00:00:00Z"), "FunFair"), // token
    BAT(false, Instant.parse("2017-06-01T00:00:00Z"), "Basic Attention Token"), // token
    SPK(false, Instant.parse("2018-01-22T00:00:00Z"), "SparksPay"), // can't find exact date
    TNB(false, Instant.parse("2016-10-01T00:00:00Z"), "Time New Bank"),
    OGN(false, Instant.parse("2018-10-01T00:00:00Z"), "Origin Protocol"),
    SXP(false, Instant.parse("2019-08-22T00:00:00Z"), "Swipe"),
    REN(false, Instant.parse("2018-03-08T00:00:00Z"), "Ren"),
    ANKR(false, Instant.parse("2019-03-07T00:00:00Z"), "Ankr"),
    GRT(false, Instant.parse("2020-10-28T00:00:00Z"), "The Graph"),
    SNX(false, Instant.parse("2018-02-28T00:00:00Z"), "Synthetix Network Token"),
    TROY(false, Instant.parse("2019-04-29T00:00:00Z"), "TROYA COIN"),
    DIA(false, Instant.parse("2020-09-23T00:00:00Z"), "DIA"),
    SUSHI(false, Instant.parse("2020-09-07T00:00:00Z"), "Sushi"),
    ZRX(false, Instant.parse("2017-08-15T00:00:00Z"), "0x"),
    UMA(false, Instant.parse("2020-08-31T00:00:00Z"), "UMA"),
    LRC(false, Instant.parse("2016-08-06T00:00:00Z"), "Loopring"),
    KNC(false, Instant.parse("2017-08-01T00:00:00Z"), "Kyber Network"),
    QTUM(false, Instant.parse("2016-12-19T00:00:00Z"), "Qtum"),
    CEL(false, Instant.parse("2018-05-03T00:00:00Z"), "Celsius"),
    MANA(false, Instant.parse("2015-06-01T00:00:00Z"), "Decentraland"),
    ZIL(false, Instant.parse("2017-11-06T00:00:00Z"), "Zilliqa"),
    KSM(false, Instant.parse("2019-12-12T00:00:00Z"), "Kusama"),
    VET(false, Instant.parse("2017-08-22T00:00:00Z"), "VeChain"),
    CRO(false, Instant.parse("2018-12-14T00:00:00Z"), "Crypto.com Coin"),
    FET(false, Instant.parse("2019-02-28T00:00:00Z"), "Fetch.AI"),
    BLZ(false, Instant.parse("2017-10-01T00:00:00Z"), "Bluzelle"),
    CELR(false, Instant.parse("2019-03-25T00:00:00Z"), "Celer Network"),
    ONE(false, Instant.parse("2019-06-01T00:00:00Z"), "Harmony"),
    IRIS(false, Instant.parse("2019-04-19T00:00:00Z"), "IRISnet"),
    CHZ(false, Instant.parse("2018-01-01T00:00:00Z"), "Chiliz"),
    SAND(false, Instant.parse("2020-12-15T00:00:00Z"), "The Sandbox"),
    CKB(false, Instant.parse("2019-07-03T00:00:00Z"), "Nervos Network"),
    SOL(false, Instant.parse("2020-04-10T00:00:00Z"), "Solana"),
    COS(false, Instant.parse("2018-08-02T00:00:00Z"), "Contentos"),
    HOT(false, Instant.parse("2018-04-30T00:00:00Z"), "Holo"),
    TOMO(false, Instant.parse("2018-03-26T00:00:00Z"), "TomoChain"),
    BSV(false, Instant.parse("2018-11-09T00:00:00Z"), "Bitcoin SV"),
    AVA(false, Instant.parse("2018-05-28T00:00:00Z"), "Travala.com"),
    ATOM(false, Instant.parse("2019-03-15T00:00:00Z"), "Cosmos"),
    EGLD(false, Instant.parse("2020-09-22T00:00:00Z"), "Elrond"),
    ALGO(false, Instant.parse("2019-06-19T00:00:00Z"), "Algorand"),
    ANT(false, Instant.parse("2017-05-05T00:00:00Z"), "Aragon"),
    COMP(false, Instant.parse("2020-06-17T00:00:00Z"), "Compound"),
    ICX(false, Instant.parse("2017-10-27T00:00:00Z"), "ICON"),
    CRV(false, Instant.parse("2020-08-25T00:00:00Z"), "Curve DAO Token"),
    REP(false, Instant.parse("2015-10-27T00:00:00Z"), "Augur"),
    FIRO(false, Instant.parse("2016-09-28T00:00:00Z"), "Firo"),
    ZEN(false, Instant.parse("2017-05-30T00:00:00Z"), "Horizen"),
    XVG(false, Instant.parse("2014-10-09T00:00:00Z"), "Verge"),
    BUSD(false, Instant.parse("2019-09-20T00:00:00Z"), "Binance USD"),
    THETA(false, Instant.parse("2017-11-23T00:00:00Z"), "THETA"),
    ONT(false, Instant.parse("2018-03-08T00:00:00Z"), "Ontology"),
    AKRO(false, Instant.parse("2019-08-07T00:00:00Z"), "Akropolis"),
    BTT(false, Instant.parse("2019-01-28T00:00:00Z"), "BitTorrent"),
    NANO(false, Instant.parse("2015-10-05T00:00:00Z"), "Nano"),
    FIL(false, Instant.parse("2017-12-03T00:00:00Z"), "Filecoin"),
    IOST(false, Instant.parse("2018-01-03T00:00:00Z"), "IOST"),
    XEM(false, Instant.parse("2014-06-06T00:00:00Z"), "NEM"),
    DENT(false, Instant.parse("2017-08-12T00:00:00Z"), "Dent"),
    RVN(false, Instant.parse("2018-01-03T00:00:00Z"), "Ravencoin"),
    WAVES(false, Instant.parse("2016-04-12T00:00:00Z"), "Waves"),
    TEL(false, Instant.parse("2017-11-11T00:00:00Z"), "Telcoin"),
    UTK(false, Instant.parse("2017-08-28T00:00:00Z"), "Utrust"),
    GLM(false, Instant.parse("2016-11-18T00:00:00Z"), "Golem"),
    MKR(false, Instant.parse("2015-08-15T00:00:00Z"), "Maker"),
    NMR(false, Instant.parse("2017-06-21T00:00:00Z"), "Numeraire"),
    PAXG(false, Instant.parse("2019-09-26T00:00:00Z"), "PAX Gold"),
    CAKE(false, Instant.parse("2021-01-30T00:00:00Z"), "PancakeSwap"),
    BAL(false, Instant.parse("2020-06-24T00:00:00Z"), "Balancer"),
    BEAM(false, Instant.parse("2019-01-18T00:00:00Z"), "Beam"),
    _1INCH("1INCH",false, Instant.parse("2020-12-25T00:00:00Z"), "1inch");

    private final String code;
    private final int decimalDigits;
    private final boolean fiat;
    private final Instant introduction;
    private final String description;

    Currency(boolean fiat, Instant introduction, String description) {
        this(null, fiat ? 2 : 8, fiat, introduction, description);
    }

    Currency(String code, boolean fiat, Instant introduction, String description) {
        this(code, fiat ? 2 : 8, fiat, introduction, description);
    }

    Currency(String code, int decimalDigits, boolean fiat, Instant introduction, String description) {
        this.decimalDigits = decimalDigits;
        this.fiat = fiat;
        this.introduction = introduction;
        this.description = description;
        this.code = code == null ? name() : code;
    }

    public String code() {
        return code;
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
        return getFiatsExcept(Set.of(exception));
    }

    public static Set<Currency> getFiatsExcept(Set<Currency> exceptions) {
        return Arrays
            .stream(values())
            .filter(Currency::isFiat)
            .filter(it -> !exceptions.contains(it))
            .collect(Collectors.toCollection(() -> EnumSet.noneOf(Currency.class)));
    }

    public static Currency fromCode(String code) {
        Objects.requireNonNull(code, "code is null");
        for (Currency c : values()) {
            if (code.equals(c.code())) {
                return c;
            }
        }
        throw new IllegalArgumentException("No enum constant " + Currency.class.getCanonicalName() + "." + code);
    }
}
