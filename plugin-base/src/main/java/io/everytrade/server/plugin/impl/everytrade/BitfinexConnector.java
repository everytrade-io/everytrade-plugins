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
import lombok.experimental.FieldDefaults;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.ExchangeFactory;
import org.knowm.xchange.bitfinex.BitfinexExchange;
import org.knowm.xchange.bitfinex.service.BitfinexAccountService;
import org.knowm.xchange.bitfinex.service.BitfinexTradeService;
import org.knowm.xchange.dto.account.FundingRecord;
import org.knowm.xchange.dto.trade.UserTrade;
import org.knowm.xchange.service.account.AccountService;
import org.knowm.xchange.service.trade.TradeService;
import org.knowm.xchange.service.trade.params.TradeHistoryParamsSorted;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static io.everytrade.server.plugin.impl.everytrade.ConnectorUtils.findDuplicateFunding;
import static io.everytrade.server.plugin.impl.everytrade.ConnectorUtils.findDuplicateTransaction;
import static io.everytrade.server.plugin.impl.everytrade.ConnectorUtils.occurrenceCount;
import static lombok.AccessLevel.PRIVATE;
import static org.springframework.util.StringUtils.isEmpty;

@AllArgsConstructor
@FieldDefaults(makeFinal = true, level = PRIVATE)
public class BitfinexConnector implements IConnector {

    private static final String ID = EveryTradePlugin.ID + IPlugin.PLUGIN_PATH_SEPARATOR + "bitfinexApiConnector";
    //https://docs.bitfinex.com/reference#rest-public-trades - 30 request / 1 minute, than 60 s no resp.
    private static final int MAX_REQUEST_COUNT = 5;
    private static final int TX_PER_REQUEST = 1000;

    private static final ConnectorParameterDescriptor PARAMETER_API_SECRET =
        new ConnectorParameterDescriptor(
            "apiSecret",
            ConnectorParameterType.SECRET,
            "API Secret",
            ""
        );

    private static final ConnectorParameterDescriptor PARAMETER_API_KEY =
        new ConnectorParameterDescriptor(
            "apiKey",
            ConnectorParameterType.STRING,
            "API Key",
            ""
        );

    public static final ConnectorDescriptor DESCRIPTOR = new ConnectorDescriptor(
        ID,
        "Bitfinex Connector",
        "",
        SupportedExchange.BITFINEX.getInternalId(),
        List.of(PARAMETER_API_KEY, PARAMETER_API_SECRET)
    );

    Exchange exchange;

    public BitfinexConnector(Map<String, String> parameters) {
        var spec = new BitfinexExchange().getDefaultExchangeSpecification();
        spec.setApiKey(parameters.get(PARAMETER_API_KEY.getId()));
        spec.setSecretKey(parameters.get(PARAMETER_API_SECRET.getId()));
        this.exchange = ExchangeFactory.INSTANCE.createExchange(spec);
    }

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public DownloadResult getTransactions(String lastDownloadState) {
        var downloadState = DownloadState.deserialize(lastDownloadState);

        List<UserTrade> userTrades = downloadTrades(downloadState);
        List<FundingRecord> funding = downloadFunding(downloadState);
        return new DownloadResult(new XChangeConnectorParser().getParseResult(userTrades, funding), downloadState.serialize());
    }

    private List<UserTrade> downloadTrades(DownloadState downloadState) {
        TradeService tradeService = exchange.getTradeService();
        var tradeHistoryParams = (BitfinexTradeService.BitfinexTradeHistoryParams) tradeService.createTradeHistoryParams();
        tradeHistoryParams.setLimit(TX_PER_REQUEST);
        tradeHistoryParams.setOrder(TradeHistoryParamsSorted.Order.asc);

        final List<UserTrade> userTrades = new ArrayList<>();

        int sentRequests = 0;
        while (sentRequests < MAX_REQUEST_COUNT) {
            tradeHistoryParams.setStartTime(downloadState.lastTxId.date);
            final List<UserTrade> userTradesBlock;
            try {
                userTradesBlock = tradeService.getTradeHistory(tradeHistoryParams).getUserTrades();
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
            downloadState.setLastTxId(new TransactionIdentifier(userTradeLast.getTimestamp(), userTradeLast.getId()));

            userTrades.addAll(userTradesToAdd);
            ++sentRequests;
        }
        return userTrades;
    }

    private List<FundingRecord> downloadFunding(DownloadState downloadState) {
        AccountService accountService = exchange.getAccountService();
        var params = (BitfinexAccountService.BitfinexFundingHistoryParams) accountService.createFundingHistoryParams();
        params.setLimit(TX_PER_REQUEST);

        final List<FundingRecord> fundingRecords = new ArrayList<>();

        int sentRequests = 0;
        while (sentRequests < MAX_REQUEST_COUNT) {
            params.setStartTime(downloadState.lastTxId.date);
            final List<FundingRecord> fundingBlock;
            try {
                fundingBlock = accountService.getFundingHistory(params);
            } catch (IOException e) {
                throw new IllegalStateException("User trade history download failed. ", e);
            }
            final List<FundingRecord> fundingToAdd;
            final int duplicateTxIndex = findDuplicateFunding(downloadState.lastTxId.id, fundingBlock);
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
            downloadState.setLastFundingId(new TransactionIdentifier(lastFunding.getDate(), lastFunding.getInternalId()));

            fundingRecords.addAll(fundingToAdd);
            ++sentRequests;
        }
        return fundingRecords;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @FieldDefaults(level = PRIVATE)
    private static class TransactionIdentifier {
        private static final String TX_SPLITER = "|";
        private static final Pattern SPLIT_PATTERN = Pattern.compile(String.format("(.*)\\%s(.*)", TX_SPLITER));

        Date date;
        String id;

        public String serialize() {
            return String.format("%s%s%s", date.toInstant().toString(), TX_SPLITER, id);
        }

        public static TransactionIdentifier parseFrom(String lastTransactionUid) {
            if (lastTransactionUid == null) {
                return new TransactionIdentifier(Date.from(Instant.EPOCH), null);
            }
            Matcher matcher = SPLIT_PATTERN.matcher(lastTransactionUid);
            if (occurrenceCount(lastTransactionUid, TX_SPLITER) != 1 || !matcher.find()) {
                throw new IllegalArgumentException(
                    String.format("Illegal value of lastTransactionUid '%s'.", lastTransactionUid)
                );
            }
            final String date = matcher.group(1);
            try {
                return new TransactionIdentifier(
                    Date.from(Instant.parse(date)),
                    matcher.group(2)
                );
            } catch (Exception e) {
                throw new IllegalArgumentException(
                    String.format(
                        "Illegal value of date part '%s' of lastTransactionUid '%s'.",
                        date,
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

        @Builder.Default
        TransactionIdentifier lastTxId = new TransactionIdentifier();
        @Builder.Default
        TransactionIdentifier lastFundingId = new TransactionIdentifier();

        public static DownloadState deserialize(String state) {
            if (isEmpty(state)) {
                return new DownloadState();
            }
            var strArray = state.split(SEPARATOR);
            return DownloadState.builder()
                .lastTxId(TransactionIdentifier.parseFrom(strArray[0]))
                .lastFundingId(strArray.length > 1 ? TransactionIdentifier.parseFrom(strArray[1]) : new TransactionIdentifier())
                .build();
        }

        public String serialize() {
            return
                (lastTxId == null ? "" : lastTxId.serialize()) +
                    SEPARATOR +
                    (lastFundingId == null ? "" : lastFundingId.serialize());
        }
    }
}