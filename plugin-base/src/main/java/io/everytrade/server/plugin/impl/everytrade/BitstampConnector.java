package io.everytrade.server.plugin.impl.everytrade;

import io.everytrade.server.UiKey;
import io.everytrade.server.model.SupportedExchange;
import io.everytrade.server.plugin.api.IPlugin;
import io.everytrade.server.plugin.api.connector.ConnectorDescriptor;
import io.everytrade.server.plugin.api.connector.ConnectorParameterDescriptor;
import io.everytrade.server.plugin.api.connector.ConnectorParameterType;
import io.everytrade.server.plugin.api.connector.DownloadResult;
import io.everytrade.server.plugin.api.connector.IConnector;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.experimental.FieldDefaults;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.ExchangeFactory;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.bitstamp.BitstampExchange;
import org.knowm.xchange.bitstamp.service.BitstampTradeHistoryParams;
import org.knowm.xchange.dto.account.FundingRecord;
import org.knowm.xchange.dto.trade.UserTrade;
import org.knowm.xchange.service.account.AccountService;
import org.knowm.xchange.service.trade.TradeService;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static lombok.AccessLevel.PRIVATE;
import static org.apache.commons.lang3.StringUtils.isEmpty;

@AllArgsConstructor
@FieldDefaults(level = PRIVATE, makeFinal = true)
public class BitstampConnector implements IConnector {

    private static final String ID = EveryTradePlugin.ID + IPlugin.PLUGIN_PATH_SEPARATOR + "bitstampApiConnector";
    //8000 req per 10 min - https://www.bitstamp.net/api/
    private static final int MAX_REQUEST_COUNT = 100;
    private static final int TXS_PER_REQUEST = 1000;

    private static final ConnectorParameterDescriptor PARAMETER_API_USERNAME =
        new ConnectorParameterDescriptor(
            "username",
            ConnectorParameterType.STRING,
            UiKey.CONNECTION_USER_NAME,
            ""
        );

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
        "Bitstamp Connector",
        "",
        SupportedExchange.BITSTAMP.getInternalId(),
        List.of(PARAMETER_API_USERNAME, PARAMETER_API_KEY, PARAMETER_API_SECRET)
    );

    Exchange exchange;

    public BitstampConnector(Map<String, String> parameters) {
        this (
            parameters.get(PARAMETER_API_USERNAME.getId()),
            parameters.get(PARAMETER_API_KEY.getId()),
            parameters.get(PARAMETER_API_SECRET.getId())
        );
    }

    public BitstampConnector(@NonNull String username, @NonNull String apiKey, @NonNull String secret) {
        final ExchangeSpecification exSpec = new BitstampExchange().getDefaultExchangeSpecification();
        exSpec.setUserName(username);
        exSpec.setApiKey(apiKey);
        exSpec.setSecretKey(secret);
        this.exchange = ExchangeFactory.INSTANCE.createExchange(exSpec);
    }

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public DownloadResult getTransactions(String downloadState) {
        var state = DownloadState.deserialize(downloadState);
        List<UserTrade> userTrades = downloadTrades(state);
        List<FundingRecord> fundingRecords = downloadFunding(state);
        return new DownloadResult(new XChangeConnectorParser().getParseResult(userTrades, fundingRecords), state.serialize());
    }

    private List<UserTrade> downloadTrades(DownloadState downloadState) {
        TradeService tradeService = exchange.getTradeService();
        var params = (BitstampTradeHistoryParams) tradeService.createTradeHistoryParams();
        params.setStartId(downloadState.lastTxId);
        params.setPageLength(TXS_PER_REQUEST);
        String lastDownloadedTx = downloadState.lastTxId;

        final List<UserTrade> userTrades = new ArrayList<>();
        int sentRequests = 0;

        while (sentRequests < MAX_REQUEST_COUNT) {
            final List<UserTrade> userTradesBlock;
            try {
                userTradesBlock = tradeService.getTradeHistory(params).getUserTrades();
            } catch (IOException e) {
                throw new IllegalStateException("Download user trade history failed.", e);
            }

            if (
                !userTradesBlock.isEmpty()
                    && lastDownloadedTx != null
                    && lastDownloadedTx.equals(userTradesBlock.get(0).getId())
            ) {
                userTradesBlock.remove(0);
            }
            if (userTradesBlock.isEmpty()) {
                break;
            }
            userTrades.addAll(userTradesBlock);
            lastDownloadedTx = userTradesBlock.get(userTradesBlock.size() - 1).getId();
            params.setStartId(lastDownloadedTx);
            ++sentRequests;
        }
        downloadState.setLastTxId(lastDownloadedTx);
        return userTrades;
    }

    private List<FundingRecord> downloadFunding(DownloadState downloadState) {
        AccountService accountService = exchange.getAccountService();
        var params = (BitstampTradeHistoryParams) accountService.createFundingHistoryParams();
        params.setStartId(downloadState.lastTxId);
        params.setPageLength(TXS_PER_REQUEST);
        String lastDownloadedTx = downloadState.lastTxId;

        final List<FundingRecord> funding = new ArrayList<>();
        int sentRequests = 0;

        while (sentRequests < MAX_REQUEST_COUNT) {
            final List<FundingRecord> fundingBlock;
            try {
                fundingBlock = accountService.getFundingHistory(params);
            } catch (IOException e) {
                throw new IllegalStateException("Download user trade history failed.", e);
            }

            if (
                !fundingBlock.isEmpty()
                    && lastDownloadedTx != null
                    && lastDownloadedTx.equals(fundingBlock.get(0).getInternalId())
            ) {
                fundingBlock.remove(0);
            }
            if (fundingBlock.isEmpty()) {
                break;
            }
            funding.addAll(fundingBlock);
            lastDownloadedTx = fundingBlock.get(fundingBlock.size() - 1).getInternalId();
            params.setStartId(lastDownloadedTx);
            ++sentRequests;
        }
        downloadState.setLastTxId(lastDownloadedTx);
        return funding;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @FieldDefaults(level = PRIVATE)
    private static class DownloadState {
        private static final String SEPARATOR = "=";

        String lastTxId;
        String lastFundingId;

        public static DownloadState deserialize(String state) {
            if (isEmpty(state)) {
                return new DownloadState();
            }
            var strA = state.split(SEPARATOR);
            return new DownloadState(
                strA[0],
                strA.length > 1 ? strA[1] : null
            );
        }

        public String serialize() {
            return (lastTxId == null ? "" : lastTxId) + SEPARATOR + (lastFundingId == null ? "" : lastFundingId);
         }
    }
}
