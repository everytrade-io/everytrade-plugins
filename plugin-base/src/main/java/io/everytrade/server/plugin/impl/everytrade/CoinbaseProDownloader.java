package io.everytrade.server.plugin.impl.everytrade;

import io.everytrade.server.plugin.api.connector.DownloadResult;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.coinbasepro.dto.account.CoinbaseProTransfersWithHeader;
import org.knowm.xchange.coinbasepro.dto.trade.CoinbaseProTradeHistoryParams;
import org.knowm.xchange.coinbasepro.service.CoinbaseProAccountService;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.account.FundingRecord;
import org.knowm.xchange.dto.trade.UserTrade;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static lombok.AccessLevel.PRIVATE;
import static org.apache.commons.lang3.StringUtils.isEmpty;

@FieldDefaults(makeFinal = false, level = PRIVATE)
public class CoinbaseProDownloader {
    //https://docs.pro.coinbase.com/#rate-limits - max 5 request per user per second --> 200 ms between requests
    private static final int TX_PER_REQUEST = 100;
    private static final int MAX_REQUEST_COUNT = 3000;
    private static final int SLEEP_BETWEEN_REQUESTS_MS = 200;
    public static final int FIRST_COINBASE_TX_ID = 1;
    public static final String FIRST_DEPOSIT_ID = "";
    public static final String FIRST_WITHDRAWAL_ID = "";
    private DownloadState state;

    Exchange exchange;

    public CoinbaseProDownloader(Exchange exchange, String downloadState) {
        this.exchange = exchange;
        this.state = DownloadState.deserialize(downloadState);
    }

    public DownloadResult download(String currencyPairs) {
        var funding = downloadFunding();
        var trades = downloadTrades(currencyPairs);

        String serialize = state.serialize();
        return new DownloadResult(new XChangeConnectorParser().getParseResult(trades, funding), serialize);
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
            String key = pair.toString();
            final Integer lastDownloadedTxFound = state.currencyPairLastIds.get(key);
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
            state.currencyPairLastIds.put(pair.toString(), lastDownloadedTx);
        }
        return userTrades;
    }

    public List<FundingRecord> downloadFunding() {
        final List<FundingRecord> records = new ArrayList<>();
        var accountService = (CoinbaseProAccountService) exchange.getAccountService();
        var params = (CoinbaseProTradeHistoryParams) accountService.createFundingHistoryParams();

        //DEPOSITS
        final String lastDepositId = state.getLastDepositId();
        String lastDownloadedDepositId = lastDepositId == null ? FIRST_DEPOSIT_ID : lastDepositId;
        params.setLimit(TX_PER_REQUEST);
        params.setType(FundingRecord.Type.DEPOSIT);
        params.setBeforeTransferId(lastDownloadedDepositId);

        final CoinbaseProTransfersWithHeader depositRecords;

        try {
            Thread.sleep(SLEEP_BETWEEN_REQUESTS_MS);
        } catch (InterruptedException e) {
            throw new IllegalStateException("Funding deposit record history download sleep interrupted.", e);
        }
        try {
            depositRecords = accountService.getTransfersWithPagination(params);
        } catch (Exception e) {
            throw new IllegalStateException("Funding deposit record history download failed. ", e);
        }
        if (!depositRecords.getFundingRecords().isEmpty()) {
            List<FundingRecord> fundingDepositRecords = depositRecords.getFundingRecords();
            records.addAll(fundingDepositRecords);
            lastDownloadedDepositId = depositRecords.getCbBefore();
            state.setLastDepositId(lastDownloadedDepositId);
        }

        //WITHDRAWALS
        final String lastWithdrawal = state.getLastWithdrawalId();
        String lastDownloadedWithdrawalId = lastWithdrawal == null ? FIRST_WITHDRAWAL_ID : lastWithdrawal;
        params.setLimit(TX_PER_REQUEST);
        params.setType(FundingRecord.Type.WITHDRAWAL);
        params.setBeforeTransferId(lastDownloadedWithdrawalId);

        final CoinbaseProTransfersWithHeader withdrawalRecords;
        try {
            Thread.sleep(SLEEP_BETWEEN_REQUESTS_MS);
        } catch (InterruptedException e) {
            throw new IllegalStateException("Funding withdrawal record history download sleep interrupted.", e);
        }
        try {
            withdrawalRecords = accountService.getTransfersWithPagination(params);
        } catch (Exception e) {
            throw new IllegalStateException("Funding withdrawal record history download failed. ", e);
        }
        if (!withdrawalRecords.getFundingRecords().isEmpty()) {
            List<FundingRecord> fundingRecords = withdrawalRecords.getFundingRecords();
            records.addAll(fundingRecords);
            lastDownloadedWithdrawalId = withdrawalRecords.getCbBefore();
            state.setLastWithdrawalId(lastDownloadedWithdrawalId);
        }
        return records;
    }

    public String getLastTransactionId() {
        return state.serialize();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @FieldDefaults(level = PRIVATE)
    private static class DownloadState {
        private static final String SEPARATOR_FOR_SPLIT = "\\|";
        private static final String SEPARATOR = "|";

        Map<String, Integer> currencyPairLastIds = new HashMap<>();
        String lastDepositId;
        String lastWithdrawalId;

        public static DownloadState deserialize(String state) {
            if (isEmpty(state)) {
                return new DownloadState();
            }
            var strA = state.split(SEPARATOR_FOR_SPLIT);

            var tradeIds = Arrays.stream(strA[0].split(":"))
                .map(entry -> entry.split("="))
                .collect(Collectors.toMap(entry -> entry[0], entry -> Integer.parseInt(entry[1])));

            return new DownloadState(
                tradeIds,
                strA.length > 1 ? strA[1] : null,
                strA.length > 2 ? strA[2] : null
            );
        }

        public String serialize() {
            return currencyPairLastIds.keySet().stream()
                .map(key -> key + "=" + currencyPairLastIds.get(key))
                .collect(Collectors.joining(":"))
                + SEPARATOR + (lastDepositId != null ? lastDepositId : "")
                + SEPARATOR + (lastWithdrawalId != null ? lastWithdrawalId : "");
        }
    }
}