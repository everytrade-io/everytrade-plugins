package io.everytrade.server.plugin.impl.everytrade;

import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.experimental.FieldDefaults;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.dto.account.FundingRecord;
import org.knowm.xchange.dto.trade.UserTrade;
import org.knowm.xchange.huobi.HuobiUtils;
import org.knowm.xchange.huobi.dto.marketdata.HuobiAssetPair;
import org.knowm.xchange.huobi.service.HuobiFundingHistoryParams;
import org.knowm.xchange.huobi.service.HuobiTradeHistoryParams;
import org.knowm.xchange.huobi.service.HuobiTradeService;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static io.everytrade.server.plugin.impl.everytrade.ConnectorUtils.findDuplicateFunding;
import static java.util.Comparator.comparing;
import static lombok.AccessLevel.PRIVATE;
import static org.knowm.xchange.dto.account.FundingRecord.Type.DEPOSIT;
import static org.knowm.xchange.dto.account.FundingRecord.Type.WITHDRAWAL;

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
        List<String> pairs = new ArrayList<>();
        if (currencyPairs != null) {
           pairs = Arrays.stream(currencyPairs.split(","))
                .map(x -> x.replace("/", "").toLowerCase())
                .toList();
        }
        HuobiAssetPair[] symbolPairs = HuobiUtils.getHuobiSymbolPairs();
        final List<UserTrade> userTrades = new ArrayList<>();
        var tradeService = (HuobiTradeService) exchange.getTradeService();
        var params = (HuobiTradeHistoryParams) tradeService.createTradeHistoryParams();
        String lastTxId = "";

        if (currencyPairs == null || currencyPairs.isBlank()) {
            pairs = Arrays.stream(symbolPairs).map(HuobiAssetPair::getSymbol).toList();
        }

        for (String pair : pairs) {
            final HuobiDownloadState downloadState = state.getOrDefault(pair, HuobiDownloadState.parseFrom(null));
            do {
                waitBetweenRequests();
                params.setStartTime(downloadState.getWindowStart());
                params.setStartId(downloadState.getLastTxIdAfterGap());
                final List<UserTrade> userTradesBlock;
                try {
                    userTradesBlock = tradeService.getTradeHistory(params, pair).getUserTrades();
                    if (userTradesBlock.isEmpty()) {
                        break;
                    }
                } catch (Exception e) {
                    throw new IllegalStateException("User trade history download failed. ", e);
                }
                if (!userTrades.isEmpty()) {
                    lastTxId = userTrades.get(userTrades.size() - 1).getOrderId();
                }
                final List<UserTrade> userTradesToAdd = getUserTradesToAddAndUpdateState(lastTxId, userTradesBlock, downloadState);
                userTrades.addAll(userTradesToAdd);
            } while (!downloadState.isEnd());

            state.put(pair, downloadState);
        }
        return userTrades;
    }

    public List<FundingRecord> downloadFunding(Map<String, HuobiDownloadState> state) {
        final List<FundingRecord> result = new ArrayList<>();
        int sentRequests = 0;
        var fundingState = state.getOrDefault(FUNDING_STATE_KEY, HuobiDownloadState.parseFrom(null));
        String lastTxId = "";

        var accountService = exchange.getAccountService();
        var params = (HuobiFundingHistoryParams) accountService.createFundingHistoryParams();

        for (FundingRecord.Type type : List.of(WITHDRAWAL, DEPOSIT)) {
            while (sentRequests < MAX_REQUEST_COUNT) {
                waitBetweenRequests();
                params.setType(type);
                params.setStartId(fundingState.getLastTxIdAfterGap());

                final List<FundingRecord> fundingBlock;
                try {
                    fundingBlock = accountService.getFundingHistory(params);
                    if (fundingBlock.isEmpty()) {
                        break;
                    }
                } catch (Exception e) {
                    throw new IllegalStateException("User funding history download failed. ", e);
                }
                if (!result.isEmpty()) {
                    lastTxId = result.get(result.size() - 1).getInternalId();
                }
                var fundingToAdd = getFundingToAddAndUpdateState(lastTxId, fundingBlock, fundingState);
                result.addAll(fundingToAdd);
                if (fundingState.isEnd()) {
                    break;
                }
                ++sentRequests;
                state.put(FUNDING_STATE_KEY, fundingState);
            }
        }
        return result;
    }


    private List<UserTrade> getUserTradesToAddAndUpdateState(
        String lastTxId,
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

        if (userTradesBlock.size() == 1 && userTradesBlock.get(0).getOrderId().equals(downloadState.getLastTxIdAfterGap())) {
            downloadState.setEnd(true);
            return List.of();
        }

        final String lastTxIdToAdd = userTradesToAdd.get(userTradesToAdd.size() - 1).getOrderId();
        final String firstTxIdToAdd = userTradesToAdd.get(0).getOrderId();

        if (lastTxIdToAdd.equals(lastTxId)) {
            downloadState.setEnd(true);
            return List.of();
        } else {
            downloadState.setEnd(false);
        }

        if (downloadState.isFirstDownloadInWindow()) {
            downloadState.setLastTxIdAfterGap(lastTxIdToAdd);
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

    private List<FundingRecord> getFundingToAddAndUpdateState(String lastTxId ,List<FundingRecord> fundingBlock,
                                                              HuobiDownloadState downloadState) {
        //Sort ASC BY ID, because of xchange sorts by Date...is not unique
        fundingBlock.sort(comparing(FundingRecord::getInternalId));

        final boolean isLastTxInBlockDuplicate = !fundingBlock.isEmpty()
            && fundingBlock.get(fundingBlock.size() - 1).getInternalId().equals(downloadState.getFirstTxIdAfterGap());

        var fundingToAdd = isLastTxInBlockDuplicate ? fundingBlock.subList(0, fundingBlock.size() - 1) : fundingBlock;

        if (fundingBlock.size() == 1 && fundingBlock.get(0).getInternalId().equals(downloadState.getLastTxIdAfterGap())) {
            downloadState.setEnd(true);
            return List.of();
        }

        if (fundingToAdd.isEmpty()) {
            downloadState.closeGap();
            downloadState.moveToNextWindow();
            return List.of();
        }

        final String lastTxIdToAdd = fundingToAdd.get(fundingToAdd.size() - 1).getInternalId();
        final String firstTxIdToAdd = fundingToAdd.get(0).getInternalId();

        if (lastTxId.equals(lastTxIdToAdd)) {
           downloadState.setEnd(true);
           return List.of();
        } else {
            downloadState.setEnd(false);
        }

        if (downloadState.isFirstDownloadInWindow()) {
            downloadState.setLastTxIdAfterGap(lastTxIdToAdd);
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
