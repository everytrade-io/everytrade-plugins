package io.everytrade.server.plugin.impl.everytrade.rateprovider;

import com.generalbytes.batm.server.extensions.extra.bitcoin.sources.coinpaprika.CoinPaprikaHistoricalTickerResponse;
import com.generalbytes.batm.server.extensions.extra.bitcoin.sources.coinpaprika.CoinPaprikaV1API;
import io.everytrade.server.model.Currency;
import io.everytrade.server.model.CurrencyPair;
import io.everytrade.server.plugin.api.IPlugin;
import io.everytrade.server.plugin.api.rateprovider.RateProviderDescriptor;
import io.everytrade.server.plugin.api.rateprovider.CachingStrategy;
import io.everytrade.server.plugin.api.rateprovider.IRateProvider;
import io.everytrade.server.plugin.api.rateprovider.Rate;
import io.everytrade.server.plugin.api.rateprovider.RateSourceType;
import io.everytrade.server.plugin.api.rateprovider.RateValidity;
import io.everytrade.server.plugin.impl.everytrade.EveryTradePlugin;
import io.everytrade.server.plugin.utils.TimeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import si.mazi.rescu.ClientConfig;
import si.mazi.rescu.HttpStatusIOException;
import si.mazi.rescu.RestProxyFactory;

import javax.ws.rs.HeaderParam;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public final class CoinPaprikaRateProvider implements IRateProvider {
    private final CoinPaprikaV1API api;

    private static final Map<Currency, String> COIN_IDS_BY_CURRENCY = new EnumMap<>(Currency.class);
    private static final Map<Currency, Instant> LISTING_START_BY_CURRENCY = new EnumMap<>(Currency.class);
    private static final Set<Currency> SUPPORTED_QUOTES = new HashSet<>();
    private static final Duration CALL_DELAY = Duration.of(250, ChronoUnit.MILLIS);
    private static final Logger LOG = LoggerFactory.getLogger(CoinPaprikaRateProvider.class);
    private static Instant LAST_CALL = Instant.now();

    public static final RateValidity MIN_RATE_VALIDITY = RateValidity.QUARTER_HOUR;
    public static final String ID = EveryTradePlugin.ID + IPlugin.PLUGIN_PATH_SEPARATOR + "coinPaprika";

    static {
        // coin ids can be viewed at https://api.coinpaprika.com/v1/coins
        COIN_IDS_BY_CURRENCY.put(Currency.BTC, "btc-bitcoin");
        COIN_IDS_BY_CURRENCY.put(Currency.ETH, "eth-ethereum");
        COIN_IDS_BY_CURRENCY.put(Currency.LTC, "ltc-litecoin");
        COIN_IDS_BY_CURRENCY.put(Currency.BCH, "bch-bitcoin-cash");
        COIN_IDS_BY_CURRENCY.put(Currency.XRP, "xrp-xrp");
        COIN_IDS_BY_CURRENCY.put(Currency.XMR, "xmr-monero");
        COIN_IDS_BY_CURRENCY.put(Currency.DAI, "dai-dai");
        COIN_IDS_BY_CURRENCY.put(Currency.DASH, "dash-dash");
        COIN_IDS_BY_CURRENCY.put(Currency.USDT, "usdt-tether");
        COIN_IDS_BY_CURRENCY.put(Currency.BNB, "bnb-binance-coin");
        COIN_IDS_BY_CURRENCY.put(Currency.LINK, "link-chainlink");
        COIN_IDS_BY_CURRENCY.put(Currency.IOTA, "miota-iota");
        COIN_IDS_BY_CURRENCY.put(Currency.TRX, "trx-tron");
        COIN_IDS_BY_CURRENCY.put(Currency.USDC, "usdc-usd-coin");
        COIN_IDS_BY_CURRENCY.put(Currency.XTZ, "xtz-tezos");
        COIN_IDS_BY_CURRENCY.put(Currency.XLM, "xlm-stellar");
        COIN_IDS_BY_CURRENCY.put(Currency.ADA, "ada-cardano");
        COIN_IDS_BY_CURRENCY.put(Currency.EOS, "eos-eos");
        COIN_IDS_BY_CURRENCY.put(Currency.DOT, "dot-polkadot");
        COIN_IDS_BY_CURRENCY.put(Currency.ETC, "etc-ethereum-classic");
        COIN_IDS_BY_CURRENCY.put(Currency.UNI, "uni-uniswap");
        COIN_IDS_BY_CURRENCY.put(Currency.DOGE, "doge-dogecoin");
        COIN_IDS_BY_CURRENCY.put(Currency.STORJ, "storj-storj");
        COIN_IDS_BY_CURRENCY.put(Currency.ZEC, "zec-zcash");
        COIN_IDS_BY_CURRENCY.put(Currency.KAVA, "kava-kava");
        COIN_IDS_BY_CURRENCY.put(Currency.YFI, "yfi-yearnfinance");
        COIN_IDS_BY_CURRENCY.put(Currency.AAVE, "aave-new");
        COIN_IDS_BY_CURRENCY.put(Currency.LSK, "lsk-lisk");
        COIN_IDS_BY_CURRENCY.put(Currency.OMG, "omg-omg-network");
        COIN_IDS_BY_CURRENCY.put(Currency.BAND, "band-band-protocol");
        COIN_IDS_BY_CURRENCY.put(Currency.MATIC, "matic-matic-network");
        COIN_IDS_BY_CURRENCY.put(Currency.DNT, "dnt-district0x");
        COIN_IDS_BY_CURRENCY.put(Currency.SC, "sc-siacoin");
        COIN_IDS_BY_CURRENCY.put(Currency.KMD, "kmd-komodo");
        COIN_IDS_BY_CURRENCY.put(Currency.DGB, "dgb-digibyte");
        COIN_IDS_BY_CURRENCY.put(Currency.NEO, "neo-neo");
        COIN_IDS_BY_CURRENCY.put(Currency.DAT, "dat-datum");
        COIN_IDS_BY_CURRENCY.put(Currency.FUN, "fun-funfair");
        COIN_IDS_BY_CURRENCY.put(Currency.BAT, "bat-basic-attention-token");
        COIN_IDS_BY_CURRENCY.put(Currency.SPK, "spk-sparks");
        COIN_IDS_BY_CURRENCY.put(Currency.TNB, "tnb-time-new-bank");
        COIN_IDS_BY_CURRENCY.put(Currency.OGN, "ogn-origin-protocol");
        COIN_IDS_BY_CURRENCY.put(Currency.SXP, "sxp-swipe");
        COIN_IDS_BY_CURRENCY.put(Currency.REN, "ren-republic-protocol");
        COIN_IDS_BY_CURRENCY.put(Currency.ANKR, "ankr-ankr-network");
        COIN_IDS_BY_CURRENCY.put(Currency.GRT, "grt-golden-ratio-token");
        COIN_IDS_BY_CURRENCY.put(Currency.SNX, "snx-synthetix-network-token");
        COIN_IDS_BY_CURRENCY.put(Currency.TROY, "troy-troya-coin");
        COIN_IDS_BY_CURRENCY.put(Currency.DIA, "dia-dia");
        COIN_IDS_BY_CURRENCY.put(Currency.SUSHI, "sushi-sushi");
        COIN_IDS_BY_CURRENCY.put(Currency.ZRX, "zrx-0x");
        COIN_IDS_BY_CURRENCY.put(Currency.UMA, "uma-uma");
        COIN_IDS_BY_CURRENCY.put(Currency.LRC, "lrc-loopring");
        COIN_IDS_BY_CURRENCY.put(Currency.KNC, "knc-kyber-network");
        COIN_IDS_BY_CURRENCY.put(Currency.QTUM, "qtum-qtum");
        COIN_IDS_BY_CURRENCY.put(Currency.CEL, "cel-celsius");
        COIN_IDS_BY_CURRENCY.put(Currency.MANA, "mana-decentraland");
        COIN_IDS_BY_CURRENCY.put(Currency.ZIL, "zil-zilliqa");
        COIN_IDS_BY_CURRENCY.put(Currency.KSM, "ksm-kusama");
        COIN_IDS_BY_CURRENCY.put(Currency.VET, "vet-vechain");
        COIN_IDS_BY_CURRENCY.put(Currency.CRO, "cro-cryptocom-chain");
        COIN_IDS_BY_CURRENCY.put(Currency.FET, "fetch-ai");
        COIN_IDS_BY_CURRENCY.put(Currency.BLZ, "blz-bluzelle");
        COIN_IDS_BY_CURRENCY.put(Currency.CELR, "celr-celer-network");
        COIN_IDS_BY_CURRENCY.put(Currency.ONE, "one-harmony");
        COIN_IDS_BY_CURRENCY.put(Currency.IRIS, "iris-irisnet");
        COIN_IDS_BY_CURRENCY.put(Currency.CHZ, "chz-chiliz");
        COIN_IDS_BY_CURRENCY.put(Currency.SAND, "sand-the-sandbox");
        COIN_IDS_BY_CURRENCY.put(Currency.CKB, "ckb-nervos-network");
        COIN_IDS_BY_CURRENCY.put(Currency.SOL, "sol-solana");
        COIN_IDS_BY_CURRENCY.put(Currency.COS, "cos-contentos");
        COIN_IDS_BY_CURRENCY.put(Currency.HOT, "hot-holo");
        COIN_IDS_BY_CURRENCY.put(Currency.TOMO, "tomo-tomochain");
        COIN_IDS_BY_CURRENCY.put(Currency.BSV, "bsv-bitcoin-sv");
        COIN_IDS_BY_CURRENCY.put(Currency.AVA, "ava-travala");
        COIN_IDS_BY_CURRENCY.put(Currency.ATOM, "atom-cosmos");
        COIN_IDS_BY_CURRENCY.put(Currency.EGLD, "egld-elrond");
        COIN_IDS_BY_CURRENCY.put(Currency.ALGO, "algo-algorand");
        COIN_IDS_BY_CURRENCY.put(Currency.ANT, "ant-aragon");
        COIN_IDS_BY_CURRENCY.put(Currency.COMP, "comp-compoundd");
        COIN_IDS_BY_CURRENCY.put(Currency.ICX, "icx-icon");
        COIN_IDS_BY_CURRENCY.put(Currency.CRV, "crv-curve-dao-token");
        COIN_IDS_BY_CURRENCY.put(Currency.REP, "rep-augur");
        COIN_IDS_BY_CURRENCY.put(Currency.FIRO, "firo-firo");
        COIN_IDS_BY_CURRENCY.put(Currency.ZEN, "zen-horizen");
        COIN_IDS_BY_CURRENCY.put(Currency.XVG, "xvg-verge");
        COIN_IDS_BY_CURRENCY.put(Currency.BUSD, "busd-binance-usd");
        COIN_IDS_BY_CURRENCY.put(Currency.THETA, "theta-theta-token");
        COIN_IDS_BY_CURRENCY.put(Currency.ONT, "ont-ontology");
        COIN_IDS_BY_CURRENCY.put(Currency.AKRO, "akro-akropolis");
        COIN_IDS_BY_CURRENCY.put(Currency.BTT, "btt-bittorrent");
        COIN_IDS_BY_CURRENCY.put(Currency.NANO, "nano-nano");
        COIN_IDS_BY_CURRENCY.put(Currency.FIL, "fil-filecoin");
        COIN_IDS_BY_CURRENCY.put(Currency.IOST, "iost-iost");
        COIN_IDS_BY_CURRENCY.put(Currency.XEM, "xem-nem");
        COIN_IDS_BY_CURRENCY.put(Currency.DENT, "dent-dent");
        COIN_IDS_BY_CURRENCY.put(Currency.RVN, "rvn-ravencoin");
        COIN_IDS_BY_CURRENCY.put(Currency.WAVES, "waves-waves");
        COIN_IDS_BY_CURRENCY.put(Currency.TEL, "tel-telcoin");
        COIN_IDS_BY_CURRENCY.put(Currency.UTK, "utk-utrust");
        COIN_IDS_BY_CURRENCY.put(Currency.GLM, "glm-golem");
        COIN_IDS_BY_CURRENCY.put(Currency.MKR, "mkr-maker");
        COIN_IDS_BY_CURRENCY.put(Currency.NMR, "nmr-numeraire");
        COIN_IDS_BY_CURRENCY.put(Currency.PAXG, "paxg-pax-gold");
        COIN_IDS_BY_CURRENCY.put(Currency.CAKE, "cake-pancakeswap");
        COIN_IDS_BY_CURRENCY.put(Currency.BAL, "bal-balancer");
        COIN_IDS_BY_CURRENCY.put(Currency.BEAM, "beam-beam");

        LISTING_START_BY_CURRENCY.put(Currency.BTC, Instant.parse("2013-04-28T18:45:00Z"));
        LISTING_START_BY_CURRENCY.put(Currency.ETH, Instant.parse("2015-08-07T14:45:00Z"));
        LISTING_START_BY_CURRENCY.put(Currency.LTC, Instant.parse("2013-04-28T18:45:00Z"));
        LISTING_START_BY_CURRENCY.put(Currency.BCH, Instant.parse("2017-08-01T05:45:00Z"));
        LISTING_START_BY_CURRENCY.put(Currency.XRP, Instant.parse("2013-08-04T18:50:00Z"));
        LISTING_START_BY_CURRENCY.put(Currency.XMR, Instant.parse("2014-05-21T09:30:00Z"));
        LISTING_START_BY_CURRENCY.put(Currency.DAI, Instant.parse("2017-12-27T01:30:00Z"));
        LISTING_START_BY_CURRENCY.put(Currency.DASH, Instant.parse("2014-02-14T13:50:00Z"));
        LISTING_START_BY_CURRENCY.put(Currency.USDT, Instant.parse("2015-03-06T13:05:00Z"));
        LISTING_START_BY_CURRENCY.put(Currency.BNB, Instant.parse("2017-07-25T04:30:00Z"));
        LISTING_START_BY_CURRENCY.put(Currency.LINK, Instant.parse("2017-09-21T04:30:00Z"));
        LISTING_START_BY_CURRENCY.put(Currency.IOTA, Instant.parse("2017-06-20T00:00:00Z"));
        LISTING_START_BY_CURRENCY.put(Currency.TRX, Instant.parse("2017-09-14T00:00:00Z"));
        LISTING_START_BY_CURRENCY.put(Currency.USDC, Instant.parse("2018-10-10T00:00:00Z"));
        LISTING_START_BY_CURRENCY.put(Currency.XTZ, Instant.parse("2017-10-03T00:00:00Z"));
        LISTING_START_BY_CURRENCY.put(Currency.XLM, Instant.parse("2014-08-06T00:00:00Z"));
        LISTING_START_BY_CURRENCY.put(Currency.ADA, Instant.parse("2017-10-01T21:30:00Z"));
        LISTING_START_BY_CURRENCY.put(Currency.EOS, Instant.parse("2017-07-02T00:00:00Z"));
        LISTING_START_BY_CURRENCY.put(Currency.DOT, Instant.parse("2020-08-22T00:00:00Z"));
        LISTING_START_BY_CURRENCY.put(Currency.ETC, Instant.parse("2016-07-25T00:00:00Z"));
        LISTING_START_BY_CURRENCY.put(Currency.UNI, Instant.parse("2020-09-17T11:00:00Z"));
        LISTING_START_BY_CURRENCY.put(Currency.DOGE, Instant.parse("2013-12-15T15:00:00Z"));
        LISTING_START_BY_CURRENCY.put(Currency.STORJ, Instant.parse("2017-07-02T01:00:00Z"));
        LISTING_START_BY_CURRENCY.put(Currency.ZEC, Instant.parse("2016-10-29T15:30:00Z"));
        LISTING_START_BY_CURRENCY.put(Currency.KAVA, Instant.parse("2019-10-31T14:15:00Z"));
        LISTING_START_BY_CURRENCY.put(Currency.YFI, Instant.parse("2020-07-18T00:00:00Z"));
        LISTING_START_BY_CURRENCY.put(Currency.AAVE, Instant.parse("2020-10-13T12:00:00Z"));
        LISTING_START_BY_CURRENCY.put(Currency.LSK, Instant.parse("2016-04-06T20:15:00Z"));
        LISTING_START_BY_CURRENCY.put(Currency.OMG, Instant.parse("2017-07-14T04:30:00Z"));
        LISTING_START_BY_CURRENCY.put(Currency.BAND, Instant.parse("2019-09-18T21:35:00Z"));
        LISTING_START_BY_CURRENCY.put(Currency.MATIC, Instant.parse("2019-04-29T05:30:00Z"));
        LISTING_START_BY_CURRENCY.put(Currency.DNT, Instant.parse("2017-08-03T22:15:00Z"));
        LISTING_START_BY_CURRENCY.put(Currency.SC, Instant.parse("2015-08-26T18:15:00Z"));
        LISTING_START_BY_CURRENCY.put(Currency.KMD, Instant.parse("2017-02-05T23:45:00Z"));
        LISTING_START_BY_CURRENCY.put(Currency.DGB, Instant.parse("2014-02-06T13:15:00Z"));
        LISTING_START_BY_CURRENCY.put(Currency.NEO, Instant.parse("2016-09-09T04:00:00Z"));
        LISTING_START_BY_CURRENCY.put(Currency.DAT, Instant.parse("2017-12-15T20:15:00Z"));
        LISTING_START_BY_CURRENCY.put(Currency.FUN, Instant.parse("2017-06-27T05:00:00Z"));
        LISTING_START_BY_CURRENCY.put(Currency.BAT, Instant.parse("2017-06-01T05:15:00Z"));
        LISTING_START_BY_CURRENCY.put(Currency.SPK, Instant.parse("2018-01-22T23:00:00Z"));
        LISTING_START_BY_CURRENCY.put(Currency.TNB, Instant.parse("2017-11-27T20:30:00Z"));
        LISTING_START_BY_CURRENCY.put(Currency.OGN, Instant.parse("2020-08-06T13:45:00Z"));
        LISTING_START_BY_CURRENCY.put(Currency.SXP, Instant.parse("2019-08-26T23:15:00Z"));
        LISTING_START_BY_CURRENCY.put(Currency.REN, Instant.parse("2018-03-08T09:00:00Z"));
        LISTING_START_BY_CURRENCY.put(Currency.ANKR, Instant.parse("2019-03-07T12:45:00Z"));
        LISTING_START_BY_CURRENCY.put(Currency.GRT, Instant.parse("2020-05-16T05:15:00Z"));
        LISTING_START_BY_CURRENCY.put(Currency.SNX, Instant.parse("2018-03-29T05:00:00Z"));
        LISTING_START_BY_CURRENCY.put(Currency.TROY, Instant.parse("2019-05-06T18:00:00Z"));
        LISTING_START_BY_CURRENCY.put(Currency.DIA, Instant.parse("2020-09-23T11:30:00Z"));
        LISTING_START_BY_CURRENCY.put(Currency.SUSHI, Instant.parse("2020-09-07T12:00:00Z"));
        LISTING_START_BY_CURRENCY.put(Currency.ZRX, Instant.parse("2017-08-16T14:15:00Z"));
        LISTING_START_BY_CURRENCY.put(Currency.UMA, Instant.parse("2020-08-31T18:00:00Z"));
        LISTING_START_BY_CURRENCY.put(Currency.LRC, Instant.parse("2017-08-30T02:30:00Z"));
        LISTING_START_BY_CURRENCY.put(Currency.KNC, Instant.parse("2017-09-24T14:15:00Z"));
        LISTING_START_BY_CURRENCY.put(Currency.QTUM, Instant.parse("2017-05-24T15:15:00Z"));
        LISTING_START_BY_CURRENCY.put(Currency.CEL, Instant.parse("2018-10-02T20:00:00Z"));
        LISTING_START_BY_CURRENCY.put(Currency.MANA, Instant.parse("2017-09-17T00:45:00Z"));
        LISTING_START_BY_CURRENCY.put(Currency.ZIL, Instant.parse("2018-01-25T23:15:00Z"));
        LISTING_START_BY_CURRENCY.put(Currency.KSM, Instant.parse("2020-08-07T12:15:00Z"));
        LISTING_START_BY_CURRENCY.put(Currency.VET, Instant.parse("2017-08-22T03:30:00Z"));
        LISTING_START_BY_CURRENCY.put(Currency.CRO, Instant.parse("2018-12-14T23:15:00Z"));
        LISTING_START_BY_CURRENCY.put(Currency.FET, Instant.parse("2019-02-28T13:00:00Z"));
        LISTING_START_BY_CURRENCY.put(Currency.BLZ, Instant.parse("2018-02-06T18:30:00Z"));
        LISTING_START_BY_CURRENCY.put(Currency.CELR, Instant.parse("2019-03-25T09:30:00Z"));
        LISTING_START_BY_CURRENCY.put(Currency.ONE, Instant.parse("2019-06-01T11:15:00Z"));
        LISTING_START_BY_CURRENCY.put(Currency.IRIS, Instant.parse("2019-04-19T03:00:00Z"));
        LISTING_START_BY_CURRENCY.put(Currency.CHZ, Instant.parse("2019-07-01T15:30:00Z"));
        LISTING_START_BY_CURRENCY.put(Currency.SAND, Instant.parse("2020-12-15T14:45:00Z"));
        LISTING_START_BY_CURRENCY.put(Currency.CKB, Instant.parse("2019-11-25T16:00:00Z"));
        LISTING_START_BY_CURRENCY.put(Currency.SOL, Instant.parse("2020-08-26T12:15:00Z"));
        LISTING_START_BY_CURRENCY.put(Currency.COS, Instant.parse("2019-08-07T11:00:00Z"));
        LISTING_START_BY_CURRENCY.put(Currency.HOT, Instant.parse("2018-04-30T22:10:00Z"));
        LISTING_START_BY_CURRENCY.put(Currency.TOMO, Instant.parse("2018-03-29T05:00:00Z"));
        LISTING_START_BY_CURRENCY.put(Currency.BSV, Instant.parse("2018-11-09T05:15:00Z"));
        LISTING_START_BY_CURRENCY.put(Currency.AVA, Instant.parse("2018-05-28T19:40:00Z"));
        LISTING_START_BY_CURRENCY.put(Currency.ATOM, Instant.parse("2019-03-15T04:45:00Z"));
        LISTING_START_BY_CURRENCY.put(Currency.EGLD, Instant.parse("2020-09-22T11:30:00Z"));
        LISTING_START_BY_CURRENCY.put(Currency.ALGO, Instant.parse("2019-08-07T09:10:00Z"));
        LISTING_START_BY_CURRENCY.put(Currency.ANT, Instant.parse("2017-05-18T21:25:00Z"));
        LISTING_START_BY_CURRENCY.put(Currency.COMP, Instant.parse("2020-07-16T13:10:00Z"));
        LISTING_START_BY_CURRENCY.put(Currency.ICX, Instant.parse("2017-10-27T00:15:00Z"));
        LISTING_START_BY_CURRENCY.put(Currency.CRV, Instant.parse("2020-08-25T10:20:00Z"));
        LISTING_START_BY_CURRENCY.put(Currency.REP, Instant.parse("2015-10-27T17:10:00Z"));
        LISTING_START_BY_CURRENCY.put(Currency.FIRO, Instant.parse("2016-10-06T20:30:00Z"));
        LISTING_START_BY_CURRENCY.put(Currency.ZEN, Instant.parse("2017-05-30T00:00:00Z"));
        LISTING_START_BY_CURRENCY.put(Currency.XVG, Instant.parse("2014-10-25T23:00:00Z"));
        LISTING_START_BY_CURRENCY.put(Currency.BUSD, Instant.parse("2019-09-20T15:25:00Z"));
        LISTING_START_BY_CURRENCY.put(Currency.THETA, Instant.parse("2018-01-17T17:30:00Z"));
        LISTING_START_BY_CURRENCY.put(Currency.ONT, Instant.parse("2018-03-08T07:10:00Z"));
        LISTING_START_BY_CURRENCY.put(Currency.AKRO, Instant.parse("2019-08-07T17:00:00Z"));
        LISTING_START_BY_CURRENCY.put(Currency.BTT, Instant.parse("2019-01-31T11:05:00Z"));
        LISTING_START_BY_CURRENCY.put(Currency.NANO, Instant.parse("2017-03-07T00:30:00Z"));
        LISTING_START_BY_CURRENCY.put(Currency.FIL, Instant.parse("2017-12-13T20:40:00Z"));
        LISTING_START_BY_CURRENCY.put(Currency.IOST, Instant.parse("2018-01-16T01:40:00Z"));
        LISTING_START_BY_CURRENCY.put(Currency.XEM, Instant.parse("2015-04-01T00:25:00Z"));
        LISTING_START_BY_CURRENCY.put(Currency.DENT, Instant.parse("2017-08-12T23:35:00Z"));
        LISTING_START_BY_CURRENCY.put(Currency.RVN, Instant.parse("2018-04-01T13:50:00Z"));
        LISTING_START_BY_CURRENCY.put(Currency.WAVES, Instant.parse("2016-06-02T21:10:00Z"));
        LISTING_START_BY_CURRENCY.put(Currency.TEL, Instant.parse("2018-01-15T00:15:00Z"));
        LISTING_START_BY_CURRENCY.put(Currency.UTK, Instant.parse("2017-12-29T19:40:00Z"));
        LISTING_START_BY_CURRENCY.put(Currency.GLM, Instant.parse("2016-11-18T07:30:00Z"));
        LISTING_START_BY_CURRENCY.put(Currency.MKR, Instant.parse("2017-01-29T19:15:00Z"));
        LISTING_START_BY_CURRENCY.put(Currency.NMR, Instant.parse("2017-06-23T04:50:00Z"));
        LISTING_START_BY_CURRENCY.put(Currency.PAXG, Instant.parse("2019-09-26T09:20:00Z"));
        LISTING_START_BY_CURRENCY.put(Currency.CAKE, Instant.parse("2021-02-05T14:50:00Z"));
        LISTING_START_BY_CURRENCY.put(Currency.BAL, Instant.parse("2020-09-01T13:30:00Z"));
        LISTING_START_BY_CURRENCY.put(Currency.BEAM, Instant.parse("2019-01-18T01:20:00Z"));

        SUPPORTED_QUOTES.add(Currency.USD);
        SUPPORTED_QUOTES.add(Currency.BTC);
    }

    public static final RateProviderDescriptor DESCRIPTOR = new RateProviderDescriptor(
        ID,
        List.copyOf(COIN_IDS_BY_CURRENCY.keySet()),
        RateProviderDescriptor.HIGH_PRIORITY
    );

    public CoinPaprikaRateProvider() {
        final ClientConfig config
            = new ClientConfig().addDefaultParam(HeaderParam.class, "User-Agent", "");

        Objects.requireNonNull(
            api = RestProxyFactory.createProxy(CoinPaprikaV1API.class, "https://api.coinpaprika.com/", config)
        );
    }

    @Override
    public Rate getRate(Currency base, Currency quote, Instant instant) {
        final CurrencyPair pair = new CurrencyPair(base, quote);
        if (!isSupported(pair)) {
            throw new IllegalArgumentException(String.format("Unsupported currency pair: '%s'", pair));
        }
        if (instant.isAfter(Instant.now())) {
            throw new IllegalArgumentException(
                String.format("Can't get rates for instant in the future: '%s'.", instant)
            );
        }
        final Instant listingStart = LISTING_START_BY_CURRENCY.get(base);
        if (instant.isBefore(listingStart)) {
            return null;
        }

        final Instant truncated =
            TimeUtils.truncate(instant, MIN_RATE_VALIDITY.getField(), MIN_RATE_VALIDITY.getCount());

        if (base.equals(quote)) {
            return new Rate(
                BigDecimal.ONE,
                base,
                quote,
                truncated,
                truncated.plus(MIN_RATE_VALIDITY.getDuration()),
                RateSourceType.FACT,
                CachingStrategy.DO_NOT_CACHE
            );
        }

        final String timeStamp =
            ZonedDateTime.ofInstant(instant, ZoneOffset.UTC).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);

        final String quoteStr = quote.name().toLowerCase();
        final String coinId = getCoinId(base);

        List<CoinPaprikaHistoricalTickerResponse> historical = null;
        boolean retry;
        int tryNo = 0;
        do {
            retry = false;
            try {
                waitForPossibleCall();
                tryNo++;
                historical = api.getHistorical(coinId, timeStamp, null, 1, quoteStr, null);
            } catch (IOException e) {
                if (!isTooManyRequests(e)) {
                    LOG.error(
                        String.format("Error getting historical rate (pair: '%s', instant: '%s').", pair, instant),
                        e
                    );
                    return null;
                }

                if (tryNo == 1) {
                    LOG.warn("Received HTTP status 429 (Too many requests). Retrying after sleep...");
                    try {
                        TimeUnit.MILLISECONDS.sleep(2 * CALL_DELAY.toMillis());
                    } catch (InterruptedException interruptedException) {
                        LOG.error("Interrupted.", e);
                        Thread.currentThread().interrupt();
                    }
                    retry = true;
                    continue;
                }

                LOG.warn("Received HTTP status 429 (Too many requests). Returning non-cacheable zero rate.");
                return new Rate(
                    BigDecimal.ZERO,
                    pair,
                    truncated,
                    truncated.plus(MIN_RATE_VALIDITY.getDuration()),
                    RateSourceType.MISSING,
                    CachingStrategy.DO_NOT_CACHE
                );
            }
        } while (retry);

        if (historical.isEmpty()) {
            LOG.error("Historical rate data is empty (pair: '{}', instant: '{}').", pair, instant);
            return null;
        }
        final CoinPaprikaHistoricalTickerResponse first = historical.get(0);
        final Instant timestamp = Instant.parse(first.timestamp);
        return new Rate(
            first.price,
            base,
            quote,
            timestamp,
            timestamp.plus(MIN_RATE_VALIDITY.getDuration()),
            RateSourceType.MARKET,
            CachingStrategy.LONG_TERM
        );
    }

    public boolean isTooManyRequests(IOException e) {
        return e instanceof HttpStatusIOException
            && ((HttpStatusIOException) e).getHttpStatusCode() == Response.Status.TOO_MANY_REQUESTS.getStatusCode();
    }

    public void logFetchError(Instant instant, CurrencyPair pair, IOException e) {
        LOG.error(
            String.format("Error getting historical rate (pair: '%s', instant: '%s').", pair, instant),
            e
        );
    }

    @Override
    public RateValidity getMinRateValidity() {
        return MIN_RATE_VALIDITY;
    }

    private String getCoinId(Currency base) {
        return COIN_IDS_BY_CURRENCY.get(base);
    }

    private boolean isSupported(CurrencyPair pair) {
        if (COIN_IDS_BY_CURRENCY.containsKey(pair.getBase())) {
            return SUPPORTED_QUOTES.contains(pair.getQuote());
        }
        return false;
    }

    private static synchronized void waitForPossibleCall() {
        final Instant now = Instant.now();
        final Duration sinceLastCall = Duration.between(LAST_CALL, now);
        final Duration waitingTime = CALL_DELAY.minus(sinceLastCall);
        if (!(waitingTime.isNegative() || waitingTime.isZero()) && waitingTime.compareTo(CALL_DELAY) <= 0) {
            try {
                final long waitMillis = waitingTime.toMillis();
                final long realSleepMillis = waitMillis == 0 ? 1 : waitMillis;
                if (LOG.isTraceEnabled()) {
                    LOG.trace(String.format("Going to sleep %d ms.", realSleepMillis));
                }
                TimeUnit.MILLISECONDS.sleep(realSleepMillis);
            } catch (InterruptedException e) {
                LOG.error("waitForPossibleCall", e);
                Thread.currentThread().interrupt();
            }
        }
        LAST_CALL = now;
    }

    public static void main(String[] args) {
        final CoinPaprikaRateProvider coinPaprikaRateSource = new CoinPaprikaRateProvider();
        for (Currency base : Set.of(Currency.ADA)) {
            for (Currency quote : Set.of(Currency.USD, Currency.BTC)) {
                final Rate rate = coinPaprikaRateSource.getRate(base, quote, Instant.parse("2020-01-01T00:00:00Z"));
                System.out.println("rate = " + rate);
            }
        }
    }
}
