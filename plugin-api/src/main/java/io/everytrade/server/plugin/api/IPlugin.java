package io.everytrade.server.plugin.api;

import io.everytrade.server.plugin.api.connector.ConnectorDescriptor;
import io.everytrade.server.plugin.api.connector.IConnector;
import io.everytrade.server.plugin.api.parser.ICsvParser;
import io.everytrade.server.plugin.api.parser.ParserDescriptor;
import io.everytrade.server.plugin.api.rateprovider.IRateProvider;
import io.everytrade.server.plugin.api.rateprovider.RateProviderDescriptor;
import org.pf4j.ExtensionPoint;

import java.util.List;
import java.util.Map;

public interface IPlugin extends ExtensionPoint {
    String PLUGIN_PATH_SEPARATOR = ".";

    /**
     * Returns plugin's identifier.
     * @return plugin ID
     */
    String getId();

    /**
     * Gets all connector descriptors.
     * @return list of descriptors for each of the plugin's connectors.
     */
    List<ConnectorDescriptor> allConnectorDescriptors();

    /**
     * Returns a descriptor for the specified connector
     * @param connectorId ID of the connector
     * @return descriptor of the specified connector.
     */
    ConnectorDescriptor connectorDescriptor(String connectorId);

    /**
     * Instantiates a new connector.
     * @param connectorId connector ID
     * @param parameters parameters for the connector instance (e.g. remote URL, credentials, etc.)
     * @return a new connector instance
     */
    IConnector createConnectorInstance(String connectorId, Map<String, String> parameters);

    /**
     * Gets all parser descriptors.
     * @return list of descriptors for each of the plugin's parsers.
     */
    List<ParserDescriptor> allParserDescriptors();

    /**
     * Instantiates a new parser.
     * @param parserId parser ID
     * @return a new parser instance
     */
    ICsvParser createParserInstance(String parserId);

    /**
     * Gets all rate provider descriptors.
     * @return list of descriptors for each of the plugin's conversion rate provider.
     */
    List<RateProviderDescriptor> allRateProviderDescriptors();

    /**
     * Instantiates a new conversion rate provider.
     * @param providerId rate provider ID
     * @return a new rate provider instance
     */
    IRateProvider createRateProviderInstance(String providerId);
}
