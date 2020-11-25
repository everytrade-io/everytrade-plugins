package io.everytrade.server.plugin.impl.everytrade;

import io.everytrade.server.plugin.api.IPlugin;
import io.everytrade.server.plugin.api.connector.ConnectorDescriptor;
import io.everytrade.server.plugin.api.connector.IConnector;
import io.everytrade.server.plugin.api.parser.ExchangeBean;
import io.everytrade.server.plugin.api.parser.postparse.IPostProcessor;
import io.everytrade.server.plugin.api.parser.preparse.IExchangeParser;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.BinanceBeanV1;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.BinanceBeanV2;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.BitfinexBeanV1;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.BitflyerBeanV1;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.BitmexBeanV1;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.BitstampBeanV1;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.BittrexBeanV1;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.BittrexBeanV2;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.CoinbaseBeanV1;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.CoinmateBeanV1;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.CoinmateBeanV2;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.CoinsquareBeanV1;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.CoinsquareBeanV2;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.EveryTradeBeanV1;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.EveryTradeBeanV2;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.GeneralBytesBeanV1;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.HitBtcBeanV1;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.HuobiBeanV1;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.KrakenBeanV1;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.LocalBitcoinsBeanV1;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.OkexBeanV1;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.PaxfulBeanV1;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.PoloniexBeanV1;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.ShakePayBeanV1;
import io.everytrade.server.plugin.impl.everytrade.parser.postparse.BitfinexPostProcessor;
import io.everytrade.server.plugin.impl.everytrade.parser.preparse.binance.v2.BinanceExchangeParser;
import org.pf4j.Extension;

