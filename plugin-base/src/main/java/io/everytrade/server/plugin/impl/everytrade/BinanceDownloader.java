package io.everytrade.server.plugin.impl.everytrade;

import lombok.experimental.FieldDefaults;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.ExchangeFactory;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.binance.BinanceExchange;
import org.knowm.xchange.binance.service.BinanceFundingHistoryParams;
import org.knowm.xchange.binance.service.BinanceTradeHistoryParams;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.account.FundingRecord;
import org.knowm.xchange.dto.trade.UserTrade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static java.time.temporal.ChronoUnit.DAYS;
import static lombok.AccessLevel.PRIVATE;
import static org.apache.commons.lang.StringUtils.isEmpty;

@FieldDefaults(level = PRIVATE)
public class BinanceDownloader {

    private static final Logger LOG = LoggerFactory.getLogger(BinanceDownloader.class);

    private static final String STATE_SEPARATOR = "|";
    private static final Duration TRADE_HISTORY_WAIT_DURATION = Duration.ofMillis(250);
    private static final Duration FUNDING_HISTORY_WAIT_DURATION = Duration.ofMillis(100);
    private static final int TXS_PER_REQUEST = 1000;
    private static final int FUNDING_PER_REQUEST = 1000;

    Map<String, String> currencyPairLastIds = new HashMap<>();
    Date lastFundingDownloadedTimestamp = null;

    final Exchange exchange;

    public BinanceDownloader(String apiKey, String apiSecret, String downloadState) {
        this.exchange = ExchangeFactory.INSTANCE.createExchange(createExchangeSpec(apiKey, apiSecret));
        deserializeState(downloadState);
    }

    public List<UserTrade> downloadTrades(String currencyPairs, int maxCount) {
        final List<CurrencyPair> pairs = ConnectorUtils.toCurrencyPairs(currencyPairs);
        var params = (BinanceTradeHistoryParams) exchange.getTradeService().createTradeHistoryParams();
        params.setLimit(TXS_PER_REQUEST);

        final List<UserTrade> userTrades = new ArrayList<>();

        for (CurrencyPair pair : pairs) {
            params.setCurrencyPair(pair);
            String lastDownloadedTx = currencyPairLastIds.get(pair.toString());
            params.setStartId(lastDownloadedTx);

            while (userTrades.size() + TXS_PER_REQUEST < maxCount) {
                sleepBetweenRequests(TRADE_HISTORY_WAIT_DURATION);
                final List<UserTrade> userTradesBlock;
                try {
                    userTradesBlock = exchange.getTradeService().getTradeHistory(params).getUserTrades();
                } catch (Exception e) {
                    throw new IllegalStateException("User trade history download failed. ", e);
                }
                if (lastDownloadedTx != null && !userTradesBlock.isEmpty() && userTradesBlock.get(0).getId().equals(lastDownloadedTx)) {
                    userTradesBlock.remove(0);
                }
                if (userTradesBlock.isEmpty()) {
                    break;
                }
                userTrades.addAll(userTradesBlock);
                lastDownloadedTx = userTradesBlock.get(userTradesBlock.size() - 1).getId();
                params.setStartId(lastDownloadedTx);
            }
            currencyPairLastIds.put(pair.toString(), lastDownloadedTx);
        }
        return userTrades;
    }

    public List<FundingRecord> downloadDepositsAndWithdrawals(int maxCount) {
        var params = (BinanceFundingHistoryParams) exchange.getAccountService().createFundingHistoryParams();

        List<FundingRecord> result = new ArrayList<>();
        while (result.size() + FUNDING_PER_REQUEST < maxCount) {
            sleepBetweenRequests(FUNDING_HISTORY_WAIT_DURATION);

            if (lastFundingDownloadedTimestamp != null) {
                params.setStartTime(lastFundingDownloadedTimestamp);
                // API limit is 90 day window
                params.setEndTime(Date.from(lastFundingDownloadedTimestamp.toInstant().plus(89, DAYS)));
            }

            final List<FundingRecord> response;
            try {
                response = exchange.getAccountService().getFundingHistory(params);
            } catch (IOException e) {
                throw new IllegalStateException("User funding history download failed. ", e);
            }
            if (!response.isEmpty() && response.get(0).getDate().equals(lastFundingDownloadedTimestamp)) {
                response.remove(0);
            }
            if (response.size() < FUNDING_PER_REQUEST) {
                break;
            }
            result.addAll(response);
            lastFundingDownloadedTimestamp = response.get(response.size() - 1).getDate();
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
            .map(key -> key + "=" + currencyPairLastIds.get(key))
            .collect(Collectors.joining(":")) + STATE_SEPARATOR + lastFundingDownloadedTimestamp.getTime();
    }

    // deserialize last downloaded IDs and timestamps to be able to continue where left off
    private void deserializeState(String state) {
        if (isEmpty(state)) {
            return;
        }
        String[] array = state.contains(STATE_SEPARATOR) ? state.split("\"" + STATE_SEPARATOR) : new String[]{state};

        this.currencyPairLastIds = Arrays.stream(array[0].split(":"))
                .map(entry -> entry.split("="))
                .collect(Collectors.toMap(entry -> entry[0], entry -> entry[1]));
        if (array.length > 1) {
            this.lastFundingDownloadedTimestamp = new Date(Long.parseLong(array[1]));
        }
    }

    private ExchangeSpecification createExchangeSpec(String apiKey, String apiSecret) {
        ExchangeSpecification exSpec = new BinanceExchange().getDefaultExchangeSpecification();
        exSpec.setApiKey(apiKey);
        exSpec.setSecretKey(apiSecret);
        return exSpec;
    }
}
