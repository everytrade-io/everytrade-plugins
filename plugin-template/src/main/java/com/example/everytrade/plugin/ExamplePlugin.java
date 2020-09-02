package com.example.everytrade.plugin;

import io.everytrade.server.plugin.api.IPlugin;
import io.everytrade.server.plugin.api.connector.ConnectorDescriptor;
import io.everytrade.server.plugin.api.connector.IConnector;
import org.pf4j.Extension;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Extension
public class ExamplePlugin implements IPlugin {
    public static final String ID = "examplePlugin";

    private static final Map<String, ConnectorDescriptor> CONNECTORS_BY_ID = Set.of(
        ExampleConnector.DESCRIPTOR
    ).stream().collect(Collectors.toMap(ConnectorDescriptor::getId, it -> it));

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public List<ConnectorDescriptor> allConnectorDescriptors() {
        return List.copyOf(CONNECTORS_BY_ID.values());
    }

    @Override
    public ConnectorDescriptor connectorDescriptor(String connectorId) {
        return CONNECTORS_BY_ID.get(connectorId);
    }

    @Override
    public IConnector createConnectorInstance(String connectorId, Map<String, String> parameters) {
        if (connectorId.equals(ExampleConnector.DESCRIPTOR.getId())) {
            return new ExampleConnector(parameters);
        }
        return null;
    }
}
