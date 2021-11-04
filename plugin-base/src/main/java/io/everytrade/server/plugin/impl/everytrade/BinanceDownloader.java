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

import static lombok.AccessLevel.PRIVATE;

@FieldDefaults(makeFinal = true, level = PRIVATE)
public class BinanceDownloader {
    //org.knowm.xchange.binance.BinanceResilience - 240 request per IP / 1 minute --> 4 req / 1 sec

    static Duration TRADE_HISTORY_WAIT_DURATION = Duration.ofMillis(250);
    static Duration FUNDING_HISTORY_WAIT_DURATION = Duration.ofMillis(100);

    static int TXS_PER_REQUEST = 1000;
    static int FUNDINGS_PER_REQUEST = 1000;

    static Logger log = LoggerFactory.getLogger(BinanceDownloader.class);

    Map<String, String> currencyPairLastIds;
    Exchange exchange;
    BinanceTradeHistoryParams tradeHistoryParams;
    BinanceFundingHistoryParams fundingHistoryParams;

    public BinanceDownloader(String apiKey, String apiSecret, String lastTransactionId) {
        this.exchange = ExchangeFactory.INSTANCE.createExchange(createExchangeSpec(apiKey, apiSecret));
        this.tradeHistoryParams = (BinanceTradeHistoryParams) exchange.getTradeService().createTradeHistoryParams();
        this.tradeHistoryParams.setLimit(TXS_PER_REQUEST);
        this.fundingHistoryParams = (BinanceFundingHistoryParams) exchange.getAccountService().createFundingHistoryParams();

        this.currencyPairLastIds =
            lastTransactionId == null ? new HashMap<>() : Arrays.stream(lastTransactionId.split(":"))
                .map(entry -> entry.split("="))
                .collect(Collectors.toMap(entry -> entry[0], entry -> entry[1]));
    }

    public List<UserTrade> downloadTrades(String currencyPairs, int maxCount) {
        final List<CurrencyPair> pairs = ConnectorUtils.toCurrencyPairs(currencyPairs);

        final List<UserTrade> userTrades = new ArrayList<>();

        for (CurrencyPair pair : pairs) {
            tradeHistoryParams.setCurrencyPair(pair);
            String lastDownloadedTx = currencyPairLastIds.get(pair.toString());
            tradeHistoryParams.setStartId(lastDownloadedTx);

            while (userTrades.size() + TXS_PER_REQUEST < maxCount) {
                sleepBetweenRequests(TRADE_HISTORY_WAIT_DURATION);
                final List<UserTrade> userTradesBlock;
                try {
                    userTradesBlock = exchange.getTradeService().getTradeHistory(tradeHistoryParams).getUserTrades();
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
                tradeHistoryParams.setStartId(lastDownloadedTx);
            }
            currencyPairLastIds.put(pair.toString(), lastDownloadedTx);
        }
        return userTrades;
    }

    public List<FundingRecord> downloadDepositsAndWithdrawals(int maxCount) {
        List<FundingRecord> result = new ArrayList<>();

        Date lastDownloadedTimestamp = null;
        while (result.size() + FUNDINGS_PER_REQUEST < maxCount) {
            sleepBetweenRequests(FUNDING_HISTORY_WAIT_DURATION);
            final List<FundingRecord> response;
            try {
                response = exchange.getAccountService().getFundingHistory(fundingHistoryParams);
            } catch (IOException e) {
                throw new IllegalStateException("User funding history download failed. ", e);
            }
            if (!response.isEmpty() && response.get(0).getDate().equals(lastDownloadedTimestamp)) {
                response.remove(0);
            }
            if (response.isEmpty()) {
                break;
            }
            result.addAll(response);
            lastDownloadedTimestamp = response.get(response.size() - 1).getDate();
            fundingHistoryParams.setStartTime(lastDownloadedTimestamp);
        }
        return result;
    }

    private void sleepBetweenRequests(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            log.warn("Sleep between binance API requests interrupted: {}", e.getMessage());
            Thread.currentThread().interrupt();
        }
    }

    public String getLastTransactionId() {
        return currencyPairLastIds.keySet().stream()
            .map(key -> key + "=" + currencyPairLastIds.get(key))
            .collect(Collectors.joining(":"));
    }

    private ExchangeSpecification createExchangeSpec(String apiKey, String apiSecret) {
        ExchangeSpecification exSpec = new BinanceExchange().getDefaultExchangeSpecification();
        exSpec.setApiKey(apiKey);
        exSpec.setSecretKey(apiSecret);
        return exSpec;
    }
}
