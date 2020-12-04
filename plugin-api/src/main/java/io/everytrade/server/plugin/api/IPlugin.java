package io.everytrade.server.plugin.api;

import io.everytrade.server.plugin.api.connector.ConnectorDescriptor;
import io.everytrade.server.plugin.api.connector.IConnector;
import io.everytrade.server.plugin.api.parser.ICsvParser;
import io.everytrade.server.plugin.api.parser.ParserDescriptor;
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

    List<ParserDescriptor> allParserDescriptors();

    ICsvParser createParserInstance(String parserId);
}
