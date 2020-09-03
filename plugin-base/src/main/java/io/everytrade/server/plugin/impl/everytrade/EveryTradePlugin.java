package io.everytrade.server.plugin.impl.everytrade;

import io.everytrade.server.plugin.api.IPlugin;
import io.everytrade.server.plugin.api.connector.ConnectorDescriptor;
import io.everytrade.server.plugin.api.connector.IConnector;
import org.pf4j.Extension;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Extension
public class EveryTradePlugin implements IPlugin {
    public static final String ID = "everytrade";

    private static final Map<String, ConnectorDescriptor> CONNECTORS_BY_ID = Set.of(
        EveryTradeConnector.DESCRIPTOR,
        KrakenConnector.DESCRIPTOR,
        BitstampConnector.DESCRIPTOR,
        CoinmateConnector.DESCRIPTOR,
        BitfinexConnector.DESCRIPTOR
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
        if (connectorId.equals(EveryTradeConnector.DESCRIPTOR.getId())) {
            return new EveryTradeConnector(parameters);
        }
        if (connectorId.equals(KrakenConnector.DESCRIPTOR.getId())) {
            return new KrakenConnector(parameters);
        }
        if (connectorId.equals(BitstampConnector.DESCRIPTOR.getId())) {
            return new BitstampConnector(parameters);
        }
        if (connectorId.equals(CoinmateConnector.DESCRIPTOR.getId())) {
            return new CoinmateConnector(parameters);
        }
        if (connectorId.equals(BitfinexConnector.DESCRIPTOR.getId())) {
            return new BitfinexConnector(parameters);
        }
        return null;
    }
}
