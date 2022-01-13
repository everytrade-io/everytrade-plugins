package io.everytrade.server.plugin.impl.everytrade;

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
import org.knowm.xchange.coinmate.CoinmateExchange;
import org.knowm.xchange.coinmate.service.CoinmateAccountService;
import org.knowm.xchange.coinmate.service.CoinmateTradeService;
import org.knowm.xchange.dto.account.FundingRecord;
import org.knowm.xchange.dto.trade.UserTrade;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static lombok.AccessLevel.PRIVATE;
import static org.apache.commons.lang3.StringUtils.isEmpty;

@AllArgsConstructor
@FieldDefaults(makeFinal = true, level = PRIVATE)
public class CoinmateConnector implements IConnector {

    private static final String ID = EveryTradePlugin.ID + IPlugin.PLUGIN_PATH_SEPARATOR + "coinmateApiConnector";
    // MAX 100 request per minute per user, https://coinmate.docs.apiary.io/#reference/request-limits
    private static final int MAX_REQUEST_COUNT = 5;
    // https://coinmate.docs.apiary.io/#reference/transaction-history/get-transaction-history
    private static final int TX_PER_REQUEST = 1000;

    private static final ConnectorParameterDescriptor PARAMETER_API_USERNAME =
        new ConnectorParameterDescriptor(
            "username",
            ConnectorParameterType.STRING,
            "Client ID",
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
        "Coinmate Connector",
        "",
        SupportedExchange.COINMATE.getInternalId(),
        List.of(PARAMETER_API_USERNAME, PARAMETER_API_KEY, PARAMETER_API_SECRET)
    );

    Exchange exchange;

    public CoinmateConnector(Map<String, String> parameters) {
        this(
            parameters.get(PARAMETER_API_USERNAME.getId()),
            parameters.get(PARAMETER_API_KEY.getId()),
            parameters.get(PARAMETER_API_SECRET.getId())
        );
    }

    public CoinmateConnector(@NonNull String username, @NonNull String apiKey, @NonNull String secret) {
        final ExchangeSpecification exSpec = new CoinmateExchange().getDefaultExchangeSpecification();
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
    public DownloadResult getTransactions(String stateStr) {
        var downloadState = DownloadState.deserialize(stateStr);

        List<UserTrade> userTrades = downloadTrades(downloadState);
        List<FundingRecord> fundingRecords = downloadFunding(downloadState);

        return new DownloadResult(new XChangeConnectorParser().getParseResult(userTrades, fundingRecords), downloadState.serialize());
    }

    private List<UserTrade> downloadTrades(DownloadState state) {
        var tradeService = exchange.getTradeService();
        var params = (CoinmateTradeService.CoinmateTradeHistoryHistoryParams) tradeService.createTradeHistoryParams();
        params.setStartId(state.lastTxId);
        params.setLimit(TX_PER_REQUEST);

        final List<UserTrade> userTrades = new ArrayList<>();
        int sentRequests = 0;
        while (sentRequests < MAX_REQUEST_COUNT) {
            final List<UserTrade> userTradesBlock;
            try {
                userTradesBlock = tradeService.getTradeHistory(params).getUserTrades();
            } catch (IOException e) {
                throw new IllegalStateException("Download user trade history failed.", e);
            }
            if (userTradesBlock.isEmpty()) {
                break;
            }
            userTrades.addAll(userTradesBlock);
            final String lastDownloadedTx = userTradesBlock.get(userTradesBlock.size() - 1).getId();
            params.setStartId(lastDownloadedTx);
            state.setLastTxId(lastDownloadedTx);
            ++sentRequests;
        }
        return userTrades;
    }

    private List<FundingRecord> downloadFunding(DownloadState state) {
        var accountService = exchange.getAccountService();
        var params = (CoinmateAccountService.CoinmateFundingHistoryParams) accountService.createFundingHistoryParams();
        if (state.getFundingsFrom() != null) {
            params.setStartTime(new Date(state.getFundingsFrom()));
        }
        params.setLimit(TX_PER_REQUEST);

        final List<FundingRecord> funding = new ArrayList<>();
        int sentRequests = 0;
        while (sentRequests < MAX_REQUEST_COUNT) {
            final List<FundingRecord> fundingBlock;
            try {
                fundingBlock = accountService.getFundingHistory(params);
            } catch (IOException e) {
                throw new IllegalStateException("Download user funding history failed.", e);
            }
            if (fundingBlock.isEmpty()) {
                break;
            }
            funding.addAll(fundingBlock);

            var newStartFromMillis = fundingBlock.get(fundingBlock.size() - 1).getDate().getTime() + 1;
            params.setStartTime(new Date(newStartFromMillis));
            state.setFundingsFrom(newStartFromMillis);
            ++sentRequests;
        }
        return funding;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @FieldDefaults(level = PRIVATE)
    private static class DownloadState {
        private static final String SEPARATOR = "=";

        String lastTxId;
        Long fundingsFrom = 0L;

        public static DownloadState deserialize(String state) {
            if (isEmpty(state)) {
                return new DownloadState();
            }
            var strA = state.split(SEPARATOR);
            return new DownloadState(
                strA[0],
                strA.length >1 ? Long.parseLong(strA[1]) : 0
            );
        }

        public String serialize() {
            return (lastTxId == null ? "" : lastTxId) + SEPARATOR + (fundingsFrom == null ? "" : fundingsFrom);
        }
    }
}
