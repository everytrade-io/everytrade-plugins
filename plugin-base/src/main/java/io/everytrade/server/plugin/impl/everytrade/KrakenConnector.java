package io.everytrade.server.plugin.impl.everytrade;

import com.univocity.parsers.common.DataValidationException;
import io.everytrade.server.model.TransactionType;
import io.everytrade.server.plugin.api.IPlugin;
import io.everytrade.server.plugin.api.connector.ConnectorDescriptor;
import io.everytrade.server.plugin.api.connector.ConnectorParameterDescriptor;
import io.everytrade.server.plugin.api.connector.ConnectorParameterType;
import io.everytrade.server.plugin.api.connector.DownloadResult;
import io.everytrade.server.plugin.api.connector.IConnector;
import io.everytrade.server.plugin.api.parser.ParsingProblem;
import io.everytrade.server.util.serialization.ConnectorSerialization;
import io.everytrade.server.util.serialization.SequenceIdentifierType;
import io.everytrade.server.util.serialization.Uid;
import io.everytrade.server.util.serialization.Uids;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.ExchangeFactory;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.Order;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import static io.everytrade.server.model.SupportedExchange.KRAKEN;
import static io.everytrade.server.model.TransactionType.BUY;
import static io.everytrade.server.model.TransactionType.SELL;
import static io.everytrade.server.plugin.api.parser.ParsingProblemType.PARSED_ROW_IGNORED;
import static io.everytrade.server.plugin.api.parser.ParsingProblemType.ROW_PARSING_FAILED;
import static io.everytrade.server.plugin.impl.everytrade.parser.ParserUtils.DECIMAL_DIGITS;
import static io.everytrade.server.util.serialization.DownloadedStatus.ALL_DATA_DOWNLOADED;
import static io.everytrade.server.util.serialization.DownloadedStatus.PARTIAL_DATA_DOWNLOADED;
import static io.everytrade.server.util.serialization.SequenceIdentifierType.END;
import static io.everytrade.server.util.serialization.SequenceIdentifierType.OFFSET;
import static io.everytrade.server.util.serialization.SequenceIdentifierType.START;
import static io.everytrade.server.util.serialization.SequenceIdentifierType.STATUS;
import static java.math.BigDecimal.ZERO;
import static java.math.RoundingMode.HALF_UP;
import static lombok.AccessLevel.PRIVATE;
import static org.knowm.xchange.dto.account.FundingRecord.Type.DEPOSIT;
import static org.knowm.xchange.dto.account.FundingRecord.Type.OTHER_INFLOW;
import static org.knowm.xchange.dto.account.FundingRecord.Type.WITHDRAWAL;
import static org.knowm.xchange.kraken.dto.account.LedgerType.SALE;
import static org.knowm.xchange.kraken.dto.account.LedgerType.STAKING;
import static org.knowm.xchange.kraken.dto.account.LedgerType.TRADE;

@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = PRIVATE)
public class KrakenConnector implements IConnector {
    private static final Logger LOG = LoggerFactory.getLogger(KrakenConnector.class);
    private static final String ID = EveryTradePlugin.ID + IPlugin.PLUGIN_PATH_SEPARATOR + "krkApiConnector";
    private static final String WRONG_NUMBER_OF_TRANSACTIONS = "wrong number of txs - expected (1x RECEIVE and 1x SEND)";
    private static final String SPEND_POSITIVE_NUMBER = "Spend - transaction amount must be negative";
    private static final String RECEIVE_POSITIVE_NUMBER = "Receive - transaction amount must be positive";
    private static final Duration SLEEP_BETWEEN_FUNDING_REQUESTS = Duration.ofMillis(3 * 1000);

    private static final int MAX_REQUESTS_COUNT = 50;
    public static final String UID_TRADES_ID = "1";
    public static final String UID_SALE_ID = "2";
    public static final String UID_DEPOSIT_ID = "3";
    public static final String UID_WITHDRAWAL_ID = "4";
    public static final String UID_STAKING_ID = "5";
    public static final String EXCEPTION_PAIR = "Invalid value of pairs: ";
    public static final String EXCEPTION_CURRENCY = "Invalid currencies: ";
    public static final String EXCEPTION_AMOUNT = "Invalid transactionAmounts: ";
    public static final String EXCEPTION_FEE_AMOUNT = "Invalid volume of fees in: ";
    public static final String EXCEPTION_WITHDRAWAL_PAIR_VALUES = "Invalid withdrawal pair values: ";
    public static final long DEFAULT_BLOCK_SIZE = 50;

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
    public DownloadResult getTransactions(String savedState) {
        Uids state = getDefaultUids();
        if (savedState != null) {
            try {
                state = ConnectorSerialization.deserialize(savedState);
            } catch (Exception e) {
                LOG.info("Kraken state cannot be deserialize - resynchronize container");
                throw new IllegalStateException("Old container state version - please resynchronize container");
            }
        }
        var userTrades = new ArrayList<UserTrade>();
        var funding = new ArrayList<FundingRecord>();
        funding.addAll(withdrawal(state));
        userTrades.addAll(trades(state));
        funding.addAll(staking(state));
        funding.addAll(deposit(state));
        userTrades.addAll(SpendAndReceive(state));
        return getResult(userTrades, funding, state);
    }

    private static Uids getDefaultUids() {
        Uids state = ConnectorSerialization.createDefaultUidMap();
        String start = getDefaultStart();
        String end = getDefaultEnd();
        Map<SequenceIdentifierType, String> trade = new TreeMap<>();
        trade.put(START, start);
        trade.put(END, end);
        trade.put(OFFSET, "0");
        trade.put(STATUS, PARTIAL_DATA_DOWNLOADED.getCode());
        Map<SequenceIdentifierType, String> sale = new TreeMap<>();
        sale.put(START, start);
        sale.put(END, end);
        sale.put(OFFSET, "0");
        sale.put(STATUS,  PARTIAL_DATA_DOWNLOADED.getCode());
        Map<SequenceIdentifierType, String> deposit = new TreeMap<>();
        deposit.put(START, start);
        deposit.put(END, end);
        deposit.put(OFFSET, "0");
        deposit.put(STATUS,  PARTIAL_DATA_DOWNLOADED.getCode());
        Map<SequenceIdentifierType, String> withdrawal = new TreeMap<>();
        withdrawal.put(START, start);
        withdrawal.put(END, end);
        withdrawal.put(OFFSET, "0");
        withdrawal.put(STATUS,  PARTIAL_DATA_DOWNLOADED.getCode());
        Map<SequenceIdentifierType, String> staking = new TreeMap<>();
        staking.put(START, start);
        staking.put(END, end);
        staking.put(OFFSET, "0");
        staking.put(STATUS,  PARTIAL_DATA_DOWNLOADED.getCode());
        state.addUid(UID_TRADES_ID, new Uid(trade));
        state.addUid(UID_SALE_ID, new Uid(sale));
        state.addUid(UID_DEPOSIT_ID, new Uid(deposit));
        state.addUid(UID_WITHDRAWAL_ID, new Uid(withdrawal));
        state.addUid(UID_STAKING_ID, new Uid(staking));
        return state;
    }

    private static String getDefaultStart() {
        return String.valueOf(Instant.parse("1970-01-01T00:00:00Z").getEpochSecond());
    }

    private static String getDefaultEnd() {
        return String.valueOf(Instant.now().getEpochSecond());
    }

    private DownloadResult getResult(List<UserTrade> trades, List<FundingRecord> funding, Uids state) {
        var xchangeParser = new XChangeConnectorParser();
        xchangeParser.setExchange(KRAKEN);
        return new DownloadResult(xchangeParser.getParseResultWithProblems(trades, funding, parsingProblems),
            ConnectorSerialization.serialize(state));
    }

    private List<FundingRecord> deposit(Uids state) {
        var accountService = (KrakenAccountService) exchange.getAccountService();
        String startUnixId = getStartState(state, UID_DEPOSIT_ID);
        String endUnixId = getEndState(state, UID_DEPOSIT_ID);
        Long offset = getOffsetState(state, UID_DEPOSIT_ID);
        List<KrakenLedger> blocks = new ArrayList<>();
        try {
            downloadLedgers(state, accountService, startUnixId, endUnixId, offset, blocks, UID_DEPOSIT_ID, LedgerType.DEPOSIT);
        } catch (IOException e) {
            throw new IllegalStateException("Download user trade history failed.", e);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        return createFundings(blocks, DEPOSIT, null);
    }

    private List<FundingRecord> withdrawal(Uids state) {
        var accountService = (KrakenAccountService) exchange.getAccountService();
        String startUnixId = getStartState(state, UID_WITHDRAWAL_ID);
        String endUnixId = getEndState(state, UID_WITHDRAWAL_ID);
        Long offset = getOffsetState(state, UID_WITHDRAWAL_ID);

        List<KrakenLedger> blocks = new ArrayList<>();
        try {
            downloadLedgers(state, accountService, startUnixId, endUnixId, offset, blocks, UID_WITHDRAWAL_ID, LedgerType.WITHDRAWAL);
            validateWithdrawals(blocks);
        } catch (IOException e) {
            throw new IllegalStateException("Download user trade history failed.", e);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        return createFundings(blocks, WITHDRAWAL, null);
    }

    private void validateTradePairs(List<KrakenLedger> pair) {
        if (pair.size() != 2) {
            throw new DataValidationException(EXCEPTION_PAIR + pair);
        }
        KrakenLedger leger1 = pair.get(0);
        KrakenLedger ledger2 = pair.get(1);

        io.everytrade.server.model.Currency currency1 = switchKrakenAssetToCurrency(leger1.getAsset());
        io.everytrade.server.model.Currency currency2 = switchKrakenAssetToCurrency(ledger2.getAsset());

        if ((currency1.isFiat() && currency2.isFiat())) {
            throw new DataValidationException(EXCEPTION_CURRENCY + pair);
        }
        var volume1 = leger1.getTransactionAmount();
        var volume2 = ledger2.getTransactionAmount();

        if ((volume1.compareTo(ZERO) > 0 && volume2.compareTo(ZERO) > 0) && volume1.compareTo(ZERO) < 0 && volume2.compareTo(ZERO) < 0) {
            throw new DataValidationException(EXCEPTION_AMOUNT + pair);
        }

        var fee1 = leger1.getFee();
        var fee2 = ledger2.getFee();
        var feeTime = convertTimeFromUnix(leger1.getUnixTime());

        if (fee1.compareTo(ZERO) < 0 && fee2.compareTo(ZERO) < 0) {
            parsingProblems.add(new ParsingProblem(pair.toString(), EXCEPTION_FEE_AMOUNT + feeTime, ROW_PARSING_FAILED));
        }
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
        if (pair.get(0).getLedgerType().toString().equalsIgnoreCase(TRADE.toString())) {
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

    private List<UserTrade> SpendAndReceive(Uids state) {
        var accountService = (KrakenAccountService) exchange.getAccountService();
        String startUnixId = getStartState(state, UID_SALE_ID);
        String endUnixId = getEndState(state, UID_SALE_ID);
        Long offset = getSaleOffsetState(state);
        List<KrakenLedger> blocks = new ArrayList<>();
        try {
            downloadLedgers(state, accountService, startUnixId, endUnixId, offset, blocks, UID_SALE_ID, SALE);
            Map<String, List<KrakenLedger>> pairsReceiveSend = new HashMap<>();
            blocks.forEach(leger -> {
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
                        parsingProblems.add(new ParsingProblem(leger.toString(), e.getMessage(), ROW_PARSING_FAILED));
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
                    KrakenType transactionType;
                    if (io.everytrade.server.model.Currency.fromCode(currencySwitcher(receive.getAsset())).isFiat()) {
                        transactionType = KrakenType.SELL;
                    } else {
                        transactionType = KrakenType.BUY;
                    }
                    var krakenTrade = new KrakenTrade(
                        receive.getRefId(),
                        currencySwitcher(receive.getAsset()).concat("/").concat(currencySwitcher((spend.getAsset()))),
                        spend.getUnixTime(),
                        transactionType,
                        KrakenOrderType.LIMIT,
                        spend.getTransactionAmount().abs().divide(receive.getTransactionAmount(), RoundingMode.HALF_UP),
                        null,
                        receive.getFee(),
                        receive.getTransactionAmount(),
                        null, null, null, null, null, null,
                        null, null, null, null, null);
                    krakenTrades.add(krakenTrade);
                } catch (Exception e) {
                    parsingProblems.add(new ParsingProblem(receiveSpendPair.toString(), e.getMessage(), ROW_PARSING_FAILED));
                }

            }
            krakenTrades.forEach(t -> sells.put(t.getOrderTxId(), t));
            return KrakenAdapters.adaptTradesHistory(sells).getUserTrades();
        } catch (IOException e) {
            throw new IllegalStateException("Download receive spend history failed.", e);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private static void downloadLedgers(Uids state, KrakenAccountService accountService, String startUnixId,
                                        String endUnixId, Long offset, List<KrakenLedger> blocks, String uidType,
                                        LedgerType ledgerType) throws IOException, InterruptedException {
        int requests = 0;
        while (requests < MAX_REQUESTS_COUNT) {
            Thread.sleep(SLEEP_BETWEEN_FUNDING_REQUESTS.toMillis());
            var block = accountService.getKrakenPartialLedgerInfo(ledgerType, startUnixId, endUnixId, offset);
            if (block.isEmpty()) {
                String start = getNewStart(state, uidType);
                state.getUidS().get(uidType).setUid(OFFSET, "0");
                state.getUidS().get(uidType).setUid(END, null);
                state.getUidS().get(uidType).setUid(START, start);
                state.getUidS().get(uidType).setUid(STATUS, ALL_DATA_DOWNLOADED.getCode());
                break;
            } else if (block.values().size() < DEFAULT_BLOCK_SIZE) {
                List<KrakenLedger> values = block.values().stream().toList();
                blocks.addAll(values);
                String start = getNewStart(state, uidType);
                state.getUidS().get(uidType).setUid(END, null);
                state.getUidS().get(uidType).setUid(OFFSET, "0");
                state.getUidS().get(uidType).setUid(START, start);
                state.getUidS().get(uidType).setUid(STATUS, ALL_DATA_DOWNLOADED.getCode());
                break;
            } else if (block.values().size() == DEFAULT_BLOCK_SIZE) {
                blocks.addAll(block.values());
                offset += DEFAULT_BLOCK_SIZE;
                state.getUidS().get(uidType).setUid(OFFSET, String.valueOf(offset));
                state.getUidS().get(uidType).setUid(STATUS, PARTIAL_DATA_DOWNLOADED.getCode());
                state.getUidS().get(uidType).setUid(START, startUnixId);
                state.getUidS().get(uidType).setUid(END, endUnixId);
                requests++;
            } else {
                throw new IllegalStateException("Unidentified state - ledger sales");
            }
        }
    }

    private static String getNewStart(Uids state, String uidType) {
            return state.getUidS().get(uidType).getUid().get(END);
    }

    private String getStartState(Uids state, String uidId) {
        Map<String, Uid> uidS = state.getUidS();
        try {
            return uidS.get(uidId).getUid().get(START);
        } catch (NullPointerException e) {
            if (uidS.get(uidId) == null) {
                state.addUid(uidId, new Uid(new TreeMap<>()));
            }
            return null;
        }
    }

    private String getEndState(Uids state, String uidId) {
        Map<String, Uid> uidS = state.getUidS();
        try {
            if (uidS.get(uidId).getUid().get(END) == null) {
                uidS.get(uidId).setUid(END, getDefaultEnd());
            } else {
                return uidS.get(uidId).getUid().get(END);
            }
            return uidS.get(uidId).getUid().get(END);
        } catch (NullPointerException e) {
            Map<SequenceIdentifierType, String> uid = new TreeMap<>();
            uid.put(END, getDefaultEnd());
            if (uidS.get(uidId) == null) {
                state.addUid(uidId, new Uid(uid));
            }
            return uidS.get(uidId).getUid().get(END);
        }
    }

    private Long getSaleOffsetState(Uids state) {
        Map<String, Uid> uidS = state.getUidS();
        try {
            return Long.valueOf(uidS.get(UID_SALE_ID).getUid().get(OFFSET));
        } catch (Exception e) {
            if (uidS.get(UID_SALE_ID) == null) {
                state.addUid(UID_SALE_ID, new Uid(new TreeMap<>()));
            }
            return 0L;
        }
    }

    private Long getOffsetState(Uids state, String uidId) {
        Map<String, Uid> uidS = state.getUidS();
        try {
            return Long.valueOf(uidS.get(uidId).getUid().get(OFFSET));
        } catch (Exception e) {
            if (uidS.get(uidId) == null) {
                state.addUid(uidId, new Uid(new TreeMap<>()));
            }
            return 0L;
        }
    }

    private List<KrakenLedger> findBaseAndQuote(List<KrakenLedger> ledgerPairs) {
        List<KrakenLedger> pairs = new ArrayList<>();
        if (switchKrakenAssetToCurrency(ledgerPairs.get(0).getAsset()).isFiat()
            && !switchKrakenAssetToCurrency(ledgerPairs.get(1).getAsset()).isFiat()) {
            pairs.add(ledgerPairs.get(1));
            pairs.add(ledgerPairs.get(0));
        } else if (!switchKrakenAssetToCurrency(ledgerPairs.get(0).getAsset()).isFiat()
            && switchKrakenAssetToCurrency(ledgerPairs.get(1).getAsset()).isFiat()) {
            pairs.add(ledgerPairs.get(0));
            pairs.add(ledgerPairs.get(1));
        } else if (!switchKrakenAssetToCurrency(ledgerPairs.get(0).getAsset()).isFiat()
            && !switchKrakenAssetToCurrency(ledgerPairs.get(1).getAsset()).isFiat()) {
            if (ledgerPairs.get(0).getTransactionAmount().compareTo(ZERO) >= 0
                && ledgerPairs.get(1).getTransactionAmount().compareTo(ZERO) <= 0) {
                pairs.add(ledgerPairs.get(0));
                pairs.add(ledgerPairs.get(1));
            } else if (ledgerPairs.get(1).getTransactionAmount().compareTo(ZERO) >= 0
                && ledgerPairs.get(0).getTransactionAmount().compareTo(ZERO) <= 0) {
                pairs.add(ledgerPairs.get(1));
                pairs.add(ledgerPairs.get(0));
            } else {
                throw new DataValidationException("Uknown base transaction");
            }
        } else {
            throw new DataValidationException("Uknown base transaction");
        }
        return pairs;
    }

    private KrakenLedger findFee(KrakenLedger base, KrakenLedger quote) {
        KrakenLedger fee = null;
        try {
            if (base.getFee().compareTo(ZERO) >= 0 && quote.getFee().compareTo(ZERO) == 0) {
                fee = base;
            } else if (quote.getFee().compareTo(ZERO) >= 0 && base.getFee().compareTo(ZERO) == 0) {
                fee = quote;
            } else if (quote.getFee().compareTo(ZERO) > 0 && base.getFee().compareTo(ZERO) > 0) {
                fee = quote;
            } else {
                parsingProblems.add(new ParsingProblem("Cannot find fee", base.toString(), PARSED_ROW_IGNORED));
            }
        } catch (Exception e) {
            parsingProblems.add(new ParsingProblem("Cannot find fee", e.getMessage(), PARSED_ROW_IGNORED));
        }
        return fee;
    }

    private UserTrade convertLedgerPairToTrade(List<KrakenLedger> ledgerPairs) {
        var pairs = findBaseAndQuote(ledgerPairs);
        var base = pairs.get(0);
        var quote = pairs.get(1);

        TransactionType type = getTransactionType(base, quote);
        KrakenLedger fee = findFee(base, quote);

        return UserTrade.builder()
            .id(base.getRefId())
            .timestamp(convertUnixToDate(base.getUnixTime()))
            .originalAmount(base.getTransactionAmount().abs())
            .currencyPair(new CurrencyPair(translateKrakenCurrency(base.getAsset()), translateKrakenCurrency(quote.getAsset())))
            .type(type.equals(BUY) ? Order.OrderType.BID : Order.OrderType.ASK)
            .price(quote.getTransactionAmount().divide(base.getTransactionAmount(), DECIMAL_DIGITS, HALF_UP).abs())
            .feeCurrency(translateKrakenCurrency(fee.getAsset()))
            .feeAmount(fee.getFee().abs())
            .build();
    }

    private List<UserTrade> convertLedgerPairsToTrade(Map<String, List<KrakenLedger>> pairs) {
        var resultsKeys = pairs.keySet();
        List<UserTrade> trades = new ArrayList<>();
        for (String key : resultsKeys) {
            var pair = pairs.get(key);
            try {
                validateTradePairs(pair);
                UserTrade trade = convertLedgerPairToTrade(pair);
                trades.add(trade);
            } catch (Exception e) {
                parsingProblems.add(new ParsingProblem(pair.toString(), e.getMessage(), ROW_PARSING_FAILED));
            }
        }
        return trades;
    }

    private TransactionType getTransactionType(KrakenLedger base, KrakenLedger quote) {
        // crypto-crypto is always buy
        if (!switchKrakenAssetToCurrency(base.getAsset()).isFiat() && !switchKrakenAssetToCurrency(quote.getAsset()).isFiat()) {
            return BUY;
        // base is always crypto
        } else if (base.getTransactionAmount().compareTo(ZERO) >= 0 && quote.getTransactionAmount().compareTo(ZERO) <= 0) {
            return BUY;
        } else if (base.getTransactionAmount().compareTo(ZERO) <= 0 && quote.getTransactionAmount().compareTo(ZERO) >= 0) {
            return SELL;
        } else {
            throw new DataValidationException("Unknown transaction type");
        }
    }

    private List<UserTrade> trades(Uids state) {
        var accountService = (KrakenAccountService) exchange.getAccountService();
        String startUnixId = getStartState(state, UID_TRADES_ID);
        String endUnixId = getEndState(state, UID_TRADES_ID);
        Long offset = getOffsetState(state, UID_TRADES_ID);
        List<KrakenLedger> blocks = new ArrayList<>();
        try {
            downloadLedgers(state, accountService, startUnixId, endUnixId, offset, blocks, UID_TRADES_ID, TRADE);
        } catch (IOException e) {
            throw new IllegalStateException("Download user trade history failed.", e);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        Map<String, List<KrakenLedger>> pairs = getPairsFromBlocks(blocks);
        return convertLedgerPairsToTrade(pairs);
    }

    private List<FundingRecord> staking(Uids state) {
        var accountService = (KrakenAccountService) exchange.getAccountService();
        String startUnixId = getStartState(state, UID_STAKING_ID);
        String endUnixId = getEndState(state, UID_STAKING_ID);
        Long offset = getOffsetState(state, UID_STAKING_ID);
        List<KrakenLedger> blocks = new ArrayList<>();
        try {
            downloadLedgers(state, accountService, startUnixId, endUnixId, offset, blocks, UID_STAKING_ID, STAKING);
        } catch (IOException e) {
            throw new IllegalStateException("Download funding records history failed.", e);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        return createFundings(blocks, OTHER_INFLOW, "reward");
    }

    private List<FundingRecord> createFundings(List<KrakenLedger> blocks, FundingRecord.Type type, String description) {
        List<FundingRecord> records = new ArrayList<>();
        blocks.forEach(ledger -> {
            Date date = convertUnixToDate(ledger.getUnixTime());
            Currency currency = translateKrakenCurrency(ledger.getAsset());
            BigDecimal feeAmount = ledger.getFee();
            BigDecimal transactionAmount = ledger.getTransactionAmount();
            String refId = ledger.getRefId();
            records.add(new FundingRecord(null, null, date, currency, transactionAmount, refId, null, type, null, null, feeAmount,
                description));
        });
        return records;
    }

    private void validateWithdrawals(List<KrakenLedger> ledgers) {
        var ledgersGroupedByRefId = ledgers.stream().collect(Collectors.groupingBy(KrakenLedger::getRefId));
        List<KrakenLedger> removeLedgers = new ArrayList<>();
        ledgers.stream().forEach(ledger -> {
                if (ledgersGroupedByRefId.get(ledger.getRefId()).size() != 1) {
                    parsingProblems.add(new ParsingProblem(ledger.toString(), EXCEPTION_WITHDRAWAL_PAIR_VALUES, PARSED_ROW_IGNORED));
                    removeLedgers.add(ledger);
                }
            }
        );
        ledgers.removeAll(removeLedgers);
    }

    private Map<String, List<KrakenLedger>> getPairsFromBlocks(List<KrakenLedger> rowData) {
        Map<String, List<KrakenLedger>> pairs = new TreeMap<>();
        rowData.forEach(row -> {
                try {
                    if (pairs.get(row.getRefId()) == null) {
                        List<KrakenLedger> list = new ArrayList<>();
                        list.add(row);
                        pairs.put(row.getRefId(), list);
                    } else {
                        List<KrakenLedger> krakenLedgers = pairs.get(row.getRefId());
                        krakenLedgers.add(row);
                    }
                } catch (Exception e) {
                    parsingProblems.add(new ParsingProblem(row.toString(), e.getMessage(), ROW_PARSING_FAILED));
                }
            }
        );
        return pairs;
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

    private Currency translateKrakenCurrency(String currency) {
        try {
            return KrakenUtils.translateKrakenCurrencyCode(currency);
        } catch (Exception ignore) {
            throw new DataValidationException(EXCEPTION_CURRENCY);
        }
    }

    private io.everytrade.server.model.Currency switchKrakenAssetToCurrency(String krakenCurrency) {
        try {
            return io.everytrade.server.model.Currency.fromCode(translateKrakenCurrency(krakenCurrency).getCurrencyCode());
        } catch (Exception ignore) {
            throw new DataValidationException(EXCEPTION_CURRENCY);
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
        if (downloadedBlock.isEmpty()) {
            return downloadedBlock;
        }
        var lastDate = downloadedBlock.stream().map(it -> it.getDate().getTime()).max(Long::compare).get() + 1000;
        state.setStakeLastTimestamp(lastDate);
        return downloadedBlock;
    }

    public Set<Currency> getListOfAssets(final List<FundingRecord> fundings) {
        return fundings.stream().map(record -> record.getCurrency()).collect(Collectors.toSet());
    }

    public List<FundingRecord> depositAddresses(List<FundingRecord> recordsBlock, List<DepostitStatus> statuses) {
        final List<FundingRecord> recordsWithAddresses = new ArrayList<>();
        for (FundingRecord r : recordsBlock) {
            String internalId = r.getInternalId();
            var status = statuses.stream().filter(s -> (s.getRefid().equals(internalId))).findAny();
            String newAddress = !status.isEmpty() ? status.get().getInfo() : null;
            recordsWithAddresses.add(recordWithAddress(r, newAddress));
        }
        return recordsWithAddresses;
    }

    public List<FundingRecord> withdrawalAddresses(List<FundingRecord> recordsBlock, List<WithdrawStatus> statuses) {
        final List<FundingRecord> recordsWithAddresses = new ArrayList<>();
        for (FundingRecord r : recordsBlock) {
            String internalId = r.getInternalId();
            var status = statuses.stream().filter(s -> (s.getRefid().equals(internalId))).findAny();
            String newAddress = !status.isEmpty() ? status.get().getInfo() : null;
            recordsWithAddresses.add(recordWithAddress(r, newAddress));
        }
        return recordsWithAddresses;
    }

    private FundingRecord recordWithAddress(FundingRecord oldRecord, String address) {
        return new FundingRecord.Builder().from(oldRecord).setAddress(address).build();
    }

    private static String convertTimeFromUnix(double unix) {
        long secondsPart = (long) unix;
        int nanosPart = (int) ((unix - secondsPart) * 1_000_000_000);
        Instant instant = Instant.ofEpochSecond(secondsPart, nanosPart);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
            .withZone(ZoneId.systemDefault());
        return formatter.format(instant);
    }

    private static Date convertUnixToDate(double unixTime) {
        long secondsPart = (long) unixTime;
        int nanosPart = (int) ((unixTime - secondsPart) * 1_000_000_000);
        Instant instant = Instant.ofEpochSecond(secondsPart, nanosPart);
        return Date.from(instant);
    }

}