import java.util.HashMap;
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
        BitfinexConnector.DESCRIPTOR,
        BinanceConnector.DESCRIPTOR,
        BittrexConnector.DESCRIPTOR,
        CoinbaseProConnector.DESCRIPTOR,
        BitmexConnector.DESCRIPTOR,
        OkexConnector.DESCRIPTOR,
        HuobiConnector.DESCRIPTOR
    ).stream().collect(Collectors.toMap(ConnectorDescriptor::getId, it -> it));

    private static final Map<String, Class<? extends ExchangeBean>> beanSignatures = new HashMap<>();

    private static final Map<Class<? extends ExchangeBean>, Class<? extends IPostProcessor>> postProcessors = Map.of(
        BitfinexBeanV1.class, BitfinexPostProcessor.class
    );

    private static final Map<Class<? extends ExchangeBean>, Class<? extends IExchangeParser>> parsers = Map.of(
        BinanceBeanV2.class, BinanceExchangeParser.class
    );

    static {
        beanSignatures.put(
            "BTX-001:|OrderUuid|Exchange|Type|Quantity|CommissionPaid|Price|Closed|",
            BittrexBeanV1.class
        );
        beanSignatures.put(
            "BTX-002:|Uuid|Exchange|OrderType|Quantity|Commission|Price|Closed|",
            BittrexBeanV2.class
        );
        beanSignatures.put(
            "BTS-001:|Datetime|Amount|Value|Rate|Fee|Sub Type|",
            BitstampBeanV1.class
        );
        beanSignatures.put(
            "ETR-001:|UID|DATE|SYMBOL|ACTION|QUANTY|PRICE|FEE|",
            EveryTradeBeanV1.class
        );
        beanSignatures.put(
            "ETR-002:|UID|DATE|SYMBOL|ACTION|QUANTY|VOLUME|FEE|",
            EveryTradeBeanV2.class
        );
        beanSignatures.put(
            "KRK-001:|txid|pair|time|type|cost|fee|vol|",
            KrakenBeanV1.class
        );
        beanSignatures.put(
            "GBT-001:|Server Time|Local Transaction Id|Remote Transaction Id|Type|Cash Amount|"
                + "Cash Currency|Crypto Amount|Crypto Currency|Status|",
            GeneralBytesBeanV1.class
        );
        beanSignatures.put(
            "CNM-001:|ID|Date|Type|Amount|Amount Currency|Price|Price Currency|Fee|Fee Currency|Status|",
            CoinmateBeanV1.class
        );
        beanSignatures.put(
            "HTB-001:|^Date \\(.*\\)$|Instrument|Trade ID|Side|Quantity|Price|Fee|Rebate|",
            HitBtcBeanV1.class
        );
        beanSignatures.put(
            "CNM-002:|?Transaction id|Date|Type detail|Currency amount|Amount|"
                + "Currency price|Price|Currency fee|Fee|Status|",
            CoinmateBeanV2.class
        );
        beanSignatures.put(
            "SHP-001:|Transaction Type|Date|Amount Debited|Debit Currency|Amount Credited|Credit Currency|",
            ShakePayBeanV1.class
        );
        beanSignatures.put(
            "POL-001:|Date|Market|Category|Type|Base Total Less Fee|Quote Total Less Fee|",
            PoloniexBeanV1.class
        );
        beanSignatures.put(
            "HUI-001:|Time|Type|Pair|Side|Amount|Total|Fee|",
            HuobiBeanV1.class
        );
        beanSignatures.put(
            "LOB-001:|id|trade_type|btc_final|fiat_amount|currency|transaction_released_at|",
            LocalBitcoinsBeanV1.class
        );
        beanSignatures.put(
            "PAX-001:|type|fiat_currency|amount_fiat|amount_btc|status|completed_at|trade_hash|",
            PaxfulBeanV1.class
        );
        beanSignatures.put(
            "BFX-001:|#|PAIR|AMOUNT|PRICE|FEE|FEE CURRENCY|DATE|",
            BitfinexBeanV1.class
        );
        beanSignatures.put(
            "CSQ-001:|date|action|currency|base_currency|amount|base_amount|",
            CoinsquareBeanV1.class
        );
        beanSignatures.put(
            "CSQ-002:|date|from_currency|from_amount|to_currency|to_amount|",
            CoinsquareBeanV2.class
        );
        beanSignatures.put(
            "BIN-001:|^Date\\(.*\\)$|Market|Type|Amount|Total|Fee|Fee Coin|",
            BinanceBeanV1.class
        );
        beanSignatures.put(
            "BIN-002:|^Date\\(.*\\)$|Pair|Type|Order Price|Order Amount|AvgTrading Price|Filled|status|",
            BinanceBeanV2.class
        );
        beanSignatures.put(
            "CBS-001:|trade id|product|side|created at|size|size unit|price|fee|price/fee/total unit|",
            CoinbaseBeanV1.class
        );
        beanSignatures.put(
            "BMX-001:|transactTime|symbol|execType|side|lastQty|lastPx|execComm|orderID|",
            BitmexBeanV1.class
        );
        beanSignatures.put(
            "BFY-001:|Trade Date|Trade Type|Traded Price|Currency 1|Amount (Currency 1)|Fee|Currency 2|Order ID|",
            BitflyerBeanV1.class
        );
        beanSignatures.put(
            "OKX-001:|\uFEFFTrade ID|\uFEFFTrade Time|\uFEFFPairs|\uFEFFAmount|\uFEFFPrice|\uFEFFTotal|\uFEFFFee" +
                "|\uFEFFunit|",
            OkexBeanV1.class
        );
    }
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
        if (connectorId.equals(OkexConnector.DESCRIPTOR.getId())) {
            return new OkexConnector(parameters);
        }
        if (connectorId.equals(HuobiConnector.DESCRIPTOR.getId())) {
            return new HuobiConnector(parameters);
        }
        return null;
    }

    @Override
    public Map<String, Class<? extends ExchangeBean>> allBeanSignatures() {
        return Map.copyOf(beanSignatures);
    }

    @Override
    public Map<Class<? extends ExchangeBean>, Class<? extends IPostProcessor>> allPostProcessors() {
        return postProcessors;
    }

    @Override
    public Map<Class<? extends ExchangeBean>, Class<? extends IExchangeParser>> allParsers() {
        return parsers;
    }
}
