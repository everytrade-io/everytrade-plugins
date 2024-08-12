package io.everytrade.server.plugin.impl.everytrade;

import io.everytrade.server.plugin.api.IPlugin;
import io.everytrade.server.plugin.api.rateprovider.RateProviderDescriptor;
import io.everytrade.server.plugin.api.connector.ConnectorDescriptor;
import io.everytrade.server.plugin.api.connector.IConnector;
import io.everytrade.server.plugin.api.parser.ICsvParser;
import io.everytrade.server.plugin.api.parser.ParserDescriptor;
import io.everytrade.server.plugin.api.rateprovider.IRateProvider;
import io.everytrade.server.plugin.impl.everytrade.parser.EverytradeCsvMultiParser;
import io.everytrade.server.plugin.impl.everytrade.rateprovider.CoinPaprikaRateProvider;
import org.pf4j.Extension;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Extension
public class WhaleBooksPlugin implements IPlugin {
    public static final String ID = "everytrade";

    private static final Map<String, ConnectorDescriptor> CONNECTORS_BY_ID =
        Set.of(
            WhaleBooksConnector.DESCRIPTOR,
            KrakenConnector.DESCRIPTOR,
            BitstampConnector.DESCRIPTOR,
            CoinmateConnector.DESCRIPTOR,
            BitfinexConnector.DESCRIPTOR,
            BinanceConnector.DESCRIPTOR,
            BittrexConnector.DESCRIPTOR,
            CoinbaseProConnector.DESCRIPTOR,
            BitmexConnector.DESCRIPTOR,
            OkxConnector.DESCRIPTOR,
            HuobiConnector.DESCRIPTOR,
            CoinbaseConnector.DESCRIPTOR,
            BlockchainBtcConnector.DESCRIPTOR,
            BlockchainLtcConnector.DESCRIPTOR,
            BlockchainEthConnector.DESCRIPTOR,
            KuCoinConnector.DESCRIPTOR
        ).stream()
            .collect(Collectors.toMap(ConnectorDescriptor::getId, it -> it));

    private static final List<ParserDescriptor> PARSER_DESCRIPTORS = List.of(
        EverytradeCsvMultiParser.DESCRIPTOR
    );

    private static final List<RateProviderDescriptor> RATE_PROVIDER_DESCRIPTORS =
        List.of(CoinPaprikaRateProvider.DESCRIPTOR);

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
        if (connectorId.equals(WhaleBooksConnector.DESCRIPTOR.getId())) {
            return new WhaleBooksConnector(parameters);
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
        if (connectorId.equals(BinanceConnector.DESCRIPTOR.getId())) {
            return new BinanceConnector(parameters);
        }
        if (connectorId.equals(BittrexConnector.DESCRIPTOR.getId())) {
            return new BittrexConnector(parameters);
        }
        if (connectorId.equals(CoinbaseProConnector.DESCRIPTOR.getId())) {
            return new CoinbaseProConnector(parameters);
        }
        if (connectorId.equals(BitmexConnector.DESCRIPTOR.getId())) {
            return new BitmexConnector(parameters);
        }
        if (connectorId.equals(OkxConnector.DESCRIPTOR.getId())) {
            return new OkxConnector(parameters);
        }
        if (connectorId.equals(HuobiConnector.DESCRIPTOR.getId())) {
            return new HuobiConnector(parameters);
        }
        if (connectorId.equals(CoinbaseConnector.DESCRIPTOR.getId())) {
            return new CoinbaseConnector(parameters);
        }
        if (connectorId.equals(BlockchainBtcConnector.DESCRIPTOR.getId())) {
            return new BlockchainBtcConnector(parameters);
        }
        if (connectorId.equals(BlockchainLtcConnector.DESCRIPTOR.getId())) {
            return new BlockchainLtcConnector(parameters);
        }
        if (connectorId.equals(BlockchainEthConnector.DESCRIPTOR.getId())) {
            return new BlockchainEthConnector(parameters);
        }
        if (connectorId.equals(KuCoinConnector.DESCRIPTOR.getId())) {
            return new KuCoinConnector(parameters);
        }
        return null;
    }

    @Override
    public List<ParserDescriptor> allParserDescriptors() {
        return PARSER_DESCRIPTORS;
    }

    @Override
    public ICsvParser createParserInstance(String parserId) {
        if (parserId.equals(EverytradeCsvMultiParser.DESCRIPTOR.getId())) {
            return new EverytradeCsvMultiParser();
        }
        return null;
    }

    @Override
    public List<RateProviderDescriptor> allRateProviderDescriptors() {
        return RATE_PROVIDER_DESCRIPTORS;
    }

    @Override
    public IRateProvider createRateProviderInstance(String providerId) {
        if (providerId.equals(CoinPaprikaRateProvider.DESCRIPTOR.getId())) {
            return new CoinPaprikaRateProvider();
        }
        return null;
    }
}
