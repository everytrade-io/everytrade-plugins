package io.everytrade.server.plugin.impl.everytrade;

import org.knowm.xchange.binance.service.BinanceTradeHistoryParams;
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

public class BinanceDownloader {
    //org.knowm.xchange.binance.BinanceResilience - 1200 request / 1 minute --> 10 user in 1 minute will be enough
    private static final int MAX_REQUEST_COUNT = 120;
    private static final int TX_PER_REQUEST = 1000;
    private final Map<String, String> currencyPairLastIds;
    private final TradeService tradeService;
    private final BinanceTradeHistoryParams tradeHistoryParams;

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
        final List<CurrencyPair> pairs = Arrays.stream(currencyPairs.split(","))
            .map(String::strip)
            .map(ConnectorUtils::createPair)
            .collect(Collectors.toList());

        final List<UserTrade> userTrades = new ArrayList<>();
        int sentRequests = 0;

        for (CurrencyPair pair : pairs) {
            tradeHistoryParams.setCurrencyPair(pair);
            String lastDownloadedTx = currencyPairLastIds.get(pair.toString());
            tradeHistoryParams.setStartId(lastDownloadedTx);

            while (sentRequests < MAX_REQUEST_COUNT) {
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

    public String getLastTransactionId() {
        return currencyPairLastIds.keySet().stream()
            .map(key -> key + "=" + currencyPairLastIds.get(key))
            .collect(Collectors.joining(":"));
    }
}
