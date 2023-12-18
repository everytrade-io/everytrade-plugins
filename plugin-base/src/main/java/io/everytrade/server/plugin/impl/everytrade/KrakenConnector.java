package io.everytrade.server.plugin.impl.everytrade;

import io.everytrade.server.plugin.api.IPlugin;
import io.everytrade.server.plugin.api.connector.ConnectorDescriptor;
import io.everytrade.server.plugin.api.connector.ConnectorParameterDescriptor;
import io.everytrade.server.plugin.api.connector.ConnectorParameterType;
import io.everytrade.server.plugin.api.connector.DownloadResult;
import io.everytrade.server.plugin.api.connector.IConnector;
import io.everytrade.server.plugin.api.parser.ParsingProblem;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.ExchangeFactory;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.dto.account.FundingRecord;
import org.knowm.xchange.dto.trade.UserTrade;
import org.knowm.xchange.kraken.KrakenAdapters;
import org.knowm.xchange.kraken.KrakenExchange;
import org.knowm.xchange.kraken.KrakenUtils;
import org.knowm.xchange.kraken.dto.account.DepostitStatus;
import org.knowm.xchange.kraken.dto.account.KrakenLedger;
import org.knowm.xchange.kraken.dto.account.LedgerType;
import org.knowm.xchange.kraken.dto.account.WithdrawStatus;
import org.knowm.xchange.kraken.dto.trade.KrakenOrderType;
import org.knowm.xchange.kraken.dto.trade.KrakenTrade;
import org.knowm.xchange.kraken.dto.trade.KrakenType;
import org.knowm.xchange.kraken.service.KrakenAccountService;
import org.knowm.xchange.kraken.service.KrakenTradeHistoryParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static io.everytrade.server.model.SupportedExchange.KRAKEN;
import static io.everytrade.server.plugin.api.parser.ParsingProblemType.ROW_PARSING_FAILED;
import static java.math.BigDecimal.ZERO;
import static java.util.Collections.emptyList;
import static lombok.AccessLevel.PRIVATE;
import static org.knowm.xchange.dto.account.FundingRecord.Type.DEPOSIT;
import static org.knowm.xchange.dto.account.FundingRecord.Type.WITHDRAWAL;

@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = PRIVATE)
public class KrakenConnector implements IConnector {
    private static final Logger LOG = LoggerFactory.getLogger(KrakenConnector.class);
    private static final String ID = EveryTradePlugin.ID + IPlugin.PLUGIN_PATH_SEPARATOR + "krkApiConnector";
    private static final String WRONG_NUMBER_OF_TRANSACTIONS = "wrong number of txs - expected (1x RECEIVE and 1x SEND)";
    private static final String SPEND_POSITIVE_NUMBER = "Spend - transaction amount must be negative";
    private static final String RECEIVE_POSITIVE_NUMBER = "Receive - transaction amount must be positive";

    private static final int MAX_TRADE_REQUESTS_COUNT = 7;
    private static final Duration SLEEP_BETWEEN_TRADE_REQUESTS = Duration.ofMillis(6 * 1000);
    private static final Duration SLEEP_BETWEEN_FUNDING_REQUESTS = Duration.ofMillis(3 * 1000);

    private List<ParsingProblem> parsingProblems = new ArrayList<>();

    private static final ConnectorParameterDescriptor PARAMETER_API_SECRET =
        new ConnectorParameterDescriptor(
            "apiSecret",
            ConnectorParameterType.SECRET,
            "API Secret",
            "",
            false
        );

    private static final ConnectorParameterDescriptor PARAMETER_API_KEY =
        new ConnectorParameterDescriptor(
            "apiKey",
            ConnectorParameterType.STRING,
            "API Key",
            "",
            false
        );

    public static final ConnectorDescriptor DESCRIPTOR = new ConnectorDescriptor(
        ID,
        "Kraken Connector",
        "",
        KRAKEN.getInternalId(),
        List.of(PARAMETER_API_KEY, PARAMETER_API_SECRET)
    );

    Exchange exchange;

    public KrakenConnector(Map<String, String> parameters) {
        this(
            parameters.get(PARAMETER_API_KEY.getId()),
            parameters.get(PARAMETER_API_SECRET.getId())
        );
    }

    public KrakenConnector(@NonNull String apiKey, @NonNull String apiSecret) {
        ExchangeSpecification exSpec = new KrakenExchange().getDefaultExchangeSpecification();
        exSpec.setApiKey(apiKey);
        exSpec.setSecretKey(apiSecret);
        this.exchange = ExchangeFactory.INSTANCE.createExchange(exSpec);
    }



    @Override
    public String getId() {
        return ID;
    }

    @Override
    public DownloadResult getTransactions(String downloadStateStr) {
        KrakenDownloadState downloadState = KrakenDownloadState.deserialize(downloadStateStr);

        var userTrades = new ArrayList<UserTrade>();
        var funding = new ArrayList<FundingRecord>();
        int sentRequests = 0;
        while (sentRequests < MAX_TRADE_REQUESTS_COUNT) {
            try {
                Thread.sleep(SLEEP_BETWEEN_TRADE_REQUESTS.toMillis());
            } catch (InterruptedException e) {
                // ignore
            }
            var downloadResult = downloadTrades(downloadState);
            if (downloadResult.isEmpty()) {
                break;
            }
            userTrades.addAll(downloadResult);
            ++sentRequests;
        }

        var depositsWithdrawals = downloadDepositsAndWithdrawals(downloadState);
        var stakings = downloadStakings(downloadState);
        var spendReceive = downloadSpendAndReceive(downloadState);
        funding.addAll(depositsWithdrawals);
        funding.addAll(stakings);
        userTrades.addAll(spendReceive);
        return getResult(userTrades,funding, downloadState);
    }

    private DownloadResult getResult(List<UserTrade> trades, List<FundingRecord> funding, KrakenDownloadState state ) {
        var xchangeParser = new XChangeConnectorParser();
        xchangeParser.setExchange(KRAKEN);
        return new DownloadResult(xchangeParser.getParseResultWithProblems(trades, funding, parsingProblems), state.serialize());
    }

    private List<UserTrade> downloadTrades(KrakenDownloadState state) {
        var tradeService = exchange.getTradeService();
        var krakenTradeHistoryParams = (KrakenTradeHistoryParams) tradeService.createTradeHistoryParams();

        if (!state.hasEmptyState()) {
            krakenTradeHistoryParams.setStartId(state.getTradeLastContinuousTxUid());
        }

        var isGap = state.getTradeFirstTxUidAfterGap() != null;
        if (isGap) {
            krakenTradeHistoryParams.setEndId(state.getTradeFirstTxUidAfterGap());
        }

        List<UserTrade> downloadedBlock;
        try {
            var rowDownloadedBlock = tradeService.getTradeHistory(krakenTradeHistoryParams).getUserTrades();
            try {
                downloadedBlock =
                    rowDownloadedBlock.stream()
                        .sorted(Comparator.comparingDouble(trade -> Double.parseDouble(((UserTrade) trade).getOrderUserReference())))
                        .collect(Collectors.toList());
            } catch (Exception e) {
                downloadedBlock = rowDownloadedBlock;
            }
        } catch (IOException e) {
            throw new IllegalStateException("Download user trade history failed.", e);
        }

        if (downloadedBlock.isEmpty()) {
            LOG.debug("No funding in kraken.");
            return emptyList();
        }

        if (isGap) {
            // because of KrakenTradeHistoryParams#setEndId is inclusive
            downloadedBlock.remove(downloadedBlock.size() - 1);
        }

        final boolean noDataLeft = downloadedBlock.isEmpty();
        if (noDataLeft) {
            state.setTradeLastContinuousTxUid(state.getTradeLastTxUidAfterGap());
            state.setTradeFirstTxUidAfterGap(null);
            state.setTradeLastTxUidAfterGap(null);
            return emptyList();
        }

        final UserTrade firstDownloadedTx = downloadedBlock.get(0);
        state.setTradeFirstTxUidAfterGap(firstDownloadedTx.getId());
        if (state.getTradeLastTxUidAfterGap() == null) {
            final UserTrade lastDownloadedTx = downloadedBlock.get(downloadedBlock.size() - 1);
            state.setTradeLastTxUidAfterGap(lastDownloadedTx.getId());
        }

        return downloadedBlock;
    }

    private List<FundingRecord> downloadDepositsAndWithdrawals(KrakenDownloadState state) {
        var accountService = (KrakenAccountService) exchange.getAccountService();
        var result = new ArrayList<FundingRecord>();

        for (FundingRecord.Type type : List.of(DEPOSIT, WITHDRAWAL)) {
            var params = (KrakenAccountService.KrakenFundingHistoryParams) accountService.createFundingHistoryParams();
            params.setType(type);
            if (type == DEPOSIT && state.getDepositFromTimestamp() != null) {
                params.setStartTime(new Date(state.getDepositFromTimestamp()));
            } else if (type == WITHDRAWAL && state.getWithdrawalFromTimestamp() != null) {
                params.setStartTime(new Date(state.getWithdrawalFromTimestamp()));
            }

            List<FundingRecord> downloadedBlock;
            List<DepostitStatus> depositStatuses = new ArrayList<>();
            List<WithdrawStatus> withdrawalStatuses = new ArrayList<>();
            try {
                downloadedBlock = accountService.getFundingHistory(params);
                // adding addresses into records
                Thread.sleep(SLEEP_BETWEEN_FUNDING_REQUESTS.toMillis());
                Set<Currency> assets = getListOfAssets(downloadedBlock); // list of unique currencies
                if (type == DEPOSIT) {
                    for (Currency a : assets) {
                        // getting a block of recent (last three months) deposit statuses (status contains addresses)
                        Thread.sleep(SLEEP_BETWEEN_FUNDING_REQUESTS.toMillis());
                        List<DepostitStatus> getBlockWithAddresses = accountService.getDepositStatus(null,
                            KrakenUtils.getKrakenCurrencyCode(a), null);
                        depositStatuses.addAll(getBlockWithAddresses);
                    }
                    // previous records - replacement
                    downloadedBlock = depositAddresses(downloadedBlock, depositStatuses);
                }

                if (type == WITHDRAWAL) {
                    for (Currency a : assets) {
                        // getting a block of recent (last three months) deposit statuses (status contains addresses)
                        Thread.sleep(SLEEP_BETWEEN_FUNDING_REQUESTS.toMillis());
                        List<WithdrawStatus> getBlockWithAddresses = accountService.getWithdrawStatus(null,
                            KrakenUtils.getKrakenCurrencyCode(a), null);
                        withdrawalStatuses.addAll(getBlockWithAddresses);
                    }
                    // previous records - replacement
                    downloadedBlock = withdrawalAddresses(downloadedBlock, withdrawalStatuses);
                }

            } catch (IOException | InterruptedException e) {
                throw new IllegalStateException("Download user trade history failed.", e);
            }

            if (downloadedBlock.isEmpty()) {
                LOG.info("No transactions in Kraken user history.");
                continue;
            }

            var lastDate = downloadedBlock.stream().map(it -> it.getDate().getTime()).max(Long::compare).get() + 1000;
            if (type == DEPOSIT) {
                state.setDepositFromTimestamp(lastDate);
            } else {
                state.setWithdrawalFromTimestamp(lastDate);
            }
            result.addAll(downloadedBlock);
        }
        return result;
    }

    private void validateReceiveSendPair(List<KrakenLedger> pair) {
        if (pair.size() != 2 ||
            !(("RECEIVE".equalsIgnoreCase(pair.get(0).getLedgerType().toString())
                && "SPEND".equalsIgnoreCase(pair.get(1).getLedgerType().toString())) ||
                ("RECEIVE".equalsIgnoreCase(pair.get(1).getLedgerType().toString())
                    && "SPEND".equalsIgnoreCase(pair.get(0).getLedgerType().toString())))) {
            throw new IllegalArgumentException(WRONG_NUMBER_OF_TRANSACTIONS);
        }
        KrakenLedger spend;
        KrakenLedger receive;
        if (pair.get(0).getLedgerType().toString().equalsIgnoreCase(LedgerType.SPEND.toString())) {
            spend = pair.get(0);
            receive = pair.get(1);
        } else {
            spend = pair.get(1);
            receive = pair.get(0);
        }
        if (spend.getTransactionAmount().compareTo(ZERO) > 1) {
            throw new IllegalArgumentException(SPEND_POSITIVE_NUMBER);
        }

        if (receive.getTransactionAmount().compareTo(ZERO) < 1) {
            throw new IllegalArgumentException(RECEIVE_POSITIVE_NUMBER);
        }
    }

    private List<UserTrade> downloadSpendAndReceive(KrakenDownloadState state) {
        var accountService = (KrakenAccountService) exchange.getAccountService();
        try {
            var ledgers = accountService.getKrakenLedgerInfo(LedgerType.SALE, null, null, null,
                state.getSpendReceiveFromUnixTimestamp(), null, null);
            var keys = ledgers.keySet();
            List<KrakenLedger> rowData = ledgers.values().stream().toList();
            Map<String, List<KrakenLedger>> pairsReceiveSend = new HashMap<>();
            rowData.forEach(leger -> {
                    try {
                        if (pairsReceiveSend.get(leger.getRefId()) == null) {
                            List<KrakenLedger> list = new ArrayList<>();
                            list.add(leger);
                            pairsReceiveSend.put(leger.getRefId(), list);
                        } else {
                            List<KrakenLedger> krakenLedgers = pairsReceiveSend.get(leger.getRefId());
                            krakenLedgers.add(leger);
                        }
                    } catch (Exception e) {
                        parsingProblems.add(new ParsingProblem(ledgers.toString(), e.getMessage(), ROW_PARSING_FAILED));
                    }
                }
            );
            var resultsKeys = pairsReceiveSend.keySet();
            List<KrakenTrade> krakenTrades = new ArrayList<>();
            Map<String, KrakenTrade> sells = new HashMap<>();

            for (String key : resultsKeys) {
                var receiveSpendPair = pairsReceiveSend.get(key);
                try {
                    validateReceiveSendPair(receiveSpendPair);
                    KrakenLedger spend;
                    KrakenLedger receive;
                    if (receiveSpendPair.get(0).getLedgerType().toString().equalsIgnoreCase(LedgerType.SPEND.toString())) {
                        spend = receiveSpendPair.get(0);
                        receive = receiveSpendPair.get(1);
                    } else {
                        spend = receiveSpendPair.get(1);
                        receive = receiveSpendPair.get(0);
                    }
                    var krakenTrade = new KrakenTrade(receive.getRefId(),
                        currencySwitcher(spend.getAsset()).concat("/").concat(currencySwitcher((receive.getAsset()))),
                        spend.getUnixTime(), KrakenType.SELL, KrakenOrderType.LIMIT,
                        spend.getTransactionAmount().abs().divide(receive.getTransactionAmount(),
                        RoundingMode.HALF_UP), receive.getTransactionAmount(), receive.getFee(), spend.getTransactionAmount().abs(),
                        null, null, null, null, null, null, null, null, null, null, null);
                    krakenTrades.add(krakenTrade);
                } catch (Exception e) {
                    parsingProblems.add(new ParsingProblem(receiveSpendPair.toString(), e.getMessage(), ROW_PARSING_FAILED));
                }

            }
            krakenTrades.forEach(t -> sells.put(t.getOrderTxId(), t));
            if (!ledgers.isEmpty()) {
                var maxTimeValue = ledgers.values().stream().mapToDouble(KrakenLedger::getUnixTime).max().getAsDouble();
                state.setSpendReceiveFromUnixTimestamp(String.valueOf(maxTimeValue));
            }
            return KrakenAdapters.adaptTradesHistory(sells).getUserTrades();
        } catch (IOException e) {
            throw new IllegalStateException("Download receive spend history failed.", e);
        }
    }

    private List<FundingRecord> removeOldStakes(List<FundingRecord> block, KrakenDownloadState state) {
        var lastDate = state.getStakeLastTimestamp() == null ? 0 : state.getStakeLastTimestamp();
        return block.stream().filter(r -> r.getDate().getTime() > lastDate).collect(Collectors.toList());
    }

    private String currencySwitcher(String currency) {
        try {
            return KrakenAdapters.adaptCurrency(currency).getCurrencyCode();
        } catch (Exception ignore) {
            return currency;
        }
    }

    private List<FundingRecord> downloadStakings(KrakenDownloadState state) {
        var accountService = (KrakenAccountService) exchange.getAccountService();
        List<FundingRecord> downloadedBlock = new ArrayList<>();
        try {
            // download all stakes inc old one
            downloadedBlock = accountService.getStakingHistory();
            if (downloadedBlock.isEmpty()) {
                return downloadedBlock;
            }
            // remove the old one
            downloadedBlock = removeOldStakes(downloadedBlock, state);
        } catch (IOException e) {
            throw new IllegalStateException("Download user staking history failed.", e);
        }
        if(downloadedBlock.isEmpty()) {
            return downloadedBlock;
        }
        var lastDate = downloadedBlock.stream().map(it -> it.getDate().getTime()).max(Long::compare).get() + 1000;
        state.setStakeLastTimestamp(lastDate);
        return downloadedBlock;
    }

    public Set<Currency> getListOfAssets(final List<FundingRecord> fundings) {
        return fundings.stream().map(record -> record.getCurrency()).collect(Collectors.toSet());
    }


    public List<FundingRecord> depositAddresses(List<FundingRecord> recordsBlock, List<DepostitStatus> statuses ) {
        final List<FundingRecord> recordsWithAddresses = new ArrayList<>();
        for (FundingRecord r : recordsBlock) {
            String internalId = r.getInternalId();
            var status = statuses.stream().filter(s -> (s.getRefid().equals(internalId))).findAny();
            String newAddress = !status.isEmpty() ? status.get().getInfo() : null;
            recordsWithAddresses.add(recordWithAddress(r,newAddress));
        }
        return recordsWithAddresses;
    }

    public List<FundingRecord> withdrawalAddresses(List<FundingRecord> recordsBlock, List<WithdrawStatus> statuses ) {
        final List<FundingRecord> recordsWithAddresses = new ArrayList<>();
        for (FundingRecord r : recordsBlock) {
            String internalId = r.getInternalId();
            var status = statuses.stream().filter(s -> (s.getRefid().equals(internalId))).findAny();
            String newAddress = !status.isEmpty() ? status.get().getInfo() : null;
            recordsWithAddresses.add(recordWithAddress(r,newAddress));
        }
        return recordsWithAddresses;
    }

    private FundingRecord recordWithAddress(FundingRecord oldRecord, String address) {
        return new FundingRecord.Builder().from(oldRecord).setAddress(address).build();
    }

}
