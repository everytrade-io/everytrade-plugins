package io.everytrade.server.plugin.impl.everytrade;

import io.everytrade.server.UiKey;
import io.everytrade.server.model.SupportedExchange;
import io.everytrade.server.plugin.api.IPlugin;
import io.everytrade.server.plugin.api.connector.ConnectorDescriptor;
import io.everytrade.server.plugin.api.connector.ConnectorParameterDescriptor;
import io.everytrade.server.plugin.api.connector.ConnectorParameterType;
import io.everytrade.server.plugin.api.connector.DownloadResult;
import io.everytrade.server.plugin.api.connector.IConnector;
import io.everytrade.server.plugin.api.parser.ParseResult;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.ExchangeFactory;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.bitstamp.BitstampExchange;
import org.knowm.xchange.bitstamp.service.BitstampTradeHistoryParams;
import org.knowm.xchange.dto.trade.UserTrade;
import org.knowm.xchange.service.trade.TradeService;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static java.util.Collections.emptyList;

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

    private final String apiUsername;
    private final String apiKey;
    private final String apiSecret;

    public BitstampConnector(Map<String, String> parameters) {
        Objects.requireNonNull(this.apiUsername = parameters.get(PARAMETER_API_USERNAME.getId()));
        Objects.requireNonNull(this.apiKey = parameters.get(PARAMETER_API_KEY.getId()));
        Objects.requireNonNull(this.apiSecret = parameters.get(PARAMETER_API_SECRET.getId()));
    }

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public DownloadResult getTransactions(String lastTransactionId) {
        final ExchangeSpecification exSpec = new BitstampExchange().getDefaultExchangeSpecification();
        exSpec.setUserName(apiUsername);
        exSpec.setApiKey(apiKey);
        exSpec.setSecretKey(apiSecret);
        final Exchange exchange = ExchangeFactory.INSTANCE.createExchange(exSpec);
        final TradeService tradeService = exchange.getTradeService();

        final List<UserTrade> userTrades = download(lastTransactionId, tradeService);

        final String lastTransactionIdNew = userTrades.isEmpty()
            ? lastTransactionId
            : userTrades.get(userTrades.size() - 1).getId();

        final ParseResult parseResult = new XChangeConnectorParser().getParseResult(userTrades, emptyList());

        return new DownloadResult(parseResult, lastTransactionIdNew);
    }

    private List<UserTrade> download(String lastTransactionId, TradeService tradeService) {
        final BitstampTradeHistoryParams bitstampTradeHistoryParams =
            (BitstampTradeHistoryParams) tradeService.createTradeHistoryParams();
        bitstampTradeHistoryParams.setStartId(lastTransactionId);
        bitstampTradeHistoryParams.setPageLength(TXS_PER_REQUEST);
        String lastDownloadedTx = lastTransactionId;
        final List<UserTrade> userTrades = new ArrayList<>();
        int sentRequests = 0;

        while (sentRequests < MAX_REQUEST_COUNT) {
            final List<UserTrade> userTradesBlock;
            try {
                userTradesBlock = tradeService.getTradeHistory(bitstampTradeHistoryParams).getUserTrades();
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
            bitstampTradeHistoryParams.setStartId(lastDownloadedTx);
            ++sentRequests;
        }
        return userTrades;
    }

    @Override
    public void close() {
        //AutoCloseable
    }
}
