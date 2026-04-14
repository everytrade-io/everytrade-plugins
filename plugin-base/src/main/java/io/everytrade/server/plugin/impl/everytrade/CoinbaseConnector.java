package io.everytrade.server.plugin.impl.everytrade;

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
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.coinbase.v2.CoinbaseExchange;

import java.util.List;
import java.util.Map;

import static lombok.AccessLevel.PRIVATE;

@RequiredArgsConstructor
@FieldDefaults(level = PRIVATE, makeFinal = true)
public class CoinbaseConnector implements IConnector {
    private static final String ID = WhaleBooksPlugin.ID + IPlugin.PLUGIN_PATH_SEPARATOR + "coinbaseApiConnector";
    private static final SupportedExchange SUPPORTED_EXCHANGE = SupportedExchange.COINBASE;

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

    public static final ConnectorDescriptor DESCRIPTOR = new ConnectorDescriptor(
        ID,
        "Coinbase Connector",
        "",
        SUPPORTED_EXCHANGE.getInternalId(),
        List.of(PARAMETER_API_KEY, PARAMETER_API_SECRET)
    );

    Exchange exchange;

    public CoinbaseConnector(Map<String, String> parameters) {
        this(parameters.get(PARAMETER_API_KEY.getId()), parameters.get(PARAMETER_API_SECRET.getId()));
    }

    public CoinbaseConnector(@NonNull String apiKey, @NonNull String secret) {
        final ExchangeSpecification exSpec = new CoinbaseExchange().getDefaultExchangeSpecification();
        exSpec.setApiKey(apiKey);
        exSpec.setSecretKey(secret);
        this.exchange =  ExchangeFactory.INSTANCE.createExchange(exSpec);
    }

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public DownloadResult getTransactions(String lastTransactionId) {
        CoinbaseDownloader coinbaseDownloader = new CoinbaseDownloader(exchange);
        DownloadResult download = coinbaseDownloader.download(lastTransactionId);
        return download;
    }
}
