package io.everytrade.server.plugin.impl.everytrade;

import io.everytrade.server.plugin.api.parser.ParseResult;
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

    public ParseResult download(String currencyPairs) {
        var funding = downloadFunding(currencyPairs);
        var trades = downloadTrades(currencyPairs);

        return new XChangeConnectorParser().getParseResult(trades, funding);
    }

    public List<UserTrade> downloadTrades(String currencyPairs) {
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
        final List<FundingRecord> records = new ArrayList<>();
        var accountService = exchange.getAccountService();
        var params = (CoinbaseProTradeHistoryParams) accountService.createFundingHistoryParams();

        //DEPOSITS
        params.setLimit(TX_PER_REQUEST);
        params.setType(FundingRecord.Type.DEPOSIT);
        final List<FundingRecord> depositRecords;

        try {
            Thread.sleep(SLEEP_BETWEEN_REQUESTS_MS);
        } catch (InterruptedException e) {
            throw new IllegalStateException("Funding deposit record history download sleep interrupted.", e);
        }
        try {
            depositRecords = accountService.getFundingHistory(params);
        } catch (Exception e) {
            throw new IllegalStateException("Funding deposit record history download failed. ", e);
        }
        if (!depositRecords.isEmpty()) {
            records.addAll(depositRecords);
        }

        //WITHDRAWALS
        params.setLimit(TX_PER_REQUEST);
        params.setType(FundingRecord.Type.WITHDRAWAL);
        final List<FundingRecord> withdrawalRecords;
        try {
            Thread.sleep(SLEEP_BETWEEN_REQUESTS_MS);
        } catch (InterruptedException e) {
            throw new IllegalStateException("Funding withdrawal record history download sleep interrupted.", e);
        }
        try {
            withdrawalRecords = accountService.getFundingHistory(params);
        } catch (Exception e) {
            throw new IllegalStateException("Funding withdrawal record history download failed. ", e);
        }
        if (!withdrawalRecords.isEmpty()) {
            records.addAll(withdrawalRecords);
        }
        return records;
    }

    public String getLastTransactionId() {
        return currencyPairLastIds.keySet().stream()
            .map(key -> key + "=" + currencyPairLastIds.get(key))
            .collect(Collectors.joining(":"));
    }
}