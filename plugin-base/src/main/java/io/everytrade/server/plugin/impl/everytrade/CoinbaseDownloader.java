package io.everytrade.server.plugin.impl.everytrade;

import com.univocity.parsers.common.DataValidationException;
import io.everytrade.server.plugin.api.connector.DownloadResult;
import io.everytrade.server.plugin.api.parser.ParsingProblem;
import io.everytrade.server.util.AmountUtil;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.coinbase.v2.dto.account.CoinbaseExpandTransactionsResponse;
import org.knowm.xchange.coinbase.v2.dto.account.transactions.CoinbaseShowTransactionV2;
import org.knowm.xchange.coinbase.v2.service.CoinbaseAccountService;
import org.knowm.xchange.coinbase.v2.service.CoinbaseAccountServiceRaw;
import org.knowm.xchange.coinbase.v2.service.CoinbaseTradeHistoryParams;
import org.knowm.xchange.coinbase.v2.service.CoinbaseTradeService;
import org.knowm.xchange.coinbase.v3.dto.transactions.CoinbaseAdvancedTradeFills;
import org.knowm.xchange.coinbase.v3.dto.transactions.CoinbaseAdvancedTradeOrderFillsResponse;
import org.knowm.xchange.coinbase.cdp.service.CoinbaseAccountServiceCDP;
import org.knowm.xchange.coinbase.cdp.service.CoinbaseAccountServiceRawCDP;
import org.knowm.xchange.coinbase.cdp.service.CoinbaseTradeServiceCDP;
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
import java.math.BigDecimal;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
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
    private static final String DASH_SYMBOL = "-";
    private static final String COLON_SYMBOL = ":";
    private static final String PIPE_SYMBOL = "|";
    private static final String ADVANCED_TRADE_SYMBOL_SEPARATOR = "&";
    private static final int TRANSACTIONS_PER_REQUEST_LIMIT = 100; // please do not change, it could affect last download state logic
    private static final int REAL_WALLET_ID_LENGTH = 36;
    private String lastDownloadWalletState;
    private long partialLastAdvanceTradeStartDatetime;
    private long partialLastAdvanceTradeEndDatetime;
    private long completedLastAdvanceTradeEndDatetime;
    private String cursorAdvanceTrade;
    private static final Logger LOG = LoggerFactory.getLogger(CoinbaseDownloader.class);

    @NonNull
    Exchange exchange;

    public CoinbaseDownloader(Exchange exchange) {
        this.exchange = exchange;
    }

    private void getLastDownloadStates(String lastDownloadState) {
        if (lastDownloadState != null) {
            String[] split = lastDownloadState.split(ADVANCED_TRADE_SYMBOL_SEPARATOR);
            if (split.length > 1) {
                String[] advancedTrades = split[1].split(COLON_SYMBOL);
                partialLastAdvanceTradeStartDatetime = Long.parseLong(advancedTrades[0]);
                partialLastAdvanceTradeEndDatetime = Long.parseLong(advancedTrades[1]);
                if (advancedTrades.length > 2) {
                    completedLastAdvanceTradeEndDatetime = Long.parseLong(advancedTrades[2]);
                }
                if (advancedTrades.length > 3) {
                    cursorAdvanceTrade = advancedTrades[3];
                }
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
        List<CoinbaseShowTransactionV2> trades = new ArrayList<>();
        List<UserTrade> advancedTrading = new ArrayList<>();
        List<ParsingProblem> parsingProblems = new ArrayList<>();

//      Advance Trades are not supported by the current version of the plugin - needs its own connector
        try {
            LOG.info("Advanced trading download start");
            advancedTrading = downloadAdvancedTrade(parsingProblems);
        } catch (Exception e) {
            LOG.error("Advanced trading download error " + e.getMessage());
        }

        try {
            LOG.info("Trades download start");
            trades = downloadTrades(walletStates);
        } catch (Exception e) {
            LOG.error("Trades download error " + e.getMessage());
        }

        try {
            LOG.info("Funding download start");
//            funding = downloadFunding(walletStates); TODO: uncomment when coinbase fix their pagination
        } catch (Exception e) {
            LOG.error("Funding download error " + e.getMessage());
        }

        DownloadResult build = DownloadResult.builder()
            .parseResult(new XChangeConnectorParser().getCoinbaseParseResult(advancedTrading, trades,funding, parsingProblems))
            .downloadStateData(getLastTransactionId(walletStates))
            .build();
        return build;
    }

    private CoinbaseTradeHistoryParams setParamsBeforeStart(TradeService tradeService, Instant now) {
        var params = (CoinbaseTradeHistoryParams) tradeService.createTradeHistoryParams();
        // 1. state - brand new download
        if (partialLastAdvanceTradeEndDatetime == 0 && partialLastAdvanceTradeStartDatetime == 0) {
            params.setEndDateTime(now);
            partialLastAdvanceTradeEndDatetime = completedLastAdvanceTradeEndDatetime;
            params.setStartDatetime(Instant.ofEpochMilli(0));
            // 2. state - not finished 1st download
        } else if (partialLastAdvanceTradeEndDatetime > 0 && partialLastAdvanceTradeStartDatetime == 0) {
            params.setEndDateTime(Instant.ofEpochMilli(partialLastAdvanceTradeEndDatetime));
            params.setStartDatetime(Instant.ofEpochMilli(0));
            // 3. finished first download and setup for second one - used as well as for start another downloads
        } else if (partialLastAdvanceTradeEndDatetime == 0 && partialLastAdvanceTradeStartDatetime > 0) {
            params.setEndDateTime(now);
            partialLastAdvanceTradeEndDatetime = now.toEpochMilli();
            params.setStartDatetime(Instant.ofEpochMilli(partialLastAdvanceTradeStartDatetime));
            // 4. not finished 2nd download
        } else if (partialLastAdvanceTradeEndDatetime > 0 && partialLastAdvanceTradeStartDatetime > 0) {
            params.setEndDateTime(Instant.ofEpochMilli(partialLastAdvanceTradeEndDatetime));
            params.setStartDatetime(Instant.ofEpochMilli(partialLastAdvanceTradeStartDatetime));
        } else {
            throw new IllegalStateException("Unknown last download state data");
        }
        params.setLimit(TRANSACTIONS_PER_REQUEST_LIMIT);
        if (cursorAdvanceTrade != null && !cursorAdvanceTrade.equalsIgnoreCase("null")) {
            params.setCursor(cursorAdvanceTrade);
        }
        return params;
    }

    /**
     * 2023-01-02T00:42:55.504087795Z
     *
     * @param textDate
     * @return
     */
    String cleanDateText(String textDate) {
        try {
            textDate = textDate.substring(0, textDate.lastIndexOf(".") + 4);
            textDate = textDate.replace("Z", "");
            textDate += "Z";
        } catch (Exception ignore) {

        }
        return textDate;
    }

    private List<UserTrade> downloadAdvancedTrade(List<ParsingProblem> parsingProblems) {
        var tradeService = exchange.getTradeService();
        Instant now = Instant.now();
        if (completedLastAdvanceTradeEndDatetime == 0) {
            completedLastAdvanceTradeEndDatetime = now.toEpochMilli();
        }
        List<CoinbaseAdvancedTradeFills> advancedTrades = new ArrayList<>();
        while (true) {
            var params = setParamsBeforeStart(tradeService, now);

            List<CoinbaseAdvancedTradeFills> advancedTradesBlock;
            try {
                CoinbaseAdvancedTradeOrderFillsResponse advancedTradeOrderFillsRow = null;
                if (tradeService instanceof CoinbaseTradeServiceCDP cdpKeys) {
                    advancedTradeOrderFillsRow = cdpKeys.getAdvancedTradeOrderFillsRow(params);
                } else if (tradeService instanceof CoinbaseTradeService legacyKeys) {
                    advancedTradeOrderFillsRow = legacyKeys.getAdvancedTradeOrderFillsRow(params);
                }
                if (advancedTradeOrderFillsRow == null) {
                    throw new IllegalStateException("Advanced trades download failed. ");
                }
                advancedTradesBlock = advancedTradeOrderFillsRow.getFills();
                cursorAdvanceTrade = advancedTradeOrderFillsRow.getCursor();
            } catch (Exception e) {
                if (e.getMessage().equalsIgnoreCase("HTTP status code was not OK: 403")) {
                    return new ArrayList<>();
                } else {
                    throw new IllegalStateException("Unable to download advanced trades. ", e);
                }
            }
            int size = advancedTradesBlock.size();
            if (size == 0) {
                partialLastAdvanceTradeStartDatetime = completedLastAdvanceTradeEndDatetime;
                partialLastAdvanceTradeEndDatetime = 0;
                completedLastAdvanceTradeEndDatetime = 0;
                break;
            } else if (size == TRANSACTIONS_PER_REQUEST_LIMIT) {
                Date minTradeDate;
                try {
                    String tradeTime = advancedTradesBlock.get(advancedTradesBlock.size() - 1).getTradeTime();
                    minTradeDate = createDateFromText(tradeTime);
                } catch (ParseException e) {
                    throw new RuntimeException("Unable to parse date of trade ", e);
                }
                partialLastAdvanceTradeEndDatetime = minTradeDate.getTime();
                advancedTrades.addAll(advancedTradesBlock);
            } else if (size < TRANSACTIONS_PER_REQUEST_LIMIT) {
                advancedTrades.addAll(advancedTradesBlock);
                partialLastAdvanceTradeStartDatetime = completedLastAdvanceTradeEndDatetime;
                partialLastAdvanceTradeEndDatetime = 0;
                completedLastAdvanceTradeEndDatetime = 0;
                break;
            } else {
                throw new IllegalStateException("Unknown state of downloaded data. ");
            }
        }
        List<UserTrade> userTrades = createUserTradesFromAdvancedTrades(advancedTrades, parsingProblems);
        return userTrades;
    }

    private List<UserTrade> createUserTradesFromAdvancedTrades(List<CoinbaseAdvancedTradeFills> fills,
                                                               List<ParsingProblem> parsingProblems) {
        List<UserTrade> trades = new ArrayList<>();
        for (CoinbaseAdvancedTradeFills fill : fills) {
            try {
                if(fill.getSizeInQuote().equals("true") || fill.getSizeInQuote().equals("false")) {
                    String[] currencies = fill.getProductId().split("-");
                    var base = new Currency(currencies[0]);
                    var quote = new Currency(currencies[1]);
                    CurrencyPair pair = new CurrencyPair(base, quote);
                    Date date = createDateFromText(fill.getTradeTime());
                    Order.OrderType type = fill.getSide().equalsIgnoreCase("BUY") ? Order.OrderType.BID : Order.OrderType.ASK;
                    BigDecimal baseAmount = fill.getSizeInQuote().equals("true")
                        ? AmountUtil.evaluateBaseAmount(fill.getSize(), fill.getPrice()) : fill.getSize();

                    UserTrade trade = new UserTrade(
                        type, baseAmount, pair,
                        fill.getPrice(), date, fill.getTradeId(),
                        fill.getOrderId(), fill.getCommission(), pair.getCounter(), fill.getUserId());

                    trades.add(trade);
                } else {
                    throw new DataValidationException(String.format("Unsupported size in quote value: %s", fill.getSizeInQuote()));
                }
            } catch (Exception e) {
                parsingProblems.add(new ParsingProblem("Advance trade error: " + fill.toString(), e.getMessage(), ROW_PARSING_FAILED));
            }
        }
        int size = parsingProblems.size();
        if (size > 0) {
            LOG.error("Several ( %s ) fills could not be processed", size);
        }
        return trades;
    }

    private List<CoinbaseShowTransactionV2> downloadTrades(Map<String, WalletState> walletStates) {
        var sortedWalletStates = sortWalletsByTxsUpdates(walletStates);
        var accountService = exchange.getAccountService();
        var wallets = sortedWalletStates.stream()
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (u, v) -> u, LinkedHashMap::new));
        final List<CoinbaseShowTransactionV2> userTrades = new ArrayList<>();

        int sentRequests = 0;
        String orderType = "asc";

        var tradeService = exchange.getTradeService();
        CoinbaseTradeHistoryParams params = (CoinbaseTradeHistoryParams) tradeService.createTradeHistoryParams();
        params.setLimit(TRANSACTIONS_PER_REQUEST_LIMIT);

        for (Map.Entry<String, WalletState> entry : wallets.entrySet()) {
            final String walletId = entry.getKey();
            final WalletState walletState = wallets.get(walletId);

            String lastTxId = walletState.lastBuyId;
            params.setStartId(lastTxId);
            List<CoinbaseShowTransactionV2> transactions = new LinkedList<>();
            boolean isNextPage = true;

            try {
                while (isNextPage) {
                    CoinbaseExpandTransactionsResponse response = null;
                    if (accountService instanceof CoinbaseAccountServiceRawCDP cdpKeys) {
                        response = cdpKeys.getExpandTransactions(walletId, params, orderType);
                    } else if (accountService instanceof CoinbaseAccountServiceRaw legacyKeys) {
                        response = legacyKeys.getExpandTransactions(walletId, params, orderType);
                    }

                    if (response == null) {
                        throw new IllegalStateException("Download trades history failed.");
                    }

                    if (response.getPagination().getNextUri() == null) {
                        isNextPage = false;
                    }
                    if (!response.getData().isEmpty()) {
                        transactions.addAll(response.getData());
                        params.setStartId(response.getData().get(response.getData().size() - 1).getId());
                        //if orderType desc = response.getData().get(0).getId()
                    }
                    sentRequests++;

                    //Uncomment Rate limiting in case of 429 error
                    //https://docs.cdp.coinbase.com/exchange/docs/rest-rate-limits/
//                    if (sentRequests % 15 == 0) {
//                        try {
//                            Thread.sleep(1000); // Sleep for 1 second after every 15 requests
//                        } catch (InterruptedException e) {
//                            Thread.currentThread().interrupt();
//                        }
//                    }
                }
                if (!transactions.isEmpty()) {
                    userTrades.addAll(transactions);
                    lastTxId = transactions.get(transactions.size() - 1).getId();
                    walletState.lastBuyId = lastTxId;
                    walletState.lastTxWalletUpdate = String.valueOf(new Date().getTime());
                }
            } catch (IOException e) {
                throw new IllegalStateException("Download buys history failed.", e);
            }
        }
        return userTrades;
    }

    public List<FundingRecord> downloadFunding(Map<String, WalletState> walletStates) {
        var walletStatesList = sortWalletsByFundingUpdates(walletStates);
        var wallets = walletStatesList.stream().
            collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (u, v) -> u, LinkedHashMap::new));

        final List<FundingRecord> fundingRecords = new ArrayList<>();

        var accountService = exchange.getAccountService();
        CoinbaseTradeHistoryParams params = (CoinbaseTradeHistoryParams) accountService.createFundingHistoryParams();
        params.setLimit(TRANSACTIONS_PER_REQUEST_LIMIT);

        for (Map.Entry<String, WalletState> entry : wallets.entrySet()) {
            final String walletId = entry.getKey();
            final WalletState walletState = wallets.get(walletId);
            String lastDepositId = entry.getValue().lastDepositId;
            String lastWithdrawalId = entry.getValue().lastWithdrawalId;

            while (true) {
                params.setStartId(lastDepositId);
                List<FundingRecord> depositRecords = new ArrayList<>();
                try {
                    if (accountService instanceof CoinbaseAccountServiceCDP cdpKeys) {
                        depositRecords = cdpKeys.getDepositHistory(params, walletId);
                    } else if (accountService instanceof CoinbaseAccountService legacyKeys) {
                        depositRecords = legacyKeys.getDepositHistory(params, walletId);
                    }
                } catch (IOException e) {
                    throw new IllegalStateException("Download deposit history failed.", e);
                }
                if (depositRecords.isEmpty()) {
                    break;
                }
                fundingRecords.addAll(depositRecords);
                lastDepositId = depositRecords.get(depositRecords.size() - 1).getInternalId();
            }

            while (true) {
                params.setStartId(lastWithdrawalId);
                List<FundingRecord> withdrawalRecords = new ArrayList<>();
                try {
                    if (accountService instanceof CoinbaseAccountServiceCDP cdpKeys) {
                        withdrawalRecords = cdpKeys.getWithdrawalHistory(params, walletId);
                    } else if (accountService instanceof CoinbaseAccountService legacyKeys) {
                        withdrawalRecords = legacyKeys.getWithdrawalHistory(params, walletId);
                    }
                } catch (IOException e) {
                    throw new IllegalStateException("Download sells history failed.", e);
                }
                if (withdrawalRecords.isEmpty()) {
                    break;
                }
                fundingRecords.addAll(withdrawalRecords);
                lastWithdrawalId = withdrawalRecords.get(withdrawalRecords.size() - 1).getInternalId();
            }

            walletState.lastDepositId = lastDepositId;
            walletState.lastWithdrawalId = lastWithdrawalId;

            walletState.lastFundingWalletUpdate = String.valueOf(new Date().getTime());
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
                            getOrNull(entry, 6)
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

    public static List<Map.Entry<String, WalletState>> sortWalletsByFundingUpdates(Map<String, WalletState> walletsMap) {
        List<Map.Entry<String, WalletState>> sortedWallets =
            walletsMap.entrySet().stream()
                .sorted(Map.Entry.comparingByValue(Comparator.comparingLong(c -> {
                    String lastFundingWalletUpdate = c.lastFundingWalletUpdate != null ? c.lastFundingWalletUpdate : String.valueOf(1L);
                    return Long.parseLong(lastFundingWalletUpdate);
                }))).collect(Collectors.toList());
        return sortedWallets;
    }

    public static List<Map.Entry<String, WalletState>> sortWalletsByTxsUpdates(Map<String, WalletState> walletsMap) {
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
        return String.valueOf(partialLastAdvanceTradeStartDatetime) + COLON_SYMBOL + String.valueOf(partialLastAdvanceTradeEndDatetime)
            + COLON_SYMBOL + String.valueOf(completedLastAdvanceTradeEndDatetime) + COLON_SYMBOL + cursorAdvanceTrade;
    }

    Date createDateFromText(String textTime) throws ParseException {
        var datePattern = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
        datePattern.setTimeZone(TimeZone.getTimeZone("UTC"));
        textTime = cleanDateText(textTime);
        return datePattern.parse(textTime);
    }

    Date createDateFromTextTradeFormat(String textTime) throws ParseException {
        var datePattern = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        datePattern.setTimeZone(TimeZone.getTimeZone("UTC"));
        return datePattern.parse(textTime);
    }
}