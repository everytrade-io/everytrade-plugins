package io.everytrade.server.plugin;

import io.everytrade.server.plugin.api.IPlugin;
import io.everytrade.server.plugin.api.connector.ConnectorDescriptor;
import io.everytrade.server.plugin.api.connector.DownloadResult;
import io.everytrade.server.plugin.api.connector.IConnector;
import io.everytrade.server.plugin.api.parser.ConversionStatistic;
import io.everytrade.server.plugin.api.parser.ImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.ParseResult;
import io.everytrade.server.plugin.support.EverytradePluginManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

public class Tester {
    private final Logger log = LoggerFactory.getLogger(this.getClass());
    private final Path workDir;
    private final Path pluginDir;

    public Tester(Path workDir) {
        this.workDir = workDir;
        final Properties properties = loadProperties("tester.properties").orElse(new Properties());
        pluginDir = Paths.get(
            properties.getProperty("tester.pluginDir", "plugin-tester/build/testedPlugins")
        );
    }

    public Optional<Properties> loadProperties(String fileName) {
        try {
            final Properties properties = new Properties();
            properties.load(new FileInputStream(workDir.resolve(fileName).toFile()));
            return Optional.of(properties);
        } catch (FileNotFoundException e) {
            return Optional.empty();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public static void main(String[] args) {
        new Tester(
            Paths.get(
                args.length == 1 ? args[0] : ""
            )
        ).test();
    }

    private void test() {
        final EverytradePluginManager pluginManager =
            new EverytradePluginManager(pluginDir);

        pluginManager.loadPlugins();
        pluginManager.startPlugins();
        final List<IPlugin> plugins = pluginManager.getExtensions(IPlugin.class);

        for (IPlugin plugin : plugins) {
            testPlugin(plugin);
        }
    }

    private void testPlugin(IPlugin plugin) {
        log.info("plugin = " + plugin.getId());
        final List<ConnectorDescriptor> connectorDescriptors = plugin.allConnectorDescriptors();
        for (ConnectorDescriptor connectorDescriptor : connectorDescriptors) {
            log.info("connectorDescriptor = " + connectorDescriptor);
            final Optional<Map<String, String>> parameters = loadParams(connectorDescriptor.getId());
            if (parameters.isEmpty()) {
                continue;
            }
            final IConnector connector = plugin.createConnectorInstance(connectorDescriptor.getId(), parameters.get());

            //first connection
            final DownloadResult downloadResult = connector.getTransactions(null);
            printResult(downloadResult);

            //follow-up connection
            printResult(connector.getTransactions(downloadResult.getLastDownloadedTransactionId()));
        }
    }

    private void printResult(DownloadResult downloadResult) {
        final ParseResult parseResult = downloadResult.getParseResult();

        for (ImportedTransactionBean importedTransactionBean : parseResult.getImportedTransactionBeans()) {
            log.info("importedTransactionBean = " + importedTransactionBean);
        }

        final ConversionStatistic conversionStatistic = parseResult.getConversionStatistic();
        log.info("conversionStatistic = " + conversionStatistic);

        final String lastDownloadedTransactionId = downloadResult.getLastDownloadedTransactionId();
        log.info("lastDownloadedTransactionId = " + lastDownloadedTransactionId);
    }

    private Optional<Map<String, String>> loadParams(String id) {
        final Optional<Properties> properties = loadProperties("plugin-tester/private/" + id +".properties");
        return properties.map(this::toMap);
    }

    private Map<String, String> toMap(Properties properties) {
        final Map<String, String> ret = new HashMap<>();
        for (String propertyName : properties.stringPropertyNames()) {
            ret.put(propertyName, properties.getProperty(propertyName));
        }
        return ret;
    }
}
