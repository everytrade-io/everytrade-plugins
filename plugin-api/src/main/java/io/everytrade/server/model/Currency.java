package io.everytrade.server.model;

import lombok.Getter;
import lombok.experimental.FieldDefaults;

import java.time.Instant;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static lombok.AccessLevel.PRIVATE;

@Getter
@FieldDefaults(level = PRIVATE, makeFinal = true)
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
    DAT(false, Instant.parse("2017-08-11T00:00:00Z"), Instant.parse("2021-11-30T00:00:00Z"), "Datum"),
    FUN(false, Instant.parse("2017-02-01T00:00:00Z"), "FunFair"), // token
    BAT(false, Instant.parse("2017-06-01T00:00:00Z"), "Basic Attention Token"), // token
    SPK(false, Instant.parse("2018-01-22T00:00:00Z"), "SparksPay"), // can't find exact date
    TNB(false, Instant.parse("2016-10-01T00:00:00Z"), "Time New Bank"),
    OGN(false, Instant.parse("2018-10-01T00:00:00Z"), "Origin Protocol"),
    SXP(false, Instant.parse("2019-08-22T00:00:00Z"), "Swipe"),
    REN(false, Instant.parse("2018-03-08T00:00:00Z"), "Ren"),
    ANKR(false, Instant.parse("2019-03-07T00:00:00Z"), "Ankr"),
    GRT(false, Instant.parse("2021-02-13T00:00:00Z"), "The Graph"),
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
    CAKE(false, Instant.parse("2021-02-06T00:00:00Z"), "PancakeSwap"),
    BAL(false, Instant.parse("2020-06-24T00:00:00Z"), "Balancer"),
    BEAM(false, Instant.parse("2019-01-18T00:00:00Z"), "Beam"),
    _1INCH("1INCH",false, Instant.parse("2021-01-14T00:00:00Z"), "1inch"),
    VTC(false, Instant.parse("2014-01-08T00:00:00Z"), "Vertcoin"),
    ERG(false, Instant.parse("2017-07-02T00:00:00Z"), "Ergo"),
    LUNA(false, Instant.parse("2019-08-01T00:00:00Z"), "Terra"),
    CLO(false, Instant.parse("2018-03-05T00:00:00Z"), "Callisto Network"),
    ICP(false, Instant.parse("2021-05-12T00:00:00Z"), "Internet Computer"),
    RLC(false, Instant.parse("2017-04-20T00:00:00Z"), "iExec RLC"),
    FLOW(false, Instant.parse("2021-04-15T00:00:00Z"), "Flow"),
    FTT(false, Instant.parse("2019-08-01T00:00:00Z"), "FTX Token"),
    PERP(false, Instant.parse("2021-11-12T00:00:00Z"), "Perpetual Protocol"),
    SRM(false, Instant.parse("2020-08-20T00:00:00Z"), "Serum"),
    XEC(false, Instant.parse("2021-08-26T00:00:00Z"), "eCash"),
    AGLD(false, Instant.parse("2021-09-03T00:00:00Z"), "Adventure Gold"),
    AIOZ(false, Instant.parse("2021-09-10T00:00:00Z"), "AIOZ Network"),
    ALICE(false, Instant.parse("2021-05-27T00:00:00Z"), "My Neighbor Alice"),
    ALPHA(false, Instant.parse("2020-10-27T00:00:00Z"), "Alpha Finance"),
    AMP(false, Instant.parse("2015-03-19T00:00:00Z"), "Amp"),
    ANCT(false, Instant.parse("2021-12-26T00:00:00Z"), "Anchor"),
    AOA(false, Instant.parse("2018-05-16T00:00:00Z"), "Aurora"),
    ARDR(false, Instant.parse("2016-10-13T00:00:00Z"), "Ardor"),
    ARK(false, Instant.parse("2016-12-09T00:00:00Z"), "Ark"),
    ARPA(false, Instant.parse("2019-08-08T00:00:00Z"), "ARPA Chain"),
    ATA(false, Instant.parse("2021-08-24T00:00:00Z"), "Automata"),
    AVAX(false, Instant.parse("2020-07-22T00:00:00Z"), "Avalanche	"),
    AWC(false, Instant.parse("2019-01-03T00:00:00Z"), "Atomic Wallet Coin"),
    AXS(false, Instant.parse("2020-12-10T00:00:00Z"), "Axie Infinity"),
    BAKE(false, Instant.parse("2021-05-27T00:00:00Z"), "BakerySwap"),
    BETA(false, Instant.parse("2021-10-11T00:00:00Z"), "PolyBeta Finance"),
    BETH(false, Instant.parse("2021-12-06T00:00:00Z"), "Anchor bETH Token"),
    BNT(false, Instant.parse("2017-06-17T00:00:00Z"), "Bancor"),
    BNX(false, Instant.parse("2021-09-09T00:00:00Z"), "BinaryX"),
    BOND(false, Instant.parse("2020-06-29T00:00:00Z"), "BarnBridge"),
    BTG(false, Instant.parse("2017-10-23T00:00:00Z"), "Bitcoin Gold"),
    BTR(false, Instant.parse("2019-08-01T00:00:00Z"), "Bitrue Coin"),
    BUNNY(false, Instant.parse("2021-05-27T00:00:00Z"), "Pancake Bunny"),
    C98(false, Instant.parse("2021-06-23T00:00:00Z"), "Coin98"),
    CLV(false, Instant.parse("2021-08-24T00:00:00Z"), "Clover Finance"),
    COTI(false, Instant.parse("2019-05-30T00:00:00Z"), "Coti"),
    CSPR(false, Instant.parse("2021-08-26T00:00:00Z"), "Casper Network"),
    CTK(false, Instant.parse("2020-10-27T00:00:00Z"), "Certik"),
    DAR(false, Instant.parse("2021-11-12T00:00:00Z"), "Mines of Dalarnia"),
    DCR(false, Instant.parse("2015-12-15T00:00:00Z"), "Decred"),
    DEGO(false, Instant.parse("2018-12-18T00:00:00Z"), "DeroGold"),
    DERC(false, Instant.parse("2021-09-04T00:00:00Z"), "DeRace"),
    DOCK(false, Instant.parse("2018-02-21T00:00:00Z"), "Dock"),
    DODO(false, Instant.parse("2021-05-12T00:00:00Z"), "Dodo"),
    DYDX(false, Instant.parse("2021-07-12T00:00:00Z"), "Dydx"),
    EFI(false, Instant.parse("2021-09-03T00:00:00Z"), "Efinity"),
    ELA(false, Instant.parse("2017-12-27T00:00:00Z"), "Elastos"),
    ELON(false, Instant.parse("2021-07-29T00:00:00Z"), "Dogelon Mars"),
    ELONGATE(false, Instant.parse("2021-10-20T00:00:00Z"), "ElonGate"),
    ENG(false, Instant.parse("2017-07-01T00:00:00Z"), "Enigma"),
    ENJ(false, Instant.parse("2017-07-24T00:00:00Z"), "Enjin Coin"),
    ENS(false, Instant.parse("2021-12-03T00:00:00Z"), "Ethereum Name Service"),
    EURS(false, Instant.parse("2018-07-26T00:00:00Z"), "Stasis euro"),
    EWT(false, Instant.parse("2019-06-19T00:00:00Z"), "Energy Web Token"),
    FIO(false, Instant.parse("2020-03-25T00:00:00Z"), "FIO Protocol"),
    FORTH(false, Instant.parse("2021-05-12T00:00:00Z"), "Ampleforth Governance Token"),
    FOX(false, Instant.parse("2021-07-23T00:00:00Z"), "Fox Finance"),
    FTM(false, Instant.parse("2018-10-25T00:00:00Z"), "Fantom"),
    FXS(false, Instant.parse("2021-05-27T00:00:00Z"), "Frax Share"),
    GALA(false, Instant.parse("2021-08-26T00:00:00Z"), "Gala"),
    GAS(false, Instant.parse("2015-11-01T00:00:00Z"), "Gas"),
    GNT(false, Instant.parse("2021-09-09T00:00:00Z"), "GreenTrust"),
    GRIN(false, Instant.parse("2019-01-17T00:00:00Z"), "Grin"),
    GTO(false, Instant.parse("2017-12-14T00:00:00Z"), "Gifto"),
    HBAR(false, Instant.parse("2019-09-12T00:00:00Z"), "Hedera Hashgraph"),
    HIVE(false, Instant.parse("2020-03-20T00:00:00Z"), "Hive"),
    HNT(false, Instant.parse("2019-07-29T00:00:00Z"), "Helium"),
    IGNIS(false, Instant.parse("2017-12-28T00:00:00Z"), "Ignis"),
    INJ(false, Instant.parse("2021-05-11T00:00:00Z"), "Injective Protocol"),
    IOTX(false, Instant.parse("2018-05-24T00:00:00Z"), "IoTeX"),
    KAR(false, Instant.parse("2021-09-03T00:00:00Z"), "Karura"),
    KAU(false, Instant.parse("2021-12-25T00:00:00Z"), "Kauri"),
    KEEP(false, Instant.parse("2021-05-08T00:00:00Z"), "Keep network"),
    KLAY(false, Instant.parse("2019-09-23T00:00:00Z"), "Klaytn"),
    LCX(false, Instant.parse("2019-12-03T00:00:00Z"), "Lcx"),
    LINA(false, Instant.parse("2018-08-02T00:00:00Z"), "Linear"),
    LIT(false, Instant.parse("2021-05-19T00:00:00Z"), "Litentry"),
    LOCG(false, Instant.parse("2021-09-07T00:00:00Z"), "LOCGame"),
    LOOM(false, Instant.parse("2018-03-29T00:00:00Z"), "Loom Network"),
    LTO(false, Instant.parse("2019-01-31T00:00:00Z"), "LTO Network"),
    MAID(false, Instant.parse("2016-08-12T00:00:00Z"), "MaidSafeCoin"),
    MBOX(false, Instant.parse("2021-06-17T00:00:00Z"), "Mobox"),
    MEPAD(false, Instant.parse("2021-05-27T00:00:00Z"), "MemePad"),
    MFT(false, Instant.parse("2018-07-12T00:00:00Z"), "Hifi Finance"),
    MIR(false, Instant.parse("2020-12-08T00:00:00Z"), "Mirror Protocol"),
    MITH(false, Instant.parse("2018-03-22T00:00:00Z"), "Mithril"),
    MOVR(false, Instant.parse("2021-10-22T00:00:00Z"), "Moonriver"),
    MTL(false, Instant.parse("2017-07-13T00:00:00Z"), "Metal"),
    NAV(false, Instant.parse("2014-07-09T00:00:00Z"), "NavCoin"),
    NEAR(false, Instant.parse("2020-12-10T00:00:00Z"), "Near Protocol"),
    NEBL(false, Instant.parse("2017-09-07T00:00:00Z"), "Neblio"),
    NEXO(false, Instant.parse("2018-04-26T00:00:00Z"), "Nexo"),
    NPXS(false, Instant.parse("2017-09-27T00:00:00Z"), "Pundi X"),
    NU(false, Instant.parse("2021-03-16T00:00:00Z"), "NuCypher"),
    OCEAN(false, Instant.parse("2019-05-02T00:00:00Z"), "Ocean Protocol"),
    OMI(false, Instant.parse("2021-03-23T00:00:00Z"), "Ecomi"),
    ORN(false, Instant.parse("2020-08-20T00:00:00Z"), "Orion Protocol"),
    OXT(false, Instant.parse("2020-11-19T00:00:00Z"), "Orchid Protocol"),
    PHA(false, Instant.parse("2021-05-27T00:00:00Z"), "Phala Network"),
    PIVX(false, Instant.parse("2016-01-30T00:00:00Z"), "Pivx"),
    POLY(false, Instant.parse("2017-12-25T00:00:00Z"), "Polymath"),
    QKC(false, Instant.parse("2018-05-31T00:00:00Z"), "QuarkChain"),
    QNT(false, Instant.parse("2018-08-09T00:00:00Z"), "Quant"),
    QUICK(false, Instant.parse("2021-07-02T00:00:00Z"), "Quickswap"),
    RAD(false, Instant.parse("2021-05-20T00:00:00Z"), "Radicle"),
    RDN(false, Instant.parse("2017-10-18T00:00:00Z"), "Raiden Network Token"),
    REEF(false, Instant.parse("2021-01-19T00:00:00Z"), "Reef"),
    REQ(false, Instant.parse("2017-08-31T00:00:00Z"), "Request"),
    REVV(false, Instant.parse("2020-09-03T00:00:00Z"), "Revv"),
    RLY(false, Instant.parse("2020-10-18T00:00:00Z"), "Rally"),
    ROSE(false, Instant.parse("2020-11-18T00:00:00Z"), "Oasis Network"),
    RSR(false, Instant.parse("2019-05-23T00:00:00Z"), "Reserve Rights"),
    RUNE(false, Instant.parse("2019-09-19T00:00:00Z"), "THORChain"),
    SAFEMOON(false, Instant.parse("2021-03-03T00:00:00Z"), "SafeMoon"),
    SALT(false, Instant.parse("2017-09-28T00:00:00Z"), "SALT"),
    SDN(false, Instant.parse("2021-09-03T00:00:00Z"), "Shiden Network"),
    SHIB(false, Instant.parse("2021-05-10T00:00:00Z"), "Shiba Inu	"),
    SIB(false, Instant.parse("2015-05-09T00:00:00Z"), "SIBCoin"),
    SKL(false, Instant.parse("2020-11-26T00:00:00Z"), "SKALE"),
    SLP(false, Instant.parse("2021-07-19T00:00:00Z"), "Smooth Love Potion"),
    SOLO(false, Instant.parse("2020-03-02T00:00:00Z"), "Sologenic"),
    SPARTA(false, Instant.parse("2021-05-27T00:00:00Z"), "Spartan Protocol Token"),
    STEEM(false, Instant.parse("2016-03-24T00:00:00Z"), "Steem"),
    STMX(false, Instant.parse("2021-05-27T00:00:00Z"), "StormX"),
    STRAX(false, Instant.parse("2016-07-14T00:00:00Z"), "Stratis"),
    STRONG(false, Instant.parse("2021-05-28T00:00:00Z"), "Strong"),
    SYS(false, Instant.parse("2014-08-15T00:00:00Z"), "Syscoin"),
    TCT(false, Instant.parse("2017-12-25T00:00:00Z"), "TokenClub"),
    TFUEL(false, Instant.parse("2019-03-28T00:00:00Z"), "Theta Fuel"),
    TLM(false, Instant.parse("2021-08-26T00:00:00Z"), "Alien Worlds"),
    TONCOIN(false, Instant.parse("2021-09-17T00:00:00Z"), "The Open Network"),
    TRB(false, Instant.parse("2019-03-01T00:00:00Z"), "Tellor"),
    TWT(false, Instant.parse("2020-10-01T00:00:00Z"), "Trust Wallet Token"),
    UOS(false, Instant.parse("2019-08-01T00:00:00Z"), "Ultra"),
    VIA(false, Instant.parse("2014-07-18T00:00:00Z"), "Viacoin"),
    VITE(false, Instant.parse("2018-07-12T00:00:00Z"), "Vite"),
    VOXEL(false, Instant.parse("2021-12-15T00:00:00Z"), "Voxies"),
    VRM(false, Instant.parse("2014-05-10T00:00:00Z"), "VeriumReserve"),
    VTHO(false, Instant.parse("2018-07-26T00:00:00Z"), "VeThor Token"),
    WABI(false, Instant.parse("2017-07-21T00:00:00Z"), "Wabi"),
    WAN(false, Instant.parse("2018-03-29T00:00:00Z"), "Wanchain"),
    WIN(false, Instant.parse("2019-08-08T00:00:00Z"), "WINkLink"),
    WING(false, Instant.parse("2021-09-03T00:00:00Z"), "Wing Finance"),
    WPR(false, Instant.parse("2017-08-01T00:00:00Z"), "WePower"),
    XCH(false, Instant.parse("2021-05-08T00:00:00Z"), "Chia"),
    XHV(false, Instant.parse("2018-04-19T00:00:00Z"), "Haven Protocol"),
    XMY(false, Instant.parse("2014-02-23T00:00:00Z"), "Myriad"),
    XVS(false, Instant.parse("2021-10-28T00:00:00Z"), "Venus");
    //    VR(false, Instant.parse(""),"Victoria VR");
    //    REPV2(false, Instant.parse(""),"Augur");

    String code;
    int decimalDigits;
    boolean fiat;
    Instant introduction;
    Instant endDate;
    String description;

    Currency(boolean fiat, Instant introduction, String description) {
        this(null, fiat ? 2 : 8, fiat, introduction, null, description);
    }

    Currency(boolean fiat, Instant introduction, Instant endDate, String description) {
        this(null, fiat ? 2 : 8, fiat, introduction, endDate, description);
    }

    Currency(String code, boolean fiat, Instant introduction, String description) {
        this(code, fiat ? 2 : 8, fiat, introduction, null, description);
    }

    Currency(String code, int decimalDigits, boolean fiat, Instant introduction, Instant endDate, String description) {
        this.decimalDigits = decimalDigits;
        this.fiat = fiat;
        this.introduction = introduction;
        this.endDate = endDate;
        this.description = description;
        this.code = code == null ? name() : code;
    }

    public String code() {
        return code;
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
