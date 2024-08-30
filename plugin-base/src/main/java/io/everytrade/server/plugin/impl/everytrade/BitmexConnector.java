package io.everytrade.server.plugin.impl.everytrade;

import io.everytrade.server.model.SupportedExchange;
import io.everytrade.server.plugin.api.IPlugin;
import io.everytrade.server.plugin.api.connector.ConnectorDescriptor;
import io.everytrade.server.plugin.api.connector.ConnectorParameterDescriptor;
import io.everytrade.server.plugin.api.connector.ConnectorParameterType;
import io.everytrade.server.plugin.api.connector.DownloadResult;
import io.everytrade.server.plugin.api.connector.IConnector;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.ExchangeFactory;
import org.knowm.xchange.bitmex.BitmexExchange;
import org.knowm.xchange.bitmex.service.BitmexTradeHistoryParams;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.dto.account.FundingRecord;
import org.knowm.xchange.dto.trade.UserTrade;
import org.knowm.xchange.service.account.AccountService;
import org.knowm.xchange.service.trade.TradeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static io.everytrade.server.plugin.impl.everytrade.ConnectorUtils.findDuplicateFunding;
import static io.everytrade.server.plugin.impl.everytrade.ConnectorUtils.findDuplicateTransaction;
import static java.util.stream.Collectors.joining;
import static lombok.AccessLevel.PRIVATE;
import static org.apache.commons.lang3.StringUtils.isEmpty;

@RequiredArgsConstructor
@FieldDefaults(level = PRIVATE, makeFinal = true)
public class BitmexConnector implements IConnector {
    private static final Logger LOG = LoggerFactory.getLogger(BitmexConnector.class);

    private static final String ID = WhaleBooksPlugin.ID + IPlugin.PLUGIN_PATH_SEPARATOR + "bitmexApiConnector";
    //https://www.bitmex.com/app/restAPI#Limits - max 60 requests per minute
    //30 --> 50% of user budget for one API connector instance
    private static final int MAX_REQUESTS = 30;
    private static final int MAX_TXS_PER_REQUEST = 500;
    private static final String LAST_TX_ID_FORMAT = "%s:%s";

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
        "BitMEX Connector",
        "",
        SupportedExchange.BITMEX.getInternalId(),
        List.of(PARAMETER_API_KEY, PARAMETER_API_SECRET)
    );


    Exchange exchange;

    public BitmexConnector(Map<String, String> parameters) {
        var exSpec = new BitmexExchange().getDefaultExchangeSpecification();
        exSpec.setApiKey(parameters.get(PARAMETER_API_KEY.getId()));
        exSpec.setSecretKey(parameters.get(PARAMETER_API_SECRET.getId()));
        this.exchange = ExchangeFactory.INSTANCE.createExchange(exSpec);
    }

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public DownloadResult getTransactions(String downloadStateStr) {
        var downloadState= DownloadState.deserialize(downloadStateStr);

        List<UserTrade> userTrades = download(downloadState);
        List<FundingRecord> funding = downloadFunding(downloadState);

        return new DownloadResult(new XChangeConnectorParser().getParseResult(userTrades, funding), downloadState.serialize());
    }

    private List<UserTrade> download(DownloadState downloadState) {
        TradeService tradeService = exchange.getTradeService();
        var params = (BitmexTradeHistoryParams) tradeService.createTradeHistoryParams();
        params.setLimit(MAX_TXS_PER_REQUEST);
        final List<UserTrade> userTrades = new ArrayList<>();
        int sentRequests = 0;

        while (sentRequests < MAX_REQUESTS) {
            params.setOffset(downloadState.lastTxId.offset);
            final List<UserTrade> userTradesBlock;
            try {
                userTradesBlock = tradeService.getTradeHistory(params).getUserTrades();
            } catch (IOException e) {
                throw new IllegalStateException("User trade history download failed. ", e);
            }
            final List<UserTrade> userTradesToAdd;
            final int duplicateTxIndex = findDuplicateTransaction(downloadState.lastTxId.id, userTradesBlock);
            if (duplicateTxIndex > -1) {
                if (duplicateTxIndex < userTradesBlock.size() - 1) {
                    userTradesToAdd = userTradesBlock.subList(duplicateTxIndex + 1, userTradesBlock.size());
                } else {
                    userTradesToAdd = List.of();
                }
            } else {
                userTradesToAdd = userTradesBlock;
            }

            if (userTradesToAdd.isEmpty()) {
                break;
            }

            final UserTrade userTradeLast = userTradesToAdd.get(userTradesToAdd.size() - 1);
            final long actualOffset = downloadState.lastTxId.offset + userTradesBlock.size();
            downloadState.setLastTxId(new TransactionIdentifier(actualOffset, userTradeLast.getId()));

            userTrades.addAll(userTradesToAdd);
            ++sentRequests;
        }

        return userTrades;
    }

    private List<FundingRecord> downloadFunding(DownloadState downloadState) {
        AccountService accountService = exchange.getAccountService();
        var params = (BitmexTradeHistoryParams) accountService.createFundingHistoryParams();
        params.setLimit(MAX_TXS_PER_REQUEST);

        Set<Currency> currencies = exchange.getExchangeMetaData().getCurrencies().keySet();
        final List<FundingRecord> funding = new ArrayList<>();
        int sentRequests = 0;

        for (Currency currency : currencies) {
            params.setCurrency(currency);
            TransactionIdentifier lastFundingId = downloadState.getFundingForCurrency(currency);
            while (sentRequests < MAX_REQUESTS) {
                params.setOffset(lastFundingId.offset);
                final List<FundingRecord> fundingBlock;
                try {
                    fundingBlock = accountService.getFundingHistory(params);
                } catch (IOException e) {
                    throw new IllegalStateException("User funding history download failed. ", e);
                }
                final List<FundingRecord> fundingToAdd;
                final int duplicateTxIndex = findDuplicateFunding(lastFundingId.id, fundingBlock);
                if (duplicateTxIndex > -1) {
                    if (duplicateTxIndex < fundingBlock.size() - 1) {
                        fundingToAdd = fundingBlock.subList(duplicateTxIndex + 1, fundingBlock.size());
                    } else {
                        fundingToAdd = List.of();
                    }
                } else {
                    fundingToAdd = fundingBlock;
                }

                if (fundingToAdd.isEmpty()) {
                    break;
                }

                final FundingRecord lastFunding = fundingToAdd.get(fundingToAdd.size() - 1);
                final long actualOffset = downloadState.lastTxId.offset + fundingBlock.size();
                downloadState.setLastFundingId(currency, new TransactionIdentifier(actualOffset, lastFunding.getInternalId()));

                funding.addAll(fundingToAdd);
                ++sentRequests;
            }
        }

        return funding;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @FieldDefaults(level = PRIVATE)
    private static class TransactionIdentifier {
        private static final String TX_SPLITER = "|";
        private static final Pattern SPLIT_PATTERN = Pattern.compile(String.format("(.*)\\%s(.*)", TX_SPLITER));

        long offset;
        String id;

        public String serialize() {
            return String.format("%s%s%s", offset, TX_SPLITER, id);
        }

        public static TransactionIdentifier parseFrom(String lastTransactionUid) {
            if (lastTransactionUid == null) {
                return new TransactionIdentifier(0L, null);
            }
            Matcher matcher = SPLIT_PATTERN.matcher(lastTransactionUid);
            if (!matcher.find()) {
                throw new IllegalArgumentException(
                    String.format("Illegal value of state '%s'.", lastTransactionUid)
                );
            }
            final String offset = matcher.group(1);
            try {
                return new TransactionIdentifier(Long.parseLong(offset), matcher.group(2));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(
                    String.format(
                        "Illegal value of offset part '%s' of lastTransactionUid '%s'.",
                        offset,
                        lastTransactionUid
                    ),
                    e
                );
            }
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @FieldDefaults(level = PRIVATE)
    private static class DownloadState {
        private static final String SEPARATOR = "=";
        private static final String FUNDING_KEY_VAL_SEPARATOR = ":";
        private static final String FUNDING_ENTRY_SEPARATOR = ";";

        @Builder.Default
        TransactionIdentifier lastTxId = new TransactionIdentifier();
        @Builder.Default
        Map<Currency,TransactionIdentifier> lastFundingIds = new HashMap<>();

        public TransactionIdentifier getFundingForCurrency(Currency c) {
            return lastFundingIds.getOrDefault(c, new TransactionIdentifier());
        }

        public void setLastFundingId(Currency currency, TransactionIdentifier transactionIdentifier) {
            lastFundingIds.put(currency, transactionIdentifier);
        }

        public static DownloadState deserialize(String state) {
            if (isEmpty(state)) {
                return new DownloadState();
            }
            var strArray = state.split(SEPARATOR);
            return DownloadState.builder()
                .lastTxId(TransactionIdentifier.parseFrom(strArray[0]))
                .lastFundingIds(strArray.length > 1 ? deserializeFundingMap(strArray[1]) : new HashMap<>())
                .build();
        }

        public String serialize() {
            return (lastTxId == null ? "" : lastTxId.serialize()) + SEPARATOR + serializeFundingMap();
        }

        private String serializeFundingMap() {
            return lastFundingIds.entrySet().stream()
                .map(e -> e.getKey().toString() + FUNDING_KEY_VAL_SEPARATOR + e.getValue().serialize())
                .collect(joining(FUNDING_ENTRY_SEPARATOR));
        }

        private static Map<Currency, TransactionIdentifier> deserializeFundingMap(String s) {
            if (isEmpty(s)) {
                return new HashMap<>();
            }
            var records = s.split(FUNDING_ENTRY_SEPARATOR);
            return Arrays.stream(records)
                .map(r -> {
                    var entryArray = r.split(FUNDING_KEY_VAL_SEPARATOR);
                    if (r.length() < 2) {
                        LOG.warn("Wrong parameter cannot be deserialized: {}", r);
                        return null;
                    }
                    return Map.entry(new Currency(entryArray[0]), TransactionIdentifier.parseFrom(entryArray[1]));
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e1));
        }
    }
}
