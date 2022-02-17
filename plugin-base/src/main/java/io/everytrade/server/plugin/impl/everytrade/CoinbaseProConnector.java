package io.everytrade.server.plugin.impl.everytrade;

import io.everytrade.server.UiKey;
import io.everytrade.server.model.SupportedExchange;
import io.everytrade.server.plugin.api.IPlugin;
import io.everytrade.server.plugin.api.connector.ConnectorDescriptor;
import io.everytrade.server.plugin.api.connector.ConnectorParameterDescriptor;
import io.everytrade.server.plugin.api.connector.ConnectorParameterType;
import io.everytrade.server.plugin.api.connector.DownloadResult;
import io.everytrade.server.plugin.api.connector.IConnector;
import io.everytrade.server.plugin.api.parser.ParseResult;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.ExchangeFactory;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.coinbasepro.CoinbaseProExchange;
import org.knowm.xchange.dto.trade.UserTrade;
import org.knowm.xchange.service.trade.TradeService;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import static java.util.Collections.emptyList;

public class CoinbaseProConnector implements IConnector {
    private static final String ID = EveryTradePlugin.ID + IPlugin.PLUGIN_PATH_SEPARATOR + "coinbaseProApiConnector";
    private static final SupportedExchange SUPPORTED_EXCHANGE = SupportedExchange.COINBASE_PRO;

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

    private static final ConnectorParameterDescriptor PARAMETER_PASS_PHRASE =
        new ConnectorParameterDescriptor(
            "passPhrase",
            ConnectorParameterType.SECRET,
            UiKey.CONNECTION_PASSPHRASE,
            ""
        );

    private static final ConnectorParameterDescriptor PARAMETER_CURRENCY_PAIRS =
        new ConnectorParameterDescriptor(
            "currencyPairs",
            ConnectorParameterType.STRING,
            UiKey.CONNECTION_CURRENCY_PAIRS_DESC,
            ""
        );

    public static final ConnectorDescriptor DESCRIPTOR = new ConnectorDescriptor(
        ID,
        "Coinbase Pro Connector",
        "",
        SUPPORTED_EXCHANGE.getInternalId(),
        List.of(PARAMETER_API_KEY, PARAMETER_API_SECRET, PARAMETER_PASS_PHRASE, PARAMETER_CURRENCY_PAIRS)
    );

    private final String apiKey;
    private final String apiSecret;
    private final String currencyPairs;
    private final String passPhrase;

    public CoinbaseProConnector(Map<String, String> parameters) {
        Objects.requireNonNull(this.apiKey = parameters.get(PARAMETER_API_KEY.getId()));
        Objects.requireNonNull(this.apiSecret = parameters.get(PARAMETER_API_SECRET.getId()));
        Objects.requireNonNull(this.currencyPairs = parameters.get(PARAMETER_CURRENCY_PAIRS.getId()));
        Objects.requireNonNull(this.passPhrase = parameters.get(PARAMETER_PASS_PHRASE.getId()));
    }

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public DownloadResult getTransactions(String lastTransactionId) {
        final ExchangeSpecification exSpec = new CoinbaseProExchange().getDefaultExchangeSpecification();
        exSpec.setApiKey(apiKey);
        exSpec.setSecretKey(apiSecret);
        exSpec.setExchangeSpecificParametersItem("passphrase", passPhrase);
        final Exchange exchange = ExchangeFactory.INSTANCE.createExchange(exSpec);

        final CoinbaseProDownloader coinbaseProDownloader = new CoinbaseProDownloader(exchange, lastTransactionId);
        final ParseResult downloadResult = coinbaseProDownloader.download(currencyPairs);

        return new DownloadResult(downloadResult, coinbaseProDownloader.getLastTransactionId());
    }
}
