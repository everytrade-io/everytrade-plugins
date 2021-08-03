package io.everytrade.server.plugin.impl.everytrade;

import com.okcoin.commons.okex.open.api.bean.spot.result.OrderInfo;
import com.okcoin.commons.okex.open.api.config.APIConfiguration;
import com.okcoin.commons.okex.open.api.service.spot.SpotOrderAPIServive;
import com.okcoin.commons.okex.open.api.service.spot.impl.SpotOrderApiServiceImpl;
import io.everytrade.server.UiKey;
import io.everytrade.server.model.SupportedExchange;
import io.everytrade.server.plugin.api.IPlugin;
import io.everytrade.server.plugin.api.connector.ConnectorDescriptor;
import io.everytrade.server.plugin.api.connector.ConnectorParameterDescriptor;
import io.everytrade.server.plugin.api.connector.ConnectorParameterType;
import io.everytrade.server.plugin.api.connector.DownloadResult;
import io.everytrade.server.plugin.api.connector.IConnector;
import io.everytrade.server.plugin.api.parser.ParseResult;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public class OkexConnector implements IConnector {
    private static final String ID = EveryTradePlugin.ID + IPlugin.PLUGIN_PATH_SEPARATOR + "okexApiConnector";

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

    private static final ConnectorParameterDescriptor PARAMETER_PASS_PHRASE =
        new ConnectorParameterDescriptor(
            "passPhrase",
            ConnectorParameterType.SECRET,
            UiKey.CONNECTION_PASSPHRASE,
            ""
        );
    private static final ConnectorParameterDescriptor PARAMETER_CURRENCY_PAIRS =
        new ConnectorParameterDescriptor(
            "currencyPairs",
            ConnectorParameterType.STRING,
            UiKey.CONNECTION_CURRENCY_PAIRS_DESC,
            ""
        );

    public static final ConnectorDescriptor DESCRIPTOR = new ConnectorDescriptor(
        ID,
        "OKEX Connector",
        "",
        SupportedExchange.OKEX.getInternalId(),
        List.of(PARAMETER_API_KEY, PARAMETER_API_SECRET, PARAMETER_PASS_PHRASE, PARAMETER_CURRENCY_PAIRS)
    );
    private static final String OKEX_URL = "https://www.okex.com/";
    private final String apiKey;
    private final String apiSecret;
    private final String passPhrase;
    private final String currencyPairs;

    public OkexConnector(Map<String, String> parameters) {
        Objects.requireNonNull(this.apiKey = parameters.get(PARAMETER_API_KEY.getId()));
        Objects.requireNonNull(this.apiSecret = parameters.get(PARAMETER_API_SECRET.getId()));
        Objects.requireNonNull(this.passPhrase = parameters.get(PARAMETER_PASS_PHRASE.getId()));
        Objects.requireNonNull(this.currencyPairs = parameters.get(PARAMETER_CURRENCY_PAIRS.getId()));
    }

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public DownloadResult getTransactions(String lastTransactionId) {
        APIConfiguration config = new APIConfiguration();
        config.setEndpoint(OKEX_URL);
        config.setApiKey(apiKey);
        config.setSecretKey(apiSecret);
        config.setPassphrase(passPhrase);
        final SpotOrderAPIServive spotOrderApiService = new SpotOrderApiServiceImpl(config);
        final OkexDownloader okexDownloader = new OkexDownloader(spotOrderApiService, lastTransactionId);
        final List<OrderInfo> orderInfos = okexDownloader.download(currencyPairs);
        final ParseResult parseResult = OkexConnectorParser.getParseResult(orderInfos);
        return new DownloadResult(parseResult, okexDownloader.getLastTransactionId());
    }

    @Override
    public void close() {
        //AutoCloseable
    }
}