package io.everytrade.server.plugin.impl.everytrade.rateprovider;

import com.generalbytes.batm.server.extensions.extra.bitcoin.sources.coinpaprika.CoinPaprikaGlobalResponse;
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

    public static final RateValidity MIN_RATE_VALIDITY = RateValidity.QUARTER_HOUR;

    private static final Map<Currency, String> COIN_IDS_BY_CURRENCY = new EnumMap<>(Currency.class);
    private static final Map<Currency, Instant> LISTING_START_BY_CURRENCY = new EnumMap<>(Currency.class);
    private static final Set<Currency> SUPPORTED_QUOTES = new HashSet<>();
    private static final Duration CALL_DELAY = Duration.of(250, ChronoUnit.MILLIS);
    private static final Logger LOG = LoggerFactory.getLogger(CoinPaprikaRateProvider.class);
    private static Instant LAST_CALL = Instant.now();

    public static final String ORIGIN = "CoinPaprika";
    public static final String ID = EveryTradePlugin.ID + IPlugin.PLUGIN_PATH_SEPARATOR + "etApiConnector";

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

        // Rates with earliest timestamp available:
        //        BTC/USD = Rate{value=135.3, base='BTC', quote='USD', instant=2013-04-28T18:45:00Z}
        //        BTC/BTC = Rate{value=1, base='BTC', quote='BTC', instant=2009-01-03T00:00:00Z}

        //        ETH/USD = Rate{value=2.83, base='ETH', quote='USD', instant=2015-08-07T14:45:00Z}
        //        ETH/BTC = Rate{value=0.0101411, base='ETH', quote='BTC', instant=2015-08-07T14:45:00Z}

        //        LTC/USD = Rate{value=4.3, base='LTC', quote='USD', instant=2013-04-28T18:45:00Z}
        //        LTC/BTC = Rate{value=0.0316211, base='LTC', quote='BTC', instant=2013-04-28T18:45:00Z}

        //        BCH/USD = Rate{value=299.21, base='BCH', quote='USD', instant=2017-08-01T05:45:00Z}
        //        BCH/BTC = Rate{value=0.103046, base='BCH', quote='BTC', instant=2017-08-01T05:45:00Z}

        //        XRP/USD = Rate{value=0.005874, base='XRP', quote='USD', instant=2013-08-04T18:50:00Z}
        //        XRP/BTC = Rate{value=0.000056, base='XRP', quote='BTC', instant=2013-08-04T18:50:00Z}

        //        XMR/USD = Rate{value=1.83, base='XMR', quote='USD', instant=2014-05-21T09:30:00Z}
        //        XMR/BTC = Rate{value=0.003712, base='XMR', quote='BTC', instant=2014-05-21T09:30:00Z}

        //        DAI/USD = Rate{value=0.915159, base='DAI', quote='USD', instant=2017-12-27T01:30:00Z}
        //        DAI/BTC = Rate{value=0.000057, base='DAI', quote='BTC', instant=2017-12-27T01:30:00Z}

        //        DASH/USD = Rate{value=0.213954, base='DASH', quote='USD', instant=2014-02-14T13:50:00Z}
        //        DASH/BTC = Rate{value=0.000341, base='DASH', quote='BTC', instant=2014-02-14T13:50:00Z}

        //        USDT/USD = Rate{value=1, base=USDT, quote=USD, timestamp=2015-03-06T13:05:00Z, sourceType=MARKET}
        //        USDT/USD = Rate{value=0, base=USDT, quote=BTC, timestamp=2015-03-06T13:05:00Z, sourceType=MARKET}

        //        BNB/USD = Rate{value=0.000042, base=BNB, quote=BTC, timestamp=2017-07-25T04:30:00Z, sourceType=MARKET}
        //        BNB/BTC = Rate{value=0.000042, base=BNB, quote=BTC, timestamp=2017-07-25T04:30:00Z, sourceType=MARKET}

        SUPPORTED_QUOTES.add(Currency.USD);
        SUPPORTED_QUOTES.add(Currency.BTC);
    }

    public static final RateProviderDescriptor DESCRIPTOR = new RateProviderDescriptor(
        ID,
        List.copyOf(COIN_IDS_BY_CURRENCY.keySet())
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

    public CoinPaprikaGlobalResponse getGlobal() {
        try {
            return api.getGlobal();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public static void main(String[] args) {
        final CoinPaprikaRateProvider coinPaprikaRateSource = new CoinPaprikaRateProvider();
        final CurrencyPair pair = new CurrencyPair("LINK", "USD");
        final Instant instant = Instant.parse("2017-12-24T18:00:00.00Z");
        System.out.println("instant = " + instant);
        final Currency base = pair.getBase();
        final Currency quote = pair.getQuote();
        Rate rate = coinPaprikaRateSource.getRate(base, quote, instant);

        System.out.println("rate = " + rate);
        Instant now1 = Instant.now();
        Instant now2 = Instant.now();
        System.out.println("now1 = " + now1);
        System.out.println("now2 = " + now2);
        System.out.println("Duration.between(now2,now1).isNegative() = " + Duration.between(now2, now1).isNegative());
    }
}
