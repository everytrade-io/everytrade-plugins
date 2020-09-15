package io.everytrade.server.plugin.impl.everytrade;

import org.knowm.xchange.coinbasepro.dto.trade.CoinbaseProTradeHistoryParams;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.trade.UserTrade;
import org.knowm.xchange.service.trade.TradeService;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class CoinbaseProDownloader {
    //https://docs.pro.coinbase.com/#rate-limits - max 5 request per user per second --> 200 ms between requests
    private static final int TX_PER_REQUEST = 100;
    private static final int MAX_REQUEST_COUNT = 3000;
    private static final int SLEEP_BETWEEN_REQUESTS_MS = 200;
    private final Map<String, Integer> currencyPairLastIds;
    private final TradeService tradeService;
    private final CoinbaseProTradeHistoryParams tradeHistoryParams;

    public CoinbaseProDownloader(TradeService tradeService, String lastTransactionId) {
        Objects.requireNonNull(this.tradeService = tradeService);
        tradeHistoryParams = (CoinbaseProTradeHistoryParams) tradeService.createTradeHistoryParams();
        tradeHistoryParams.setLimit(TX_PER_REQUEST);
        if (lastTransactionId == null) {
            currencyPairLastIds = new HashMap<>();
        } else {
            currencyPairLastIds = Arrays.stream(lastTransactionId.split(":"))
                .map(entry -> entry.split("="))
                .collect(Collectors.toMap(entry -> entry[0], entry -> Integer.parseInt(entry[1])));
        }
    }

    public List<UserTrade> download(String currencyPairs) {
        final List<CurrencyPair> pairs = Arrays.stream(currencyPairs.split(","))
            .map(String::strip)
            .map(ConnectorUtils::createPair)
            .collect(Collectors.toList());
        final List<UserTrade> userTrades = new ArrayList<>();
        int sentRequests = 0;

        for (CurrencyPair pair : pairs) {
            tradeHistoryParams.setCurrencyPair(pair);
            final Integer lastDownloadedTxFound = currencyPairLastIds.get(pair.toString());
            int lastDownloadedTx = lastDownloadedTxFound == null ? 1 : lastDownloadedTxFound;

            while (sentRequests < MAX_REQUEST_COUNT) {
                tradeHistoryParams.setBeforeTradeId(lastDownloadedTx);
                final List<UserTrade> userTradesBlock;
                try {
                    Thread.sleep(SLEEP_BETWEEN_REQUESTS_MS);
                } catch (InterruptedException e) {
                    throw new IllegalStateException("User trade history download sleep interrupted.", e);
                }
                try {
                    userTradesBlock = tradeService.getTradeHistory(tradeHistoryParams).getUserTrades();
                } catch (Exception e) {
                    throw new IllegalStateException("User trade history download failed. ", e);
                }
                if (userTradesBlock.isEmpty()) {
                    break;
                }
                userTrades.addAll(userTradesBlock);
                lastDownloadedTx = Integer.parseInt(userTradesBlock.get(userTradesBlock.size() - 1).getId());

                ++sentRequests;
            }
            currencyPairLastIds.put(pair.toString(), lastDownloadedTx);
        }

        return userTrades;

    }

    public String getLastTransactionId() {
        return currencyPairLastIds.keySet().stream()
            .map(key -> key + "=" + currencyPairLastIds.get(key))
            .collect(Collectors.joining(":"));
    }
}