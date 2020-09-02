package io.everytrade.server.plugin.impl.generalbytes;

import io.everytrade.server.plugin.api.connector.ConnectorParameterType;
import io.everytrade.server.model.SupportedExchange;
import io.everytrade.server.plugin.api.IPlugin;
import io.everytrade.server.plugin.api.connector.ConnectorDescriptor;
import io.everytrade.server.plugin.api.connector.ConnectorParameterDescriptor;
import io.everytrade.server.plugin.api.connector.IConnector;
import io.everytrade.server.plugin.api.connector.DownloadResult;
import io.everytrade.server.plugin.impl.everytrade.EveryTradeConnector;

import java.util.List;
import java.util.Map;


public class GBConnector implements IConnector {
    private static final String ID = GBPlugin.ID + IPlugin.PLUGIN_PATH_SEPARATOR + "GBConnector";

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

    private static final ConnectorParameterDescriptor PARAMETER_URL =
        new ConnectorParameterDescriptor(
            "url",
            ConnectorParameterType.STRING,
            "CAS URL",
            "URL to GENERAL BYTES Crypto Application Server For example https://gb.example.com:7777/"
        );

    public static final ConnectorDescriptor DESCRIPTOR = new ConnectorDescriptor(
        ID,
        "GENERAL BYTES CAS Connector",
        SupportedExchange.GENERAL_BYTES.getInternalId(),
        List.of(PARAMETER_URL, PARAMETER_API_KEY, PARAMETER_API_SECRET)
    );


    private EveryTradeConnector con = null;

    public GBConnector(Map<String, String> parameters) {
        String url = parameters.get(PARAMETER_URL.getId());
        if (!url.contains("/everytrade")) {
            if (!url.endsWith("/")) {
                url+="/";
            }
            url+="everytrade";
        }
        con = new EveryTradeConnector (
            url,
            parameters.get(PARAMETER_API_KEY.getId()),
            parameters.get(PARAMETER_API_SECRET.getId())
        );
    }

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public DownloadResult getTransactions(String lastTransactionId) {
        return con.getTransactions(lastTransactionId);
    }

    @Override
    public void close() {
        con.close();
    }
}
