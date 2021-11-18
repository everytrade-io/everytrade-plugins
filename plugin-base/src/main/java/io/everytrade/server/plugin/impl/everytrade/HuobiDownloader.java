package io.everytrade.server.plugin.impl.everytrade;

import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.experimental.FieldDefaults;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.account.FundingRecord;
import org.knowm.xchange.dto.trade.UserTrade;
import org.knowm.xchange.huobi.service.HuobiFundingHistoryParams;
import org.knowm.xchange.huobi.service.HuobiTradeHistoryParams;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static io.everytrade.server.plugin.impl.everytrade.ConnectorUtils.findDuplicateFunding;
import static java.util.Comparator.comparing;
import static lombok.AccessLevel.PRIVATE;

@AllArgsConstructor
@FieldDefaults(makeFinal = true, level = PRIVATE)
public class HuobiDownloader {
    // huobiapi.github.io/docs/spot/v1/en/#overview-2 --> 10 requests per API_KEY per second
    // 200 ms = 50% API_KEY capacity
    private static final Duration SLEEP_BETWEEN_REQUESTS = Duration.ofMillis(200);
    // txs count in request = 100, max 5 sec, 2.500 txs per cycle --> 25 requests
    private static final int MAX_REQUEST_COUNT = 25;
    private static final String FUNDING_STATE_KEY = "funding";

    @NonNull
    Exchange exchange;

    public List<UserTrade> downloadTrades(String currencyPairs, Map<String, HuobiDownloadState> state) {
        final List<CurrencyPair> pairs = ConnectorUtils.toCurrencyPairs(currencyPairs);
        final List<UserTrade> userTrades = new ArrayList<>();
        int sentRequests = 0;

        var tradeService = exchange.getTradeService();
        var params = (HuobiTradeHistoryParams) tradeService.createTradeHistoryParams();
        for (CurrencyPair pair : pairs) {
            params.setCurrencyPair(pair);
            final HuobiDownloadState downloadState = state.getOrDefault(pair.toString(), HuobiDownloadState.parseFrom(null));

            while (sentRequests < MAX_REQUEST_COUNT) {
                waitBetweenRequests();
                params.setStartTime(downloadState.getWindowStart());
                params.setEndTime(downloadState.getWindowEnd());
                params.setStartId(downloadState.getFirstTxIdAfterGap());

                final List<UserTrade> userTradesBlock;
                try {
                    userTradesBlock = tradeService.getTradeHistory(params).getUserTrades();
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
            state.put(pair.toString(), downloadState);
        }
        return userTrades;
    }

    public List<FundingRecord> downloadFunding(Map<String, HuobiDownloadState> state) {
        final List<FundingRecord> result = new ArrayList<>();
        int sentRequests = 0;
        var fundingState = state.getOrDefault(FUNDING_STATE_KEY, HuobiDownloadState.parseFrom(null));

        var accountService = exchange.getAccountService();
        var params = (HuobiFundingHistoryParams) accountService.createFundingHistoryParams();

        for (FundingRecord.Type type : FundingRecord.Type.values()) {
            while (sentRequests < MAX_REQUEST_COUNT) {
                waitBetweenRequests();
                params.setType(type);
                params.setStartId(fundingState.getFirstTxIdAfterGap());

                final List<FundingRecord> fundingBlock;
                try {
                    fundingBlock = accountService.getFundingHistory(params);
                } catch (Exception e) {
                    throw new IllegalStateException("User funding history download failed. ", e);
                }
                var fundingToAdd = getFundingToAddAndUpdateState(fundingBlock, fundingState);
                result.addAll(fundingToAdd);
                if (fundingState.isEnd()) {
                    break;
                }
                ++sentRequests;
            }
        }
        state.put(FUNDING_STATE_KEY, fundingState);
        return result;
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

    private List<FundingRecord> getFundingToAddAndUpdateState(List<FundingRecord> fundingBlock, HuobiDownloadState downloadState) {
        //Sort ASC BY ID, because of xchange sorts by Date...is not unique
        fundingBlock.sort(comparing(FundingRecord::getInternalId));

        final boolean isLastTxInBlockDuplicate = !fundingBlock.isEmpty()
            && fundingBlock.get(fundingBlock.size() - 1).getInternalId().equals(downloadState.getFirstTxIdAfterGap());

        var fundingToAdd = isLastTxInBlockDuplicate ? fundingBlock.subList(0, fundingBlock.size() - 1) : fundingBlock;

        if (fundingToAdd.isEmpty()) {
            downloadState.closeGap();
            downloadState.moveToNextWindow();
            return List.of();
        }

        final String lastTxIdToAdd = fundingToAdd.get(fundingToAdd.size() - 1).getInternalId();
        final String firstTxIdToAdd = fundingToAdd.get(0).getInternalId();

        if (downloadState.isFirstDownloadInWindow()) {
            if (!downloadState.isGap()) {
                downloadState.setLastTxIdAfterGap(lastTxIdToAdd);
            }
            downloadState.setFirstTxIdAfterGap(firstTxIdToAdd);
            return fundingToAdd;
        }

        final int duplicateIndex = findDuplicateFunding(downloadState.getLastContinuousTxId(), fundingToAdd);
        final boolean isDownloadedTxsInWindowContinuous = duplicateIndex > -1;
        if (isDownloadedTxsInWindowContinuous) {
            if (downloadState.isGap()) {
                downloadState.closeGap();
            } else {
                downloadState.setLastContinuousTxId(lastTxIdToAdd);
            }
            downloadState.moveToNextWindow();
            if (fundingToAdd.size() == 1) {
                return List.of();
            } else {
                return fundingToAdd.subList(duplicateIndex + 1, fundingToAdd.size());
            }
        }

        downloadState.setFirstTxIdAfterGap(firstTxIdToAdd);
        if (!downloadState.isGap()) {
            downloadState.setLastTxIdAfterGap(lastTxIdToAdd);
        }
        return fundingToAdd;
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

    private void waitBetweenRequests() {
        try {
            Thread.sleep(SLEEP_BETWEEN_REQUESTS.toMillis());
        } catch (InterruptedException e) {
            throw new IllegalStateException("User funding history download sleep interrupted.", e);
        }
    }
}
