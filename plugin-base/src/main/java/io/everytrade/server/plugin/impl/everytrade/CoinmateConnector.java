package io.everytrade.server.plugin.impl.everytrade;

import io.everytrade.server.plugin.api.connector.ConnectorParameterType;
import io.everytrade.server.model.SupportedExchange;
import io.everytrade.server.plugin.api.parser.ParseResult;
import io.everytrade.server.plugin.api.connector.DownloadResult;
import io.everytrade.server.plugin.api.IPlugin;
import io.everytrade.server.plugin.api.connector.ConnectorDescriptor;
import io.everytrade.server.plugin.api.connector.ConnectorParameterDescriptor;
import io.everytrade.server.plugin.api.connector.IConnector;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.ExchangeFactory;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.coinmate.CoinmateExchange;
import org.knowm.xchange.coinmate.service.CoinmateTradeService;
import org.knowm.xchange.dto.trade.UserTrade;
import org.knowm.xchange.service.trade.TradeService;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class CoinmateConnector implements IConnector {

    private static final String ID = EveryTradePlugin.ID + IPlugin.PLUGIN_PATH_SEPARATOR + "coinmateApiConnector";
    // MAX 100 request per minute per user, https://coinmate.docs.apiary.io/#reference/request-limits
    private static final int MAX_REQUEST_COUNT = 50;
    // https://coinmate.docs.apiary.io/#reference/transaction-history/get-transaction-history
    private static final int TX_PER_REQUEST = 1000;

    private static final ConnectorParameterDescriptor PARAMETER_API_USERNAME =
        new ConnectorParameterDescriptor(
            "username",
            ConnectorParameterType.STRING,
            "User Name",
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
        SupportedExchange.COINMATE.getInternalId(),
        List.of(PARAMETER_API_USERNAME, PARAMETER_API_KEY, PARAMETER_API_SECRET)
    );

    private final String apiUsername;
    private final String apiKey;
    private final String apiSecret;

    public CoinmateConnector(Map<String, String> parameters) {
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
        final ExchangeSpecification exSpec = new CoinmateExchange().getDefaultExchangeSpecification();
        exSpec.setUserName(apiUsername);
        exSpec.setApiKey(apiKey);
        exSpec.setSecretKey(apiSecret);
        final Exchange exchange = ExchangeFactory.INSTANCE.createExchange(exSpec);
        final TradeService tradeService = exchange.getTradeService();

        List<UserTrade> userTrades = download(lastTransactionId, tradeService);

        final String actualLastTransactionId = userTrades.isEmpty()
            ? lastTransactionId
            : userTrades.get(userTrades.size() - 1).getId();

        final ParseResult parseResult = XChangeConnectorParser.getParseResult(userTrades, SupportedExchange.COINMATE);

        return new DownloadResult(parseResult, actualLastTransactionId);
    }

    private List<UserTrade> download(String lastTransactionId, TradeService tradeService) {
        final CoinmateTradeService.CoinmateTradeHistoryHistoryParams tradeHistoryParams
            = (CoinmateTradeService.CoinmateTradeHistoryHistoryParams) tradeService.createTradeHistoryParams();
        tradeHistoryParams.setStartId(lastTransactionId);
        tradeHistoryParams.setLimit(TX_PER_REQUEST);

        final List<UserTrade> userTrades = new ArrayList<>();
        int counter = 0;
        while (counter++ < MAX_REQUEST_COUNT) {
            final List<UserTrade> userTradesBlock;
            try {
                userTradesBlock = tradeService.getTradeHistory(tradeHistoryParams).getUserTrades();
            } catch (IOException e) {
                throw new IllegalStateException("Download user trade history failed.", e);
            }
            if (userTradesBlock.isEmpty()) {
                break;
            }
            userTrades.addAll(userTradesBlock);
            final String lastDownloadedTx = userTradesBlock.get(userTradesBlock.size() - 1).getId();
            tradeHistoryParams.setStartId(lastDownloadedTx);
        }
        return userTrades;
    }

    @Override
    public void close() {
        //AutoCloseable
    }

}
