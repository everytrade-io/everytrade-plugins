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
import lombok.NonNull;
import lombok.experimental.FieldDefaults;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.ExchangeFactory;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.dto.account.FundingRecord;
import org.knowm.xchange.dto.trade.UserTrade;
import org.knowm.xchange.huobi.HuobiExchange;

import java.util.List;
import java.util.Map;

import static lombok.AccessLevel.PRIVATE;

@AllArgsConstructor
@FieldDefaults(makeFinal = true, level = PRIVATE)
public class HuobiConnector implements IConnector {

    private static final String ID = WhaleBooksPlugin.ID + IPlugin.PLUGIN_PATH_SEPARATOR + "huobiApiConnector";

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

    private static final ConnectorParameterDescriptor PARAMETER_CURRENCY_PAIRS =
        new ConnectorParameterDescriptor(
            "currencyPairs",
            ConnectorParameterType.STRING,
            UiKey.CONNECTION_CURRENCY_PAIRS_DESC,
            "",
            false
        );

    private static final ConnectorParameterDescriptor PARAMETER_PAIR_SETTINGS =
        new ConnectorParameterDescriptor(
            "pairSettings",
            ConnectorParameterType.BOOLEAN,
            UiKey.CONNECTION_CURRENCY_PAIRS_SETTINGS,
            "",
            true
        );

    public static final ConnectorDescriptor DESCRIPTOR = new ConnectorDescriptor(
        ID,
        "Huobi Connector",
        "",
        SupportedExchange.HUOBI.getInternalId(),
        List.of(
            PARAMETER_API_KEY,
            PARAMETER_API_SECRET,
            PARAMETER_PAIR_SETTINGS,
            PARAMETER_CURRENCY_PAIRS)
    );

    Exchange exchange;
    String currencyPairs;
    boolean pairSettings;

    public HuobiConnector(Exchange exchange, String currencyPairs) {
        this(exchange, currencyPairs, false);
    }

    public HuobiConnector(Map<String, String> parameters) {
        this(
            parameters.get(PARAMETER_API_KEY.getId()),
            parameters.get(PARAMETER_API_SECRET.getId()),
            parameters.get(PARAMETER_CURRENCY_PAIRS.getId()),
            Boolean.parseBoolean(parameters.get(PARAMETER_PAIR_SETTINGS.getId()))
        );
    }

    public HuobiConnector(@NonNull String apiKey, @NonNull String apiSecret, String currencyPairs, boolean pairSettings) {
        final ExchangeSpecification exSpec = new HuobiExchange().getDefaultExchangeSpecification();
        exSpec.setApiKey(apiKey);
        exSpec.setSecretKey(apiSecret);
        this.exchange = ExchangeFactory.INSTANCE.createExchange(exSpec);
        this.currencyPairs = currencyPairs;
        this.pairSettings = pairSettings;
    }

    public HuobiConnector(@NonNull String apiKey, @NonNull String apiSecret, @NonNull String currencyPairs) {
        this(apiKey, apiSecret, currencyPairs, false);
    }

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public DownloadResult getTransactions(String lastState) {
        final HuobiDownloader huobiDownloader = new HuobiDownloader(exchange);
        Map<String, HuobiDownloadState> state = HuobiDownloadState.deserializeState(lastState);

        List<UserTrade> userTrades = huobiDownloader.downloadTrades(currencyPairs, state);
        List<FundingRecord> fundingRecords = huobiDownloader.downloadFunding(state);

        return new DownloadResult(
            new XChangeConnectorParser().getParseResult(userTrades, fundingRecords),
            HuobiDownloadState.serializeState(state)
        );
    }
}
