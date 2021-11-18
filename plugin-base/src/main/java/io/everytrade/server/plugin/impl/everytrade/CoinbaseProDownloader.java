package io.everytrade.server.plugin.impl.everytrade;

import lombok.experimental.FieldDefaults;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.coinbasepro.dto.trade.CoinbaseProTradeHistoryParams;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.account.FundingRecord;
import org.knowm.xchange.dto.trade.UserTrade;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static java.util.Collections.emptyList;
import static lombok.AccessLevel.PRIVATE;

@FieldDefaults(makeFinal = false, level = PRIVATE)
public class CoinbaseProDownloader {
    //https://docs.pro.coinbase.com/#rate-limits - max 5 request per user per second --> 200 ms between requests
    private static final int TX_PER_REQUEST = 100;
    private static final int MAX_REQUEST_COUNT = 3000;
    private static final int SLEEP_BETWEEN_REQUESTS_MS = 200;
    public static final int FIRST_COINBASE_TX_ID = 1;


    Map<String, Integer> currencyPairLastIds;
    Exchange exchange;

    public CoinbaseProDownloader(Exchange exchange, String downloadState) {
        this.exchange = exchange;

        if (downloadState == null) {
            currencyPairLastIds = new HashMap<>();
        } else {
            currencyPairLastIds = Arrays.stream(downloadState.split(":"))
                .map(entry -> entry.split("="))
                .collect(Collectors.toMap(entry -> entry[0], entry -> Integer.parseInt(entry[1])));
        }
    }

    public List<UserTrade> download(String currencyPairs) {
        var tradeService = exchange.getTradeService();
        var params = (CoinbaseProTradeHistoryParams) tradeService.createTradeHistoryParams();
        params.setLimit(TX_PER_REQUEST);

        final List<CurrencyPair> pairs = ConnectorUtils.toCurrencyPairs(currencyPairs);
        final List<UserTrade> userTrades = new ArrayList<>();
        int sentRequests = 0;

        for (CurrencyPair pair : pairs) {
            params.setCurrencyPair(pair);
            final Integer lastDownloadedTxFound = currencyPairLastIds.get(pair.toString());
            int lastDownloadedTx = lastDownloadedTxFound == null ? FIRST_COINBASE_TX_ID : lastDownloadedTxFound;

            while (sentRequests < MAX_REQUEST_COUNT) {
                params.setBeforeTradeId(lastDownloadedTx);
                final List<UserTrade> userTradesBlock;
                try {
                    Thread.sleep(SLEEP_BETWEEN_REQUESTS_MS);
                } catch (InterruptedException e) {
                    throw new IllegalStateException("User trade history download sleep interrupted.", e);
                }
                try {
                    userTradesBlock = tradeService.getTradeHistory(params).getUserTrades();
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

    public List<FundingRecord> downloadFunding(String currencyPairs) {
        var accService = exchange.getAccountService();
        var params = (CoinbaseProTradeHistoryParams) accService.createFundingHistoryParams();
        params.setLimit(TX_PER_REQUEST);

        // TODO
        try {
            return accService.getFundingHistory(params);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return emptyList();
    }

    public String getLastTransactionId() {
        return currencyPairLastIds.keySet().stream()
            .map(key -> key + "=" + currencyPairLastIds.get(key))
            .collect(Collectors.joining(":"));
    }
}