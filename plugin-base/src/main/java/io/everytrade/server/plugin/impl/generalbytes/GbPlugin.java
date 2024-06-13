package io.everytrade.server.plugin.impl.generalbytes;

import io.everytrade.server.model.Currency;
import io.everytrade.server.plugin.api.IPlugin;
import io.everytrade.server.plugin.api.connector.ConnectorDescriptor;
import io.everytrade.server.plugin.api.connector.IConnector;
import io.everytrade.server.plugin.api.parser.ICsvParser;
import io.everytrade.server.plugin.api.parser.ParserDescriptor;
import io.everytrade.server.plugin.api.rateprovider.IRateProvider;
import io.everytrade.server.plugin.api.rateprovider.RateProviderDescriptor;
import org.pf4j.Extension;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Extension
public class GbPlugin implements IPlugin {
    public static final String ID = "generalbytes";

    private static final Map<String, ConnectorDescriptor> CONNECTORS_BY_ID =
        Set.of(GbConnector.DESCRIPTOR)
            .stream()
            .collect(Collectors.toMap( ConnectorDescriptor::getId, it -> it ));

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
        if (connectorId.equals(GbConnector.DESCRIPTOR.getId())) {
            return new GbConnector(parameters);
        }
        return null;
    }

    @Override
    public List<ParserDescriptor> allParserDescriptors() {
        return List.of();
    }

    @Override
    public ICsvParser createParserInstance(String parserId) {
        return null;
    }

    @Override
    public List<RateProviderDescriptor> allRateProviderDescriptors() {
        return Collections.emptyList();
    }

    @Override
    public IRateProvider createRateProviderInstance(String providerId) {
        return null;
    }

    public static Currency parseGbCurrency(String currency) {
        if ("USDTTRON".equalsIgnoreCase(currency)) {
            return Currency.USDT;
        } else if ("LBTC".equalsIgnoreCase(currency)) { //BTC Lightning
            return Currency.BTC;
        } else {
            return Currency.fromCode(currency);
        }
    }
}
