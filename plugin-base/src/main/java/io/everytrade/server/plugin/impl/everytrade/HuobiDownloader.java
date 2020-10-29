package io.everytrade.server.plugin.impl.everytrade;

import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.trade.UserTrade;
import org.knowm.xchange.huobi.service.HuobiTradeHistoryParams;
import org.knowm.xchange.service.trade.TradeService;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class HuobiDownloader {
    // huobiapi.github.io/docs/spot/v1/en/#overview-2 --> 10 requests per API_KEY per second
    // 200 ms = 50% API_KEY capacity
    private static final Duration SLEEP_BETWEEN_REQUESTS = Duration.ofMillis(200);
    // txs count in request = 100, max 5 sec, 2.500 txs per cycle --> 25 requests
    private static final int MAX_REQUEST_COUNT = 25;
    public static final int MAX_LAST_TX_ID_LENGTH = 255;
    private final Map<String, HuobiDownloadState> currencyPairDownloadStates;
    private final TradeService tradeService;
    private final HuobiTradeHistoryParams tradeHistoryParams;

    public HuobiDownloader(TradeService tradeService, String lastTransactionId) {
        Objects.requireNonNull(this.tradeService = tradeService);
        tradeHistoryParams = (HuobiTradeHistoryParams) tradeService.createTradeHistoryParams();
        if (lastTransactionId == null) {
            currencyPairDownloadStates = new HashMap<>();
        } else {
            currencyPairDownloadStates = Arrays.stream(lastTransactionId.split("\\|"))
                .map(entry -> entry.split("="))
                .collect(Collectors.toMap(entry -> entry[0], entry -> HuobiDownloadState.parseFrom(entry[1])));
        }
    }

    public List<UserTrade> download(String currencyPairs) {
        final List<CurrencyPair> pairs = ConnectorUtils.toCurrencyPairs(currencyPairs);
        final List<UserTrade> userTrades = new ArrayList<>();
        int sentRequests = 0;
        for (CurrencyPair pair : pairs) {
            tradeHistoryParams.setCurrencyPair(pair);
            final HuobiDownloadState downloadState
                = currencyPairDownloadStates.getOrDefault(pair.toString(), HuobiDownloadState.parseFrom(null));

            while (sentRequests < MAX_REQUEST_COUNT) {
                try {
                    Thread.sleep(SLEEP_BETWEEN_REQUESTS.toMillis());
                } catch (InterruptedException e) {
                    throw new IllegalStateException("User trade history download sleep interrupted.", e);
                }
                tradeHistoryParams.setStartTime(downloadState.getWindowStart());
                tradeHistoryParams.setEndTime(downloadState.getWindowEnd());
                tradeHistoryParams.setStartId(downloadState.getFirstTxIdAfterGap());

                final List<UserTrade> userTradesBlock;
                try {
                    userTradesBlock = tradeService.getTradeHistory(tradeHistoryParams).getUserTrades();
                } catch (Exception e) {
                    throw new IllegalStateException("User trade history download failed. ", e);
                }
                final List<UserTrade> userTradesToAdd
                    = getUserTradesToAddAndUpdateState(userTradesBlock, downloadState);
                userTrades.addAll(userTradesToAdd);
                if (downloadState.isEnd()) {
                    break;
                }
                ++sentRequests;
            }
            currencyPairDownloadStates.put(pair.toString(), downloadState);
        }
        return userTrades;
    }


    private List<UserTrade> getUserTradesToAddAndUpdateState(
        List<UserTrade> userTradesBlock,
        HuobiDownloadState downloadState
    ) {
        //Sort ASC BY ID, because of xchange sorts by Date...is not unique
        userTradesBlock.sort(this::compare);
        final boolean isLastTxInBlockDuplicate = !userTradesBlock.isEmpty()
                && userTradesBlock.get(userTradesBlock.size() - 1)
            .getOrderId()
            .equals(downloadState.getFirstTxIdAfterGap());

        final List<UserTrade> userTradesToAdd = isLastTxInBlockDuplicate
            ? userTradesBlock.subList(0, userTradesBlock.size() - 1)
            : userTradesBlock;

        if (userTradesToAdd.isEmpty()) {
            downloadState.closeGap();
            downloadState.moveToNextWindow();
            return List.of();
        }

        final String lastTxIdToAdd = userTradesToAdd.get(userTradesToAdd.size() - 1).getOrderId();
        final String firstTxIdToAdd = userTradesToAdd.get(0).getOrderId();

        if (downloadState.isFirstDownloadInWindow()) {
            if (!downloadState.isGap()) {
                downloadState.setLastTxIdAfterGap(lastTxIdToAdd);
            }
            downloadState.setFirstTxIdAfterGap(firstTxIdToAdd);
            return userTradesToAdd;
        }

        final int duplicateIndex = findDuplicate(downloadState.getLastContinuousTxId(), userTradesToAdd);
        final boolean isDownloadedTxsInWindowContinuous = duplicateIndex > -1;
        if (isDownloadedTxsInWindowContinuous) {
            if (downloadState.isGap()) {
                downloadState.closeGap();
            } else {
                downloadState.setLastContinuousTxId(lastTxIdToAdd);
            }
            downloadState.moveToNextWindow();
            if (userTradesToAdd.size() == 1) {
                return List.of();
            } else {
                return userTradesToAdd.subList(duplicateIndex + 1, userTradesToAdd.size());
            }
        }

        downloadState.setFirstTxIdAfterGap(firstTxIdToAdd);
        if (!downloadState.isGap()) {
            downloadState.setLastTxIdAfterGap(lastTxIdToAdd);
        }

        return userTradesToAdd;
    }

    public String getLastTransactionId() {
        String result = currencyPairDownloadStates.keySet().stream()
            .map(key -> key + "=" + currencyPairDownloadStates.get(key).toString())
            .collect(Collectors.joining("|"));
        if (result.length() > MAX_LAST_TX_ID_LENGTH) {
            throw new IllegalStateException(String.format(
                "Last transaction ID's size '%d' is over limit.", result.length()
            ));
        }
        return result;
    }

    private int compare(UserTrade tradeA, UserTrade tradeB) {
        return Long.compare(Long.parseLong(tradeA.getOrderId()), Long.parseLong(tradeB.getOrderId()));
    }

    public  int findDuplicate(String transactionId, List<UserTrade> userTradesBlock) {
        for (int i = 0; i < userTradesBlock.size(); i++) {
            final UserTrade userTrade = userTradesBlock.get(i);
            if (userTrade.getOrderId().equals(transactionId)) {
                return i;
            }
        }
        return -1;
    }

}
