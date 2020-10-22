package com.example.everytrade.plugin;

import io.everytrade.server.model.SupportedExchange;
import io.everytrade.server.plugin.api.IPlugin;
import io.everytrade.server.plugin.api.connector.ConnectorDescriptor;
import io.everytrade.server.plugin.api.connector.DownloadResult;
import io.everytrade.server.plugin.api.connector.IConnector;

import java.util.List;
import java.util.Map;

public class ExampleConnector implements IConnector {
    public static final String ID = ExamplePlugin.ID + IPlugin.PLUGIN_PATH_SEPARATOR + "exampleConnector";
    public static final ConnectorDescriptor DESCRIPTOR = new ConnectorDescriptor(
        ID,
        "Connector Example",
        SupportedExchange.EVERYTRADE.getInternalId(),
        List.of()
    );

    public ExampleConnector(Map<String, String> parameters) {

    }

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public DownloadResult getTransactions(String lastTransactionId) {
        throw new UnsupportedOperationException("Implement me!");
    }
}
