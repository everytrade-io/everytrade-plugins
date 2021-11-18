package io.everytrade.server.plugin.impl.everytrade;

import io.everytrade.server.plugin.api.connector.DownloadResult;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.coinbase.v2.service.CoinbaseTradeHistoryParams;
import org.knowm.xchange.coinbase.v2.service.CoinbaseTradeService;
import org.knowm.xchange.dto.account.FundingRecord;
import org.knowm.xchange.dto.trade.UserTrade;
import org.knowm.xchange.service.account.AccountService;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toMap;
import static lombok.AccessLevel.PRIVATE;
import static org.knowm.xchange.bitfinex.service.BitfinexAdapters.log;

@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = PRIVATE)
public class CoinbaseDownloader {
    //https://developers.coinbase.com/api/v2#rate-limiting 10.000 / API-KEY / hour ---> 2500 / 15 min
    private static final int MAX_REQUEST_COUNT = 2500;
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

        return DownloadResult.builder()
            .parseResult(new XChangeConnectorParser().getParseResult(trades, funding))
            .downloadStateData(getLastTransactionId(walletStates))
            .build();
    }

    private List<UserTrade> downloadTrades(Map<String, WalletState> walletStates) {
        final List<UserTrade> userTrades = new ArrayList<>();
        int sentRequests = 0;

        var tradeService = (CoinbaseTradeService) exchange.getTradeService();
        CoinbaseTradeHistoryParams params = (CoinbaseTradeHistoryParams) tradeService.createTradeHistoryParams();
        params.setLimit(TRANSACTIONS_PER_REQUEST_LIMIT);

        for (Map.Entry<String, WalletState> entry : walletStates.entrySet()) {
            String lastBuyId = entry.getValue().lastBuyId;
            String lastSellId = entry.getValue().lastSellId;
            final String walletId = entry.getKey();

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

            final WalletState walletState = walletStates.get(walletId);
            walletState.lastBuyId = lastBuyId;
            walletState.lastSellId = lastSellId;

        }
        return userTrades;
    }

    public List<FundingRecord> downloadFunding(Map<String, WalletState> walletStates) {
        //var accountService = (CoinbaseAccountService) exchange.getAccountService();
        List<FundingRecord> fundingRecords = new ArrayList<>();
        // TODO funding
       /* for (Map.Entry<String, WalletState> entry : walletStates.entrySet()) {
            String lastDepositId = entry.getValue().lastDepositId;
            String lastWithdrawId = entry.getValue().lastWithdrawId;
            String walletId = entry.getKey();

            Map depositsBlock;
            try {
                depositsBlock = accountService.getDeposits(walletId);
            } catch (IOException e) {
                throw new IllegalStateException("Download deposit history failed.", e);
            }

            final Map withdrawalsBlock;
            try {
                withdrawalsBlock = accountService.getWithdrawals(walletId);
            } catch (IOException e) {
                throw new IllegalStateException("Download sells history failed.", e);
            }
            assert depositsBlock != null;
            assert withdrawalsBlock != null;
        }*/
        return fundingRecords;
    }

    private String getLastTransactionId(Map<String, WalletState> walletStates) {
        return walletStates.entrySet().stream()
            .filter(entry -> entry.getValue().lastBuyId != null || entry.getValue().lastSellId != null)
            .map(
                entry -> entry.getKey()
                    + COLON_SYMBOL
                    + Objects.requireNonNullElse(entry.getValue().lastBuyId, DASH_SYMBOL)
                    + COLON_SYMBOL
                    + Objects.requireNonNullElse(entry.getValue().lastSellId, DASH_SYMBOL)
            )
            .collect(Collectors.joining(PIPE_SYMBOL));
    }

    private Map<String, WalletState> walletStates(String downloadState) {
        final Set<String> actualWalletIds = getWalletIds();

        Map<String, WalletState> previousWalletStates =
            downloadState == null ? new HashMap<>() :
                Arrays.stream(downloadState.split("\\" + PIPE_SYMBOL))
                    .map(entry -> entry.split(COLON_SYMBOL))
                    .collect(toMap(
                        entry -> entry[0],
                        entry -> new WalletState(
                            getOrNull(entry, 1),
                            getOrNull(entry, 2),
                            getOrNull(entry, 3),
                            getOrNull(entry, 4)
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
        String lastWithdrawId;

        public WalletState(String lastBuyId, String lastSellId, String lastDepositId, String lastWithdrawId) {
            this.lastBuyId = DASH_SYMBOL.equals(lastBuyId) ? null : lastBuyId;
            this.lastSellId = DASH_SYMBOL.equals(lastSellId) ? null : lastSellId;
            this.lastDepositId = DASH_SYMBOL.equals(lastBuyId) ? null : lastDepositId;
            this.lastWithdrawId = DASH_SYMBOL.equals(lastSellId) ? null : lastWithdrawId;
        }
    }
}