package io.everytrade.server.plugin.impl.everytrade;

import io.everytrade.server.UiKey;
import io.everytrade.server.plugin.api.IPlugin;
import io.everytrade.server.plugin.api.connector.ConnectorDescriptor;
import io.everytrade.server.plugin.api.connector.ConnectorParameterDescriptor;
import io.everytrade.server.plugin.api.connector.ConnectorParameterType;
import io.everytrade.server.plugin.api.connector.DownloadResult;
import io.everytrade.server.plugin.api.connector.IConnector;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.ExchangeFactory;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.binance.BinanceExchange;
import org.knowm.xchange.dto.account.FundingRecord;
import org.knowm.xchange.dto.trade.UserTrade;

import java.util.List;
import java.util.Map;

import static io.everytrade.server.model.SupportedExchange.BINANCE;
import static lombok.AccessLevel.PRIVATE;

@RequiredArgsConstructor
@FieldDefaults(level = PRIVATE, makeFinal = true)
public class BinanceConnector implements IConnector {

    private static final Object LOCK = new Object();
    private static final String ID = WhaleBooksPlugin.ID + IPlugin.PLUGIN_PATH_SEPARATOR + "binanceApiConnector";
    private static final int MAX_DOWNLOADED_TXS = 7000;

    private static final ConnectorParameterDescriptor PARAMETER_API_SECRET =
        new ConnectorParameterDescriptor(
            "apiSecret",
            ConnectorParameterType.SECRET,
            "API Secret",
            "",
            false
        );

    private static final ConnectorParameterDescriptor PARAMETER_API_KEY =
        new ConnectorParameterDescriptor(
            "apiKey",
            ConnectorParameterType.STRING,
            "API Key",
            "",
            false
        );

    private static final ConnectorParameterDescriptor PARAMETER_CURRENCY_PAIRS =
        new ConnectorParameterDescriptor(
            "currencyPairs",
            ConnectorParameterType.STRING,
            UiKey.CONNECTION_CURRENCY_PAIRS_DESC,
            "",
            false
        );

    private static final ConnectorParameterDescriptor PARAMETER_PAIR_SETTINGS =
        new ConnectorParameterDescriptor(
            "pairSettings",
            ConnectorParameterType.BOOLEAN,
            UiKey.CONNECTION_CURRENCY_PAIRS_SETTINGS,
            "",
            true
        );

    private static final ConnectorParameterDescriptor PARAMETER_IS_PAID_SUBSCRIPTION =
        new ConnectorParameterDescriptor(
            "isPaidSubscription",
            ConnectorParameterType.BOOLEAN,
            UiKey.IS_PAID_SUBSCRIPTION,
            "",
            false
        );

    public static final ConnectorDescriptor DESCRIPTOR = new ConnectorDescriptor(
        ID,
        "Binance Connector",
        "",
        BINANCE.getInternalId(),
        List.of(
            PARAMETER_API_KEY,
            PARAMETER_API_SECRET,
            PARAMETER_PAIR_SETTINGS,
            PARAMETER_CURRENCY_PAIRS,
            PARAMETER_IS_PAID_SUBSCRIPTION)
    );

    Exchange exchange;
    XChangeConnectorParser parser;
    String currencyPairs;
    boolean pairSettings;
    boolean isPaidSubscription;

    public BinanceConnector(Map<String, String> parameters) {
        this(
            parameters.get(PARAMETER_API_KEY.getId()),
            parameters.get(PARAMETER_API_SECRET.getId()),
            parameters.get(PARAMETER_CURRENCY_PAIRS.getId()),
            Boolean.parseBoolean(parameters.get(PARAMETER_PAIR_SETTINGS.getId())),
            Boolean.parseBoolean(parameters.get(PARAMETER_IS_PAID_SUBSCRIPTION.getId()))
        );
    }
    public BinanceConnector(@NonNull String apiKey, @NonNull String apiSecret, String currencyPairs, boolean pairSettings) {
        this(apiKey, apiSecret, currencyPairs, pairSettings, false);
    }

    public BinanceConnector(@NonNull String apiKey, @NonNull String apiSecret, String currencyPairs,
                            boolean pairSettings, boolean isPaidSubscription) {
        this.exchange = ExchangeFactory.INSTANCE.createExchange(createExchangeSpec(apiKey, apiSecret));
        this.parser = new XChangeConnectorParser();
        this.currencyPairs = currencyPairs;
        this.pairSettings = pairSettings;
        this.isPaidSubscription = isPaidSubscription;
    }

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public DownloadResult getTransactions(String lastTransactionId) {
        synchronized (LOCK) {
            var binanceDownloader = new BinanceDownloader(exchange, lastTransactionId);
            List<UserTrade> convertedTrades = binanceDownloader.downloadConvertedTrades();
            List<FundingRecord> funding = binanceDownloader.downloadDepositsAndWithdrawals(MAX_DOWNLOADED_TXS);
            List<UserTrade> userTrades = binanceDownloader.downloadTrades(currencyPairs, pairSettings, isPaidSubscription);
            userTrades.addAll(convertedTrades);
            return DownloadResult.builder()
                .parseResult(parser.getParseResult(userTrades, funding))
                .downloadStateData(binanceDownloader.serializeState())
                .build();
        }
    }

    private ExchangeSpecification createExchangeSpec(String apiKey, String apiSecret) {
        ExchangeSpecification exSpec = new BinanceExchange().getDefaultExchangeSpecification();
        exSpec.setApiKey(apiKey);
        exSpec.setSecretKey(apiSecret);
        return exSpec;
    }
}
