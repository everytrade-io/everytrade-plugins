package io.everytrade.server.plugin.impl.everytrade;

import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.trade.UserTrade;
import org.knowm.xchange.huobi.service.HuobiTradeHistoryParams;
import org.knowm.xchange.service.trade.TradeService;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class HuobiDownloader {
    // huobiapi.github.io/docs/spot/v1/en/#overview-2 --> 10 requests per API_KEY per second
    // 200 ms = 50% API_KEY capacity
    private static final Duration SLEEP_BETWEEN_REQUESTS = Duration.ofMillis(200);
    // txs count in request = 100, 20.000 txs per cycle --> 200 requests
    private static final int MAX_REQUEST_COUNT = 200;
    public static final int DAYS_AGO = 179;
    private final Map<String, String> currencyPairLastIds;
    private final TradeService tradeService;
    private final HuobiTradeHistoryParams tradeHistoryParams;

    public HuobiDownloader(TradeService tradeService, String lastTransactionId) {
        Objects.requireNonNull(this.tradeService = tradeService);
        tradeHistoryParams = (HuobiTradeHistoryParams) tradeService.createTradeHistoryParams();
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
            final String lastDownloadedTx = currencyPairLastIds.get(pair.toString());
            String lastDownloadedTxId = getId(lastDownloadedTx);
            LocalDate lastDownloadedTxDate = getDate(lastDownloadedTx);
            tradeHistoryParams.setStartId(lastDownloadedTxId);
            tradeHistoryParams.setStartTime(convert(lastDownloadedTxDate));
            tradeHistoryParams.setEndTime(convert(lastDownloadedTxDate.plus(1, ChronoUnit.DAYS)));

            while (sentRequests < MAX_REQUEST_COUNT) {
                try {
                    Thread.sleep(SLEEP_BETWEEN_REQUESTS.toMillis());
                } catch (InterruptedException e) {
                    throw new IllegalStateException("User trade history download sleep interrupted.", e);
                }
                final List<UserTrade> userTradesBlock;
                try {
                    userTradesBlock = tradeService.getTradeHistory(tradeHistoryParams).getUserTrades();
                } catch (Exception e) {
                    throw new IllegalStateException("User trade history download failed. ", e);
                }
                if (
                    lastDownloadedTx != null
                        && !userTradesBlock.isEmpty()
                        && userTradesBlock.get(0).getId().equals(lastDownloadedTxId)
                ) {
                    userTradesBlock.remove(0);
                }
                if (userTradesBlock.isEmpty()) {
                    if (lastDownloadedTxDate.equals(LocalDate.now())) {
                        break;
                    } else {
                        lastDownloadedTxDate = lastDownloadedTxDate.plus(1, ChronoUnit.DAYS);
                    }

                } else {
                    userTrades.addAll(userTradesBlock);
                    lastDownloadedTxId = userTradesBlock.get(userTradesBlock.size() - 1).getId();
                    tradeHistoryParams.setStartId(lastDownloadedTxId);
                    ++sentRequests;
                }

            }
            currencyPairLastIds.put(
                pair.toString(),
                String.format(
                    "%s_%s",
                    lastDownloadedTxDate,
                    lastDownloadedTxId == null ? "" : lastDownloadedTxId
                )
            );
        }

        return userTrades;
    }

    public String getLastTransactionId() {
        return currencyPairLastIds.keySet().stream()
            .map(key -> key + "=" + currencyPairLastIds.get(key))
            .collect(Collectors.joining(":"));
    }

    private LocalDate getDate(String lastDowmloadedTxId) {
        if (lastDowmloadedTxId == null) {
            return LocalDate.now().minus(DAYS_AGO, ChronoUnit.DAYS);

        }
        return LocalDate.parse(lastDowmloadedTxId.split("_")[0]);
    }

    private String getId(String lastDowmloadedTxId) {
        if (lastDowmloadedTxId == null) {
            return null;
        }
        final String[] split = lastDowmloadedTxId.split("_");
        return split.length < 2 ? null : split[1];
    }

    public Date convert(LocalDate dateToConvert) {
        return java.util.Date.from(dateToConvert.atStartOfDay()
            .atZone(ZoneId.systemDefault())
            .toInstant());
    }
}
