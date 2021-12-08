package io.everytrade.server.plugin.impl.everytrade;

import com.okcoin.commons.okex.open.api.bean.spot.result.OrderInfo;
import com.okcoin.commons.okex.open.api.service.spot.SpotOrderAPIServive;
import org.knowm.xchange.currency.CurrencyPair;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class OkexDownloader {
    //https://www.okex.com/docs/en/#spot-account_information - limit 20 requests per second
    private static final int MAX_REQUESTS = 30;
    private static final Duration SLEEP_BETWEEN_REQUESTS = Duration.ofMillis(100);
    private static final String MAX_TXS_PER_REQUEST = "100";
    public static final String STATUS_FULLY_FILLED = "2";
    private final SpotOrderAPIServive spotOrderApiService;
    private final OkexCurrencyPairDonwloadState donwloadState;

    public OkexDownloader(SpotOrderAPIServive spotOrderApiService, String lastTransactionId) {
        Objects.requireNonNull(this.spotOrderApiService = spotOrderApiService);
        donwloadState = new OkexCurrencyPairDonwloadState(lastTransactionId);
    }

    public List<OrderInfo> download(String currencyPairs) {
        final List<CurrencyPair> pairs = ConnectorUtils.toCurrencyPairs(currencyPairs);
        final List<OrderInfo> orders = new ArrayList<>();
        int sentRequests = 0;
        for (CurrencyPair pair : pairs) {
            final List<OrderInfo> pairOrders = new ArrayList<>();
            String pairCode = String.format("%s-%s", pair.base, pair.counter);
            final String continuousBlockLastTxId = donwloadState.getContinuousBlockLastTxId(pairCode);
            String currentAfterGapFirstTxId = donwloadState.getAfterGapFirstTxId(pairCode);
            boolean isGapClosed = false;

            while (sentRequests < MAX_REQUESTS) {
                final List<OrderInfo> ordersBlock;
                try {
                    ordersBlock = spotOrderApiService.getOrders(
                        pairCode,
                        STATUS_FULLY_FILLED,
                        currentAfterGapFirstTxId,
                        currentAfterGapFirstTxId == null ? continuousBlockLastTxId : null,
                        MAX_TXS_PER_REQUEST
                    );
                    Thread.sleep(SLEEP_BETWEEN_REQUESTS.toMillis());
                } catch (Exception e) {
                    throw new IllegalStateException("User trade history download failed. ", e);
                }
                final List<OrderInfo> ordersToAdd = getOrdersToAdd(ordersBlock, continuousBlockLastTxId);
                isGapClosed = ordersBlock.isEmpty() || ordersToAdd.size() < ordersBlock.size();
                if (ordersToAdd.isEmpty()) {
                    break;
                }

                pairOrders.addAll(ordersToAdd);
                currentAfterGapFirstTxId = ordersToAdd.get(ordersToAdd.size() - 1).getOrder_id();
                ++sentRequests;
            }

            donwloadState.update(
                pairCode,
                isGapClosed,
                pairOrders
            );
            orders.addAll(pairOrders);
        }
        return orders;
    }

    public String getLastTransactionId() {
        return donwloadState.toLastTransactionId();
    }

    private List<OrderInfo> getOrdersToAdd(List<OrderInfo> ordersBlock, String continuousBlockLastTxId) {
        final int duplicate = findDuplicate(continuousBlockLastTxId, ordersBlock);
        final List<OrderInfo> ordersToAdd = new ArrayList<>(ordersBlock);
        if (duplicate > -1) {
            ordersToAdd.removeAll(ordersToAdd.subList(0, duplicate + 1));
        }
        return ordersToAdd;
    }

    private int findDuplicate(String transactionId, List<OrderInfo> ordersBlock) {
        for (int i = 0; i < ordersBlock.size(); i++) {
            final OrderInfo orderInfo = ordersBlock.get(i);
            if (orderInfo.getOrder_id().equals(transactionId)) {
                return i;
            }
        }
        return -1;
    }
}