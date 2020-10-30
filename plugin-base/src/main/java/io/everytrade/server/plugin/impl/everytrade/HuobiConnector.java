package io.everytrade.server.plugin.impl.everytrade;

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
import org.knowm.xchange.dto.trade.UserTrade;
import org.knowm.xchange.huobi.HuobiExchange;
import org.knowm.xchange.service.trade.TradeService;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public class HuobiConnector implements IConnector {

    private static final String ID = EveryTradePlugin.ID + IPlugin.PLUGIN_PATH_SEPARATOR + "huobiApiConnector";

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

    private static final ConnectorParameterDescriptor PARAMETER_CURRENCY_PAIRS =
        new ConnectorParameterDescriptor(
            "currencyPairs",
            ConnectorParameterType.STRING,
            "Trade currency pairs (e.g. LTC/USDT,LTC/BTC)",
            ""
        );

    public static final ConnectorDescriptor DESCRIPTOR = new ConnectorDescriptor(
        ID,
        "Huobi Connector",
        "Only order-limit is supported.",
        SupportedExchange.HUOBI.getInternalId(),
        List.of(PARAMETER_API_KEY, PARAMETER_API_SECRET, PARAMETER_CURRENCY_PAIRS)
    );

    private final String apiKey;
    private final String apiSecret;
    private final String currencyPairs;

    public HuobiConnector(Map<String, String> parameters) {
        Objects.requireNonNull(this.apiKey = parameters.get(PARAMETER_API_KEY.getId()));
        Objects.requireNonNull(this.apiSecret = parameters.get(PARAMETER_API_SECRET.getId()));
        Objects.requireNonNull(this.currencyPairs = parameters.get(PARAMETER_CURRENCY_PAIRS.getId()));
    }

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public DownloadResult getTransactions(String lastTransactionId) {
        final ExchangeSpecification exSpec = new HuobiExchange().getDefaultExchangeSpecification();
        exSpec.setApiKey(apiKey);
        exSpec.setSecretKey(apiSecret);
        final Exchange exchange = ExchangeFactory.INSTANCE.createExchange(exSpec);
        final TradeService tradeService = exchange.getTradeService();
        final HuobiDownloader huobiDownloader = new HuobiDownloader(tradeService, lastTransactionId);

        List<UserTrade> userTrades = huobiDownloader.download(currencyPairs);
        final ParseResult parseResult = XChangeConnectorParser.getParseResult(userTrades, SupportedExchange.BINANCE);

        return new DownloadResult(parseResult, huobiDownloader.getLastTransactionId());
    }

    @Override
    public void close() {
        //AutoCloseable
    }
}
