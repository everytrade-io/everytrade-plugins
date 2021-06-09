package io.everytrade.server.plugin.impl.everytrade;

import org.knowm.xchange.binance.service.BinanceTradeHistoryParams;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.trade.UserTrade;
import org.knowm.xchange.service.trade.TradeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class BinanceDownloader {
    //org.knowm.xchange.binance.BinanceResilience - 240 request per IP / 1 minute --> 4 req / 1 sec
    private static final int MAX_REQUEST_COUNT = 20; // max 10000 txs per cycle
    private final Duration MIN_TIME_BETWEEN_REQUESTS = Duration.ofMillis(250);
    private static final int TX_PER_REQUEST = 500;
    private final Map<String, String> currencyPairLastIds;
    private final TradeService tradeService;
    private final BinanceTradeHistoryParams tradeHistoryParams;
    private final Logger log = LoggerFactory.getLogger(this.getClass());

    public BinanceDownloader(TradeService tradeService, String lastTransactionId) {
        Objects.requireNonNull(this.tradeService = tradeService);
        tradeHistoryParams = (BinanceTradeHistoryParams) tradeService.createTradeHistoryParams();
        tradeHistoryParams.setLimit(TX_PER_REQUEST);
        if (lastTransactionId == null) {
            currencyPairLastIds = new HashMap<>();
        } else {
            currencyPairLastIds = Arrays.stream(lastTransactionId.split(":"))
                .map(entry -> entry.split("="))
                .collect(Collectors.toMap(entry -> entry[0], entry -> entry[1]));
        }
    }

    public List<UserTrade> download(String currencyPairs) {
        final List<CurrencyPair> pairs = ConnectorUtils.toCurrencyPairs(currencyPairs);

        final List<UserTrade> userTrades = new ArrayList<>();
        int sentRequests = 0;

        for (CurrencyPair pair : pairs) {
            tradeHistoryParams.setCurrencyPair(pair);
            String lastDownloadedTx = currencyPairLastIds.get(pair.toString());
            tradeHistoryParams.setStartId(lastDownloadedTx);

            while (sentRequests < MAX_REQUEST_COUNT) {
                sleepBetweenRequests();
                final List<UserTrade> userTradesBlock;
                try {
                    userTradesBlock = tradeService.getTradeHistory(tradeHistoryParams).getUserTrades();
                } catch (Exception e) {
                    throw new IllegalStateException("User trade history download failed. ", e);
                }
                if (
                    lastDownloadedTx != null
                        && !userTradesBlock.isEmpty()
                        && userTradesBlock.get(0).getId().equals(lastDownloadedTx)
                ) {
                    userTradesBlock.remove(0);
                }
                if (userTradesBlock.isEmpty()) {
                    break;
                }
                userTrades.addAll(userTradesBlock);
                lastDownloadedTx = userTradesBlock.get(userTradesBlock.size() - 1).getId();
                tradeHistoryParams.setStartId(lastDownloadedTx);
                ++sentRequests;
            }
            currencyPairLastIds.put(pair.toString(), lastDownloadedTx);
        }
        return userTrades;
    }

    private void sleepBetweenRequests() {
        try {
            Thread.sleep(MIN_TIME_BETWEEN_REQUESTS.toMillis());
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
}
