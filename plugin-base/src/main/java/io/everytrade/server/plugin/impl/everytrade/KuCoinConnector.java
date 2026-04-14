package io.everytrade.server.plugin.impl.everytrade;

import io.everytrade.server.UiKey;
import io.everytrade.server.model.SupportedExchange;
import io.everytrade.server.plugin.api.IPlugin;
import io.everytrade.server.plugin.api.connector.ConnectorDescriptor;
import io.everytrade.server.plugin.api.connector.ConnectorParameterDescriptor;
import io.everytrade.server.plugin.api.connector.ConnectorParameterType;
import io.everytrade.server.plugin.api.connector.DownloadResult;
import io.everytrade.server.plugin.api.connector.IConnector;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.ExchangeFactory;
import org.knowm.xchange.kucoin.KucoinExchange;

import java.util.List;
import java.util.Map;

import static lombok.AccessLevel.PRIVATE;
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = PRIVATE)
public class KuCoinConnector implements IConnector {
    private static final String ID = WhaleBooksPlugin.ID + IPlugin.PLUGIN_PATH_SEPARATOR + "kucoinApiConnector";
    private static final SupportedExchange SUPPORTED_EXCHANGE = SupportedExchange.KUCOIN;

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

    private static final ConnectorParameterDescriptor PARAMETER_PASS_PHRASE =
        new ConnectorParameterDescriptor(
            "passPhrase",
            ConnectorParameterType.SECRET,
            UiKey.CONNECTION_PASSPHRASE,
            "",
            false
        );

    public static final ConnectorDescriptor DESCRIPTOR = new ConnectorDescriptor(
        ID,
        "KuCoin Connector",
        "",
        SUPPORTED_EXCHANGE.getInternalId(),
        List.of(PARAMETER_API_KEY, PARAMETER_API_SECRET, PARAMETER_PASS_PHRASE)
    );

    Exchange exchange;

    public KuCoinConnector(Map<String, String> parameters) {
        this(parameters.get(PARAMETER_API_KEY.getId()), parameters.get(PARAMETER_API_SECRET.getId()),
            parameters.get(PARAMETER_PASS_PHRASE.getId()));
    }

    public KuCoinConnector(@NonNull String apiKey,@NonNull String apiSecret,@NonNull String passPhrase) {
        var exSpec = new KucoinExchange().getDefaultExchangeSpecification();
        exSpec.setApiKey(apiKey);
        exSpec.setSecretKey(apiSecret);
        exSpec.setExchangeSpecificParametersItem("passphrase", passPhrase);
        this.exchange = ExchangeFactory.INSTANCE.createExchange(exSpec);
    }

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public DownloadResult getTransactions(String downloadState) {
        return new KuCoinDownloader(exchange, downloadState).download();
    }
}
