package io.everytrade.server.plugin.impl.everytrade;

import lombok.experimental.FieldDefaults;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.binance.dto.meta.exchangeinfo.BinanceExchangeInfo;
import org.knowm.xchange.binance.dto.trade.BinanceTradeHistoryParams;
import org.knowm.xchange.binance.dto.account.BinanceFundingHistoryParams;
import org.knowm.xchange.binance.service.BinanceAccountService;
import org.knowm.xchange.binance.service.BinanceTradeService;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.account.FundingRecord;
import org.knowm.xchange.dto.trade.UserTrade;
import org.knowm.xchange.dto.trade.UserTrades;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static java.time.temporal.ChronoUnit.DAYS;
import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.joining;
import static lombok.AccessLevel.PRIVATE;
import static org.apache.commons.lang3.StringUtils.isEmpty;

@FieldDefaults(level = PRIVATE)
public class BinanceDownloader {

    private static final Logger LOG = LoggerFactory.getLogger(BinanceDownloader.class);

    private static final String STATE_SEPARATOR = "|";
    private static final Duration TRADE_HISTORY_WAIT_DURATION = Duration.ofMillis(250);
    private static final Duration FUNDING_HISTORY_WAIT_DURATION = Duration.ofMillis(100);
    private static final int LIMIT = 1000;

    //Funding
    private static final int FUNDING_PER_REQUEST = 1000;
    private static final Date EXCHANGE_OPENING_DATE = new GregorianCalendar(2017,06,01).getTime();
    private static long FUNDING_PERIOD_REQUEST = 88;
    private static final int MAX_FUNDING_REQUESTS = 25;

    //Convert
    private static final Date EXCHANGE_CONVERT_START_DATE =
        new GregorianCalendar(2017, 06, 1, 0, 0).getTime();
    private static final int CONVERT_MAX_REQUESTS = 12;
    private static final int CONVERT_MAX_TX_LIMIT = 1000;
    private static final long CONVERT_RANGE_OF_DAYS = 30L;
    private long convertStartTimestamp;
    private long convertEndTimestamp;

    Map<String, String> currencyPairLastIds = new HashMap<>();
    Date lastFundingDownloadedTimestamp = null;
    Date lastConvertDownloadedTimestamp = null;
    Exchange exchange;

    public BinanceDownloader(Exchange exchange, String downloadState) {
        this.exchange = exchange;
        deserializeState(downloadState);
    }

    public List<UserTrade> downloadTrades(String currencyPairs, boolean pairSettings, boolean isPaidSubscription) {
        BinanceTradeService tradeServices = (BinanceTradeService) exchange.getTradeService();
        BinanceAccountService accountService = (BinanceAccountService) exchange.getAccountService();
        BinanceTradeHistoryParams params = (BinanceTradeHistoryParams) exchange.getTradeService().createTradeHistoryParams();

        List<CurrencyPair> tradingSymbols = new ArrayList<>();

        if (!pairSettings && isPaidSubscription) {
            BinanceExchangeInfo allSymbols;
            try {
                allSymbols = accountService.getExchangeInfo();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            Arrays.stream(allSymbols.getSymbols()).filter(x -> x.getStatus().equals("TRADING")).forEach(x -> {
                tradingSymbols.add(new CurrencyPair(x.getBaseAsset(), x.getQuoteAsset()));
            });
        } else {
            tradingSymbols.addAll(ConnectorUtils.toCurrencyPairs(currencyPairs));
        }

        params.setLimit(LIMIT);
        List<UserTrade> trades = new ArrayList<>();

        for (CurrencyPair symbol : tradingSymbols) {
            String lastDownloadedTx = currencyPairLastIds.get(symbol.toString());
            long lastTradeId = isEmpty(lastDownloadedTx) ? 0L : Long.parseLong(lastDownloadedTx);
            params.setInstrument(symbol);
            params.setStartId(String.valueOf(lastTradeId));

            List<UserTrade> fetchedTrades;
            do {
                sleepBetweenRequests(TRADE_HISTORY_WAIT_DURATION);
                if (lastTradeId > 0) {
                    params.setStartId(String.valueOf(lastTradeId + 1));
                }

                UserTrades userTrades;
                try {
                    userTrades = tradeServices.getTradeHistory(params);
                } catch (IOException e) {
                    throw new IllegalStateException("User trade history download failed. " + e.getMessage() , e);
                }
                fetchedTrades = userTrades.getUserTrades();

                if (fetchedTrades.isEmpty()) {
                    break;
                }

                trades.addAll(fetchedTrades);

                String lastFetchedId = fetchedTrades.get(fetchedTrades.size() - 1).getId();
                lastTradeId = Long.parseLong(lastFetchedId);
                currencyPairLastIds.put(symbol.toString(), lastFetchedId);
            } while (fetchedTrades.size() == LIMIT);
        }
        return trades;
    }


    private void setNextConvertDates(long startId) {
        this.convertStartTimestamp = startId;
        this.convertEndTimestamp = startId + (1000L * 60 * 60 * 24 * CONVERT_RANGE_OF_DAYS);
    }

    public List<UserTrade> downloadConvertedTrades() {
        long now = Instant.now().getEpochSecond() * 1000L;
        if (lastConvertDownloadedTimestamp == null) {
            lastConvertDownloadedTimestamp = EXCHANGE_CONVERT_START_DATE;
        }
        setNextConvertDates(lastConvertDownloadedTimestamp.getTime());
        var params = (BinanceTradeHistoryParams) exchange.getTradeService().createTradeHistoryParams();
        var service = (BinanceTradeService) exchange.getTradeService();

        params.setLimit(CONVERT_MAX_TX_LIMIT);

        final List<UserTrade> converts = new ArrayList<>();
        int request = 0;
        while (request < CONVERT_MAX_REQUESTS) {
            params.setStartTime(new Date(convertStartTimestamp));
            params.setEndTime(new Date(convertEndTimestamp));
            sleepBetweenRequests(TRADE_HISTORY_WAIT_DURATION);
            final List<UserTrade> convertBlock;
            try {
                UserTrades convertHistory = service.getConvertHistory(params);
                convertBlock = convertHistory.getUserTrades();
            } catch (Exception e) {
                throw new IllegalStateException("User trade history download failed. ", e);
            }
            if (convertBlock.isEmpty()) {
                if (convertEndTimestamp > now) {
                    lastConvertDownloadedTimestamp = new Date(now);
                    break;
                } else {
                    lastConvertDownloadedTimestamp = new Date(convertEndTimestamp);
                    setNextConvertDates(convertEndTimestamp);
                    request++;
                }
            } else {

                if (convertBlock.size() < CONVERT_MAX_TX_LIMIT) {
                    if (convertEndTimestamp > now) {
                        lastConvertDownloadedTimestamp = new Date(now);
                        converts.addAll(convertBlock);
                        break;
                    } else {
                        lastConvertDownloadedTimestamp = new Date(convertEndTimestamp);
                        setNextConvertDates(convertEndTimestamp);
                        converts.addAll(convertBlock);
                        request++;
                    }
                } else {
                    long time = convertBlock.stream().max(comparing(trade -> trade.getTimestamp()
                        .getTime())).get().getTimestamp().getTime();
                    long lastTimestamp =
                        time + 1L;
                    converts.addAll(convertBlock);
                    setNextConvertDates(lastTimestamp);
                    lastConvertDownloadedTimestamp = new Date(lastTimestamp);
                    request++;
                }
            }
        }
        return converts;
    }

    public List<FundingRecord> downloadDepositsAndWithdrawals(int maxCount) {
        var params = (BinanceFundingHistoryParams) exchange.getAccountService().createFundingHistoryParams();
        var accountService = (BinanceAccountService) exchange.getAccountService();

        List<FundingRecord> result = new ArrayList<>();
        int requests = 0;
        while (result.size() + FUNDING_PER_REQUEST < maxCount && requests < MAX_FUNDING_REQUESTS) {
            sleepBetweenRequests(FUNDING_HISTORY_WAIT_DURATION);
            Date lastRequestTime = new Date();
            lastFundingDownloadedTimestamp = Objects.requireNonNullElse(lastFundingDownloadedTimestamp, EXCHANGE_OPENING_DATE);

            params.setStartTime(lastFundingDownloadedTimestamp);
            // API limit is 90 day window
            Date endDate = Date.from(lastFundingDownloadedTimestamp.toInstant().plus(FUNDING_PERIOD_REQUEST, DAYS));
            params.setEndTime(endDate);

            final List<FundingRecord> response;
            try {
                response = accountService.getFundingHistory(params);
            } catch (IOException e) {
                throw new IllegalStateException("User funding history download failed. ", e);
            }
            if (response.size() < FUNDING_PER_REQUEST) {
                lastFundingDownloadedTimestamp = lastRequestTime.after(endDate) ? endDate : lastRequestTime;
                result.addAll(response);
                if (lastFundingDownloadedTimestamp == lastRequestTime) {
                    break;
                }
            } else {
                FUNDING_PERIOD_REQUEST = Long.divideUnsigned(FUNDING_PERIOD_REQUEST,2); // too many results halve the period
            }
            requests++;
        }
        return result;
    }

    private void sleepBetweenRequests(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            LOG.warn("Sleep between binance API requests interrupted: {}", e.getMessage());
            Thread.currentThread().interrupt();
        }
    }

    // deserialize current state to String so it can be used next time
    public String serializeState() {
        // PAIR=TRADE_ID:PAIR2=TRADE_ID2[..]|LAST_FUNDING_DATE
        return currencyPairLastIds.keySet().stream()
            .filter(key -> currencyPairLastIds.get(key) != null)
            .map(key -> key + "=" + currencyPairLastIds.get(key))
            .collect(joining(":")) + STATE_SEPARATOR
            + (lastFundingDownloadedTimestamp == null ? EXCHANGE_OPENING_DATE.getTime() : lastFundingDownloadedTimestamp.getTime())
            + STATE_SEPARATOR
            + (lastConvertDownloadedTimestamp == null ? EXCHANGE_CONVERT_START_DATE.getTime() : lastConvertDownloadedTimestamp.getTime());
    }

    // deserialize last downloaded IDs and timestamps to be able to continue where left off
    private void deserializeState(String state) {
        if (isEmpty(state)) {
            return;
        }
        String[] array = state.contains(STATE_SEPARATOR) ? state.split("\\" + STATE_SEPARATOR) : new String[]{state};

        this.currencyPairLastIds = Arrays.stream(array[0].split(":"))
            .filter(split -> split.contains("=") && !split.endsWith("=") && !split.endsWith("=null"))
            .map(entry -> entry.split("="))
            .collect(Collectors.toMap(entry -> entry[0], entry -> entry[1]));

        if (array.length > 1) {
            this.lastFundingDownloadedTimestamp = new Date(Long.parseLong(array[1]));
        } else {
            this.lastFundingDownloadedTimestamp = EXCHANGE_OPENING_DATE;
        }
        if (array.length > 2) {
            this.lastConvertDownloadedTimestamp = new Date(Long.parseLong(array[2]));
        } else {
            this.lastConvertDownloadedTimestamp = EXCHANGE_CONVERT_START_DATE;
        }
    }
}
