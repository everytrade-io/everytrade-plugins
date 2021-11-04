package io.everytrade.server.plugin.impl.everytrade;

import org.knowm.xchange.coinbase.v2.service.CoinbaseTradeHistoryParams;
import org.knowm.xchange.coinbase.v2.service.CoinbaseTradeService;
import org.knowm.xchange.dto.trade.UserTrade;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static org.knowm.xchange.bitfinex.service.BitfinexAdapters.log;

public class CoinbaseDownloader {
    //https://developers.coinbase.com/api/v2#rate-limiting 10.000 / API-KEY / hour ---> 2500 / 15 min
    private static final int MAX_REQUEST_COUNT = 2500;
    private static final String DASH_SYMBOL = "-";
    private static final String COLON_SYMBOL = ":";
    private static final String PIPE_SYMBOL = "|";
    private static final int TRANSACTIONS_PER_REQUEST_LIMIT = 100;
    private final CoinbaseTradeService tradeService;
    private final Map<String, WalletState> actualWalletStates = new HashMap<>();

    public CoinbaseDownloader(
        CoinbaseTradeService tradeService,
        String lastTransactionId,
        Set<String> actualWalletIds
    ) {
        Objects.requireNonNull(this.tradeService = tradeService);
        Map<String, WalletState> previousWalletStates;
        if (lastTransactionId == null) {
            previousWalletStates = new HashMap<>();
        } else {
            previousWalletStates = Arrays.stream(lastTransactionId.split("\\" + PIPE_SYMBOL))
                .map(entry -> entry.split(COLON_SYMBOL))
                .collect(Collectors.toMap(entry -> entry[0], entry -> new WalletState(entry[1], entry[2])));
        }

        for (String actualWalletId : actualWalletIds) {
            final WalletState walletState = previousWalletStates.get(actualWalletId);
            actualWalletStates.put(actualWalletId, Objects.requireNonNullElseGet(
                walletState,
                () -> new WalletState(null, null))
            );
        }
    }

    public List<UserTrade> download() {
        final List<UserTrade> userTrades = new ArrayList<>();
        int sentRequests = 0;
        final CoinbaseTradeHistoryParams tradeHistoryParams
            = (CoinbaseTradeHistoryParams) tradeService.createTradeHistoryParams();
        tradeHistoryParams.setLimit(TRANSACTIONS_PER_REQUEST_LIMIT);

        for (Map.Entry<String, WalletState> entry : actualWalletStates.entrySet()) {
            String lastBuyId = entry.getValue().lastBuyId;
            String lastSellId = entry.getValue().lastSellId;
            final String walletId = entry.getKey();

            while (sentRequests < MAX_REQUEST_COUNT) {
                ++sentRequests;
                tradeHistoryParams.setStartId(lastBuyId);
                final List<UserTrade> buysTradeHistoryBlock;
                try {
                    buysTradeHistoryBlock
                        = tradeService.getBuyTradeHistory(tradeHistoryParams, walletId).getUserTrades();
                } catch (IOException e) {
                    throw new IllegalStateException("Download buys history failed.", e);
                }

                if (buysTradeHistoryBlock.isEmpty()) {
                    break;
                }

                userTrades.addAll(buysTradeHistoryBlock);
                lastBuyId = buysTradeHistoryBlock.get(0).getId();
            }

            while (sentRequests < MAX_REQUEST_COUNT) {
                ++sentRequests;
                tradeHistoryParams.setStartId(lastSellId);
                final List<UserTrade> sellsTradeHistoryBlock;
                try {
                    sellsTradeHistoryBlock = tradeService.getSellTradeHistory(tradeHistoryParams, walletId).getUserTrades();
                } catch (IOException e) {
                    throw new IllegalStateException("Download sells history failed.", e);
                }

                if (sellsTradeHistoryBlock.isEmpty()) {
                    break;
                }

                userTrades.addAll(sellsTradeHistoryBlock);
                lastSellId = sellsTradeHistoryBlock.get(0).getId();

            }
            if (sentRequests == MAX_REQUEST_COUNT) {
                log.info("Max request count {} has been achieved.", MAX_REQUEST_COUNT);
            }

            final WalletState walletState = actualWalletStates.get(walletId);
            walletState.lastBuyId = lastBuyId;
            walletState.lastSellId = lastSellId;

        }
        return userTrades;
    }

    public String getLastTransactionId() {
        final String result = actualWalletStates.entrySet().stream()
            .filter(entry -> entry.getValue().lastBuyId != null || entry.getValue().lastSellId != null)
            .map(
                entry -> entry.getKey()
                    + COLON_SYMBOL
                    + Objects.requireNonNullElse(entry.getValue().lastBuyId, DASH_SYMBOL)
                    + COLON_SYMBOL
                    + Objects.requireNonNullElse(entry.getValue().lastSellId, DASH_SYMBOL)
            )
            .collect(Collectors.joining(PIPE_SYMBOL));

        return result;
    }

    private static class WalletState {
        private String lastBuyId;
        private String lastSellId;

        public WalletState(String lastBuyId, String lastSellId) {
            this.lastBuyId = DASH_SYMBOL.equals(lastBuyId) ? null : lastBuyId;
            this.lastSellId = DASH_SYMBOL.equals(lastSellId) ? null : lastSellId;
        }
    }
}