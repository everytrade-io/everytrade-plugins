package io.everytrade.server.plugin.impl.everytrade;

import io.everytrade.server.UiKey;
import io.everytrade.server.model.SupportedExchange;
import io.everytrade.server.plugin.api.IPlugin;
import io.everytrade.server.plugin.api.connector.ConnectorDescriptor;
import io.everytrade.server.plugin.api.connector.ConnectorParameterDescriptor;
import io.everytrade.server.plugin.api.connector.ConnectorParameterType;
import io.everytrade.server.plugin.api.connector.DownloadResult;
import io.everytrade.server.plugin.api.connector.IConnector;
import lombok.experimental.FieldDefaults;
import org.knowm.xchange.ExchangeFactory;
import org.knowm.xchange.kucoin.KucoinExchange;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import static lombok.AccessLevel.PRIVATE;

@FieldDefaults(makeFinal = true, level = PRIVATE)
public class KuCoinConnector implements IConnector {
    private static final String ID = EveryTradePlugin.ID + IPlugin.PLUGIN_PATH_SEPARATOR + "kuCoinApiConnector";
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

    String apiKey;
    String apiSecret;
    String passPhrase;

    public KuCoinConnector(Map<String, String> parameters) {
        Objects.requireNonNull(this.apiKey = parameters.get(PARAMETER_API_KEY.getId()));
        Objects.requireNonNull(this.apiSecret = parameters.get(PARAMETER_API_SECRET.getId()));
        Objects.requireNonNull(this.passPhrase = parameters.get(PARAMETER_PASS_PHRASE.getId()));
    }

    public KuCoinConnector(String apiKey, String apiSecret, String passPhrase) {
        Objects.requireNonNull(this.apiKey = apiKey);
        Objects.requireNonNull(this.apiSecret = apiSecret);
        Objects.requireNonNull(this.passPhrase = passPhrase);
    }

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public DownloadResult getTransactions(String downloadState) {
        var exSpec = new KucoinExchange().getDefaultExchangeSpecification();
        exSpec.setApiKey(apiKey);
        exSpec.setSecretKey(apiSecret);
        exSpec.setExchangeSpecificParametersItem("passphrase", passPhrase);
        var exchange = ExchangeFactory.INSTANCE.createExchange(exSpec);
        return new KuCoinDownloader(exchange, downloadState).download();
    }
}
