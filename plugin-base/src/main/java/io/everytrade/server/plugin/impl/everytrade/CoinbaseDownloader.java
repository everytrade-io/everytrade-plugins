package io.everytrade.server.plugin.impl.everytrade;

import io.everytrade.server.plugin.api.connector.DownloadResult;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.coinbase.v2.service.CoinbaseAccountService;
import org.knowm.xchange.coinbase.v2.service.CoinbaseTradeHistoryParams;
import org.knowm.xchange.coinbase.v2.service.CoinbaseTradeService;
import org.knowm.xchange.dto.account.FundingRecord;
import org.knowm.xchange.dto.trade.UserTrade;
import org.knowm.xchange.service.account.AccountService;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toMap;
import static lombok.AccessLevel.PRIVATE;
import static org.apache.commons.lang3.StringUtils.isEmpty;
import static org.knowm.xchange.bitfinex.service.BitfinexAdapters.log;

@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = PRIVATE)
public class CoinbaseDownloader {
    //https://developers.coinbase.com/api/v2#rate-limiting 10.000 / API-KEY / hour ---> 2500 / 15 min
    private static final int MAX_REQUEST_COUNT = 1250;
    private static final int MAX_REQUEST_COUNT_DEPOSIT_WITHDRAWALS = 1250;
    private static final int MAX_WALLET_REQUESTS = 50;
    private static final String DASH_SYMBOL = "-";
    private static final String COLON_SYMBOL = ":";
    private static final String PIPE_SYMBOL = "|";
    private static final int TRANSACTIONS_PER_REQUEST_LIMIT = 100;
    private static final int REAL_WALLET_ID_LENGTH = 36;

    @NonNull
    Exchange exchange;

    public DownloadResult download(String lastDownloadState) {
        Map<String, WalletState> walletStates = walletStates(lastDownloadState);
        var funding = downloadFunding(walletStates);
        var trades = downloadTrades(walletStates);

        DownloadResult build = DownloadResult.builder()
            .parseResult(new XChangeConnectorParser().getParseResult(trades, funding))
            .downloadStateData(getLastTransactionId(walletStates))
            .build();
        return build;
    }

    private List<UserTrade> downloadTrades(Map<String, WalletState> walletStates) {
        var sortedWalletStates = sortWalletsByTxsUpdates(walletStates);
        var wallets = sortedWalletStates.stream().
            collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (u, v) -> u, LinkedHashMap::new));
        final List<UserTrade> userTrades = new ArrayList<>();
        int sentRequests = 0;
        int walletRequests = 0;

        var tradeService = (CoinbaseTradeService) exchange.getTradeService();
        CoinbaseTradeHistoryParams params = (CoinbaseTradeHistoryParams) tradeService.createTradeHistoryParams();
        params.setLimit(TRANSACTIONS_PER_REQUEST_LIMIT);

        for (Map.Entry<String, WalletState> entry : wallets.entrySet()) {
            final String walletId = entry.getKey();
            final WalletState walletState = wallets.get(walletId);
            if (walletRequests < MAX_WALLET_REQUESTS) {
                String lastBuyId = entry.getValue().lastBuyId;
                String lastSellId = entry.getValue().lastSellId;

                while (sentRequests < MAX_REQUEST_COUNT) {
                    ++sentRequests;
                    params.setStartId(lastBuyId);
                    final List<UserTrade> buysTradeHistoryBlock;
                    try {
                        buysTradeHistoryBlock = tradeService.getBuyTradeHistory(params, walletId).getUserTrades();
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
                    params.setStartId(lastSellId);
                    final List<UserTrade> sellsTradeHistoryBlock;
                    try {
                        sellsTradeHistoryBlock = tradeService.getSellTradeHistory(params, walletId).getUserTrades();
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

                walletState.lastBuyId = lastBuyId;
                walletState.lastSellId = lastSellId;

                walletRequests++;
                walletState.lastTxWalletUpdate =  String.valueOf(new Date().getTime());
            }
        }
        return userTrades;
    }

    public List<FundingRecord> downloadFunding(Map<String, WalletState> walletStates) {
        var walletStatesList = sortWalletsByFundingUpdates(walletStates);
        var wallets = walletStatesList.stream().
            collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (u, v) -> u, LinkedHashMap::new));

        final List<FundingRecord> fundingRecords = new ArrayList<>();
        int sentRequests = 0;
        int walletRequests = 0;

        var accountService = (CoinbaseAccountService) exchange.getAccountService();
        CoinbaseTradeHistoryParams params = (CoinbaseTradeHistoryParams) accountService.createFundingHistoryParams();
        params.setLimit(TRANSACTIONS_PER_REQUEST_LIMIT);

        for (Map.Entry<String, WalletState> entry : wallets.entrySet()) {
            final String walletId = entry.getKey();
            final WalletState walletState = wallets.get(walletId);
            if(walletRequests < MAX_WALLET_REQUESTS) {
                String lastDepositId = entry.getValue().lastDepositId;
                String lastWithdrawalId = entry.getValue().lastWithdrawalId;

                while (sentRequests < MAX_REQUEST_COUNT_DEPOSIT_WITHDRAWALS) {
                    ++sentRequests;
                    params.setStartId(lastDepositId);
                    final List<FundingRecord> depositRecords;
                    try {
                        depositRecords = accountService.getDepositHistory(params, walletId);
                    } catch (IOException e) {
                        throw new IllegalStateException("Download deposit history failed.", e);
                    }

                    if (depositRecords.isEmpty()) {
                        break;
                    }

                    fundingRecords.addAll(depositRecords);
                    lastDepositId = depositRecords.get(depositRecords.size() - 1).getInternalId();
                }

                while (sentRequests < MAX_REQUEST_COUNT_DEPOSIT_WITHDRAWALS) {
                    ++sentRequests;
                    params.setStartId(lastWithdrawalId);
                    final List<FundingRecord> withdrawalRecords;
                    try {
                        withdrawalRecords = accountService.getWithdrawalHistory(params, walletId);
                    } catch (IOException e) {
                        throw new IllegalStateException("Download sells history failed.", e);
                    }

                    if (withdrawalRecords.isEmpty()) {
                        break;
                    }

                    fundingRecords.addAll(withdrawalRecords);
                    lastWithdrawalId = withdrawalRecords.get(withdrawalRecords.size() - 1).getInternalId();

                }
                if (sentRequests == MAX_REQUEST_COUNT) {
                    log.info("Max request count {} has been achieved.", MAX_REQUEST_COUNT);
                }

                walletState.lastDepositId = lastDepositId;
                walletState.lastWithdrawalId = lastWithdrawalId;

                walletRequests++;
                walletState.lastFundingWalletUpdate =  String.valueOf(new Date().getTime());
            }
        }
        return fundingRecords;
    }

    private String getLastTransactionId(Map<String, WalletState> walletStates) {
        return walletStates.entrySet().stream()
            .filter(entry -> entry.getValue().lastBuyId != null ||
                entry.getValue().lastSellId != null ||
                entry.getValue().lastDepositId != null ||
                entry.getValue().lastWithdrawalId != null ||
                entry.getValue().lastTxWalletUpdate != null ||
                entry.getValue().lastFundingWalletUpdate !=null
            )
            .map(
                entry -> entry.getKey()
                    + COLON_SYMBOL
                    + Objects.requireNonNullElse(entry.getValue().lastBuyId, DASH_SYMBOL)
                    + COLON_SYMBOL
                    + Objects.requireNonNullElse(entry.getValue().lastSellId, DASH_SYMBOL)
                    + COLON_SYMBOL
                    + Objects.requireNonNullElse(entry.getValue().lastDepositId, DASH_SYMBOL)
                    + COLON_SYMBOL
                    + Objects.requireNonNullElse(entry.getValue().lastWithdrawalId, DASH_SYMBOL)
                    + COLON_SYMBOL
                    + Objects.requireNonNullElse(entry.getValue().lastTxWalletUpdate, DASH_SYMBOL)
                    + COLON_SYMBOL
                    + Objects.requireNonNullElse(entry.getValue().lastFundingWalletUpdate, DASH_SYMBOL)
            )
            .collect(Collectors.joining(PIPE_SYMBOL));
    }

    private Map<String, WalletState> walletStates(String downloadState) {
        final Set<String> actualWalletIds = getWalletIds();

        Map<String, WalletState> previousWalletStates =
            isEmpty(downloadState) ? new HashMap<>() :
                Arrays.stream(downloadState.split("\\" + PIPE_SYMBOL))
                    .map(entry -> entry.split(COLON_SYMBOL))
                    .collect(toMap(
                        entry -> entry[0],
                        entry -> new WalletState(
                            getOrNull(entry, 1),
                            getOrNull(entry, 2),
                            getOrNull(entry, 3),
                            getOrNull(entry, 4),
                            getOrNull(entry, 5),
                            getOrNull(entry,6)
                        )
                    ));

        Map<String, WalletState> actualWalletStates = new HashMap<>();
        for (String actualWalletId : actualWalletIds) {
            final WalletState walletState = previousWalletStates.get(actualWalletId);
            actualWalletStates.put(
                actualWalletId,
                Objects.requireNonNullElseGet(walletState, WalletState::new)
            );
        }
        return actualWalletStates;
    }

    public static List<Map.Entry<String, WalletState>> sortWalletsByFundingUpdates(Map<String, WalletState>walletsMap) {
        List<Map.Entry<String, WalletState>> sortedWallets =
            walletsMap.entrySet().stream()
                .sorted(Map.Entry.comparingByValue(Comparator.comparingLong(c -> {
                    String lastFundingWalletUpdate = c.lastFundingWalletUpdate != null ? c.lastFundingWalletUpdate : String.valueOf(1L);
                    return Long.parseLong(lastFundingWalletUpdate);
                }))).collect(Collectors.toList());
        return sortedWallets;
    }

    public static List<Map.Entry<String, WalletState>> sortWalletsByTxsUpdates(Map<String, WalletState>walletsMap) {
        List<Map.Entry<String, WalletState>> sortedWallets =
            walletsMap.entrySet().stream()
                .sorted(Map.Entry.comparingByValue(Comparator.comparingLong(c -> {
                    String lastFundingWalletUpdate = c.lastTxWalletUpdate != null ? c.lastTxWalletUpdate : String.valueOf(1L);
                    return Long.parseLong(lastFundingWalletUpdate);
                }))).collect(Collectors.toList());
        return sortedWallets;
    }

    private Set<String> getWalletIds() {
        final AccountService accountService = exchange.getAccountService();
        try {
            return accountService.getAccountInfo().getWallets().keySet()
                .stream()
                .filter(s -> s.length() == REAL_WALLET_ID_LENGTH)
                .collect(Collectors.toSet());
        } catch (IOException e) {
            throw new IllegalStateException("Wallets download failed.", e);
        }
    }

    private String getOrNull(String[] arr, int index) {
        if (arr == null) {
            return null;
        }
        return arr.length > index ? arr[index] : null;
    }

    @NoArgsConstructor
    private static class WalletState {
        String lastBuyId;
        String lastSellId;
        String lastDepositId;
        String lastWithdrawalId;
        String lastTxWalletUpdate;
        String lastFundingWalletUpdate;

        public WalletState(String lastBuyId, String lastSellId, String lastDepositId, String lastWithdrawalId, String lastTxWalletUpdate,
         String lastFundingWalletUpdate) {
            this.lastBuyId = DASH_SYMBOL.equals(lastBuyId) ? null : lastBuyId;
            this.lastSellId = DASH_SYMBOL.equals(lastSellId) ? null : lastSellId;
            this.lastDepositId = DASH_SYMBOL.equals(lastDepositId) ? null : lastDepositId;
            this.lastWithdrawalId = DASH_SYMBOL.equals(lastWithdrawalId) ? null : lastWithdrawalId;
            this.lastTxWalletUpdate = DASH_SYMBOL.equals(lastTxWalletUpdate) ? null : lastTxWalletUpdate;
            this.lastFundingWalletUpdate = DASH_SYMBOL.equals(lastFundingWalletUpdate) ? null : lastFundingWalletUpdate;
        }
    }
}