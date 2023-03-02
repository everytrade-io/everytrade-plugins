package io.everytrade.server.plugin.impl.everytrade;

import io.everytrade.server.plugin.api.connector.DownloadResult;
import io.everytrade.server.plugin.api.parser.ParsingProblem;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.coinbase.v2.service.CoinbaseAccountService;
import org.knowm.xchange.coinbase.v2.service.CoinbaseTradeHistoryParams;
import org.knowm.xchange.coinbase.v2.service.CoinbaseTradeService;
import org.knowm.xchange.coinbase.v3.dto.transactions.CoinbaseAdvancedTradeFills;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.account.FundingRecord;
import org.knowm.xchange.dto.trade.UserTrade;
import org.knowm.xchange.service.account.AccountService;
import org.knowm.xchange.service.trade.TradeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
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
import java.util.TimeZone;
import java.util.stream.Collectors;

import static io.everytrade.server.plugin.api.parser.ParsingProblemType.ROW_PARSING_FAILED;
import static java.util.stream.Collectors.toMap;
import static org.apache.commons.lang3.StringUtils.isEmpty;

public class CoinbaseDownloader {
    //https://developers.coinbase.com/api/v2#rate-limiting 10.000 / API-KEY / hour ---> 2500 / 15 min
    private static final int MAX_REQUEST_COUNT = 1250;
    private static final int MAX_REQUEST_COUNT_DEPOSIT_WITHDRAWALS = 1250;
    private static final int MAX_WALLET_REQUESTS = 50;
    private static final String DASH_SYMBOL = "-";
    private static final String COLON_SYMBOL = ":";
    private static final String PIPE_SYMBOL = "|";
    private static final String ADVANCED_TRADE_SYMBOL_SEPARATOR = "&";
    private static final int TRANSACTIONS_PER_REQUEST_LIMIT = 100; // please do not change, it could affect last download state logic
    private static final int REAL_WALLET_ID_LENGTH = 36;
    private String lastDownloadWalletState;
    private long lastAdvanceTradeStartDatetime;
    private long lastAdvanceTradeEndDatetime;
    private static final Logger log = LoggerFactory.getLogger(CoinbaseDownloader.class);

    @NonNull
    Exchange exchange;

    public  CoinbaseDownloader(Exchange exchange) {
        this.exchange = exchange;
    }

    private void getLastDownloadStates(String lastDownloadState) {
        if (lastDownloadState != null) {
            String[] split = lastDownloadState.split(ADVANCED_TRADE_SYMBOL_SEPARATOR);
            if (split.length > 1) {
                String[] advancedTrades = split[1].split(COLON_SYMBOL);
                lastAdvanceTradeStartDatetime = Long.parseLong(advancedTrades[0]);
                lastAdvanceTradeEndDatetime = Long.parseLong(advancedTrades[1]);
                lastDownloadWalletState = split[0];
            } else if (split.length == 1) {
                lastDownloadWalletState = split[0];
            }
        }
    }

    public DownloadResult download(String lastDownloadState) {
        getLastDownloadStates(lastDownloadState);
        Map<String, WalletState> walletStates = walletStates(lastDownloadWalletState);
        List<FundingRecord> funding = new ArrayList<>();
        List<UserTrade> trades = new ArrayList<>();
        List<UserTrade> advancedTrading = new ArrayList<>();

        try{
            log.info("Advanced trading download start");
            advancedTrading = downloadAdvancedTrade();
        } catch (Exception e) {
            log.error("Advanced trading download error " + e.getMessage());
        }

        try{
            log.info("Trades download start");
            trades = downloadTrades(walletStates);
        } catch (Exception e) {
            log.error("Trades download error " + e.getMessage());
        }

        try{
            log.info("Funding download start");
            funding = downloadFunding(walletStates);
        } catch (Exception e) {
            log.error("Funding download error " + e.getMessage());
        }
        trades.addAll(advancedTrading);

        DownloadResult build = DownloadResult.builder()
            .parseResult(new XChangeConnectorParser().getParseResult(trades, funding))
            .downloadStateData(getLastTransactionId(walletStates))
            .build();
        return build;
    }


    private CoinbaseTradeHistoryParams setParamsBeforeStart(TradeService tradeService) {
        var params = (CoinbaseTradeHistoryParams) tradeService.createTradeHistoryParams();
        // 1. state - brand new download
        if(lastAdvanceTradeEndDatetime == 0 && lastAdvanceTradeStartDatetime == 0) {
            params.setEndDateTime(Instant.now());
            params.setStartDatetime(Instant.ofEpochMilli(0));
            // 2. state - not finished 1st download
        } else if (lastAdvanceTradeEndDatetime > 0 && lastAdvanceTradeStartDatetime == 0) {
            params.setEndDateTime(Instant.ofEpochMilli(lastAdvanceTradeEndDatetime));
            params.setStartDatetime(Instant.ofEpochMilli(0));
            // 3. finished first download and setup for second one - used as well as for start another downloads
        } else if (lastAdvanceTradeEndDatetime == 0 && lastAdvanceTradeStartDatetime > 0) {
            params.setEndDateTime(Instant.now());
            params.setStartDatetime(Instant.ofEpochMilli(lastAdvanceTradeStartDatetime));
            // 4. not finished 2nd download
        } else if (lastAdvanceTradeEndDatetime > 0 && lastAdvanceTradeStartDatetime > 0) {
            params.setEndDateTime(Instant.ofEpochMilli(lastAdvanceTradeEndDatetime));
            params.setStartDatetime(Instant.ofEpochMilli(lastAdvanceTradeStartDatetime));
        } else {
            throw new IllegalStateException("Unknown last download state data");
        }
        params.setLimit(TRANSACTIONS_PER_REQUEST_LIMIT);
        return params;
    }

    private List<CoinbaseAdvancedTradeFills> removeLastTxs(List<CoinbaseAdvancedTradeFills> block) {
        long lastTxTime;
        try {
            String tradeTime = block.get(block.size() - 1).getTradeTime();
            lastTxTime = createDateFromText(tradeTime).getTime();
            lastTxTime = lastTxTime + 1000L;
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }
        long finalLastTxTime = lastTxTime;
        var advancedTradesPureBlock =
            block.stream().filter(tx -> {
                try {
                    String tradeTime = tx.getTradeTime();
                    long time = createDateFromText(tradeTime).getTime();
                    return time > finalLastTxTime;
                } catch (ParseException e) {
                    throw new RuntimeException(e);
                }
            }).collect(Collectors.toList());
        return advancedTradesPureBlock;
    }

    /**
     * 2023-01-02T00:42:55.504087795Z
     * @param textDate
     * @return
     */
    String cleanDateText(String textDate) {
        try {
            textDate = textDate.substring(0,textDate.lastIndexOf(".") + 4);
            textDate = textDate.replace("Z", "");
            textDate += "Z";
        } catch (Exception ignore) {

        }
        return textDate;
    }

    private List<UserTrade> downloadAdvancedTrade() {
        var tradeService = (CoinbaseTradeService) exchange.getTradeService();
        int sentRequests = 0;
        List<CoinbaseAdvancedTradeFills> advancedTrades = new ArrayList<>();
        while (sentRequests < MAX_REQUEST_COUNT) {
            var params = setParamsBeforeStart(tradeService);

            List<CoinbaseAdvancedTradeFills> advancedTradesBlock = new ArrayList<>();
            try {
                advancedTradesBlock = tradeService.getAdvancedTradeOrderFills(params);
            } catch (Exception e) {
                throw new IllegalStateException("Unable to download advanced trades. ", e);
            }
            int size = advancedTradesBlock.size();
            if (size == 0) {
                lastAdvanceTradeEndDatetime = 0;
                lastAdvanceTradeStartDatetime = lastAdvanceTradeEndDatetime + 1;
                break;
            } else if (size == TRANSACTIONS_PER_REQUEST_LIMIT) {
                // remove last txs with the same timestamp
                advancedTradesBlock = removeLastTxs(advancedTradesBlock);
                Date minTradeDate;
                try {
                    String tradeTime = advancedTradesBlock.get(advancedTradesBlock.size() - 1).getTradeTime();
                    minTradeDate = createDateFromText(tradeTime);
                } catch (ParseException e) {
                    throw new RuntimeException("Unable to parse date of trade ", e);
                }
                lastAdvanceTradeEndDatetime = minTradeDate.getTime();
                advancedTrades.addAll(advancedTradesBlock);
            } else if (size < TRANSACTIONS_PER_REQUEST_LIMIT && size > 0) {
                advancedTrades.addAll(advancedTradesBlock);
                String tradeTime = advancedTrades.get(0).getTradeTime();
                long endDate;
                try {
                    endDate = createDateFromText(tradeTime).getTime();
                } catch (ParseException e) {
                    throw new RuntimeException(e);
                }
                lastAdvanceTradeEndDatetime = 0;
                lastAdvanceTradeStartDatetime = endDate + 1;
                break;
            } else {
                throw new IllegalStateException("Unknown state of downloaded data. ");
            }
            sentRequests++;
        }
        List<UserTrade> userTrades = createUserTradesFromAdvancedTrades(advancedTrades);
        return userTrades;
    }

    private List<UserTrade> createUserTradesFromAdvancedTrades(List<CoinbaseAdvancedTradeFills> fills) {
        List<UserTrade> trades = new ArrayList<>();
        var parsingProblems = new ArrayList<ParsingProblem>();
        for(CoinbaseAdvancedTradeFills fill : fills) {
            try {
                String[] currencies = fill.getProductId().split("-");
                var base = new Currency(currencies[0]);
                var quote = new Currency(currencies[1]);
                var pair = new CurrencyPair(base, quote);
                Date date = createDateFromText(fill.getTradeTime());
                Order.OrderType type = fill.getSide().equalsIgnoreCase("BUY") ? Order.OrderType.BID : Order.OrderType.ASK;
                var trade = new UserTrade(type, fill.getSize(), pair, fill.getPrice(), date, fill.getTradeId(),
                    fill.getOrderId(), fill.getCommission(), pair.getCounter(), fill.getUserId());
                trades.add(trade);
            } catch (Exception e) {
                parsingProblems.add(new ParsingProblem(fill.toString(), e.getMessage(), ROW_PARSING_FAILED));
            }
        }
        int size = parsingProblems.size();
        if(size > 0 ) {
            log.error("Several ( %s ) fills could not be processed", size);
        }
        return trades;
    }

    Date createDateFromText(String textTime) throws ParseException {
        var datePattern = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
        datePattern.setTimeZone(TimeZone.getTimeZone("UTC"));
        textTime = cleanDateText(textTime);
        return datePattern.parse(textTime);
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
        String walletsState = walletStates.entrySet().stream()
            .filter(entry -> entry.getValue().lastBuyId != null ||
                entry.getValue().lastSellId != null ||
                entry.getValue().lastDepositId != null ||
                entry.getValue().lastWithdrawalId != null ||
                entry.getValue().lastTxWalletUpdate != null ||
                entry.getValue().lastFundingWalletUpdate != null
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
        return walletsState + ADVANCED_TRADE_SYMBOL_SEPARATOR + advancedTradeLastDownloadTimestamp();
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

    private String advancedTradeLastDownloadTimestamp() {
        return String.valueOf(lastAdvanceTradeStartDatetime) + COLON_SYMBOL + String.valueOf(lastAdvanceTradeEndDatetime);
    }
}