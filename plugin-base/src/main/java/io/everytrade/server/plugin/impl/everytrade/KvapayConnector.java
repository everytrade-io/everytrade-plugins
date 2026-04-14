package io.everytrade.server.plugin.impl.everytrade;

import io.everytrade.server.model.SupportedExchange;
import io.everytrade.server.plugin.api.IPlugin;
import io.everytrade.server.plugin.api.connector.ConnectorDescriptor;
import io.everytrade.server.plugin.api.connector.ConnectorParameterDescriptor;
import io.everytrade.server.plugin.api.connector.ConnectorParameterType;
import io.everytrade.server.plugin.api.connector.DownloadResult;
import lombok.NonNull;

import java.util.List;
import java.util.Map;

public class KvapayConnector extends WhaleBooksConnector {
    private static final String ID = WhaleBooksPlugin.ID + IPlugin.PLUGIN_PATH_SEPARATOR + "kvapayApiConnector";
    private static final SupportedExchange SUPPORTED_EXCHANGE = SupportedExchange.KVAPAY;


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
        "Kvapay Connector",
        "",
        SUPPORTED_EXCHANGE.getInternalId(),
        List.of(PARAMETER_API_KEY, PARAMETER_API_SECRET)
    );

    public KvapayConnector(Map<String, String> parameters) {
        this(
            parameters.get(PARAMETER_API_KEY.getId()),
            parameters.get(PARAMETER_API_SECRET.getId())
        );
    }

    public KvapayConnector(@NonNull String apiKey, @NonNull String apiSecret) {
        super("https://app.kvapay.com/api/whalebooks/", apiKey, apiSecret);
    }

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public DownloadResult getTransactions(String lastTransactionId) {
        return super.getTransactions(lastTransactionId);
    }
}
