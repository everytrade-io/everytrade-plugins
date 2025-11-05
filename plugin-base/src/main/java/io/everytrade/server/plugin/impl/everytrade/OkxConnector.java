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
import lombok.NonNull;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.ExchangeFactory;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.dto.account.FundingRecord;
import org.knowm.xchange.dto.trade.UserTrade;
import org.knowm.xchange.okex.OkexExchange;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public class OkxConnector implements IConnector {
    private static final String ID = WhaleBooksPlugin.ID + IPlugin.PLUGIN_PATH_SEPARATOR + "okexApiConnector";

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

    private static final ConnectorParameterDescriptor PARAMETER_PASS_PHRASE =
        new ConnectorParameterDescriptor(
            "passPhrase",
            ConnectorParameterType.SECRET,
            UiKey.CONNECTION_PASSPHRASE,
            "",
            false
        );

    public static final ConnectorDescriptor DESCRIPTOR = new ConnectorDescriptor(
        ID,
        "OKX Connector",
        "",
        SupportedExchange.OKX.getInternalId(),
        List.of(PARAMETER_API_KEY, PARAMETER_API_SECRET, PARAMETER_PASS_PHRASE)
    );

    private static final String OKEX_URL = "https://eea.okx.com";
    Exchange exchange;

    public OkxConnector(Map<String, String> parameters) {
        this(
            Objects.requireNonNull(parameters.get(PARAMETER_API_KEY.getId())),
            Objects.requireNonNull(parameters.get(PARAMETER_API_SECRET.getId())),
            Objects.requireNonNull(parameters.get(PARAMETER_PASS_PHRASE.getId()))
        );
    }

    public OkxConnector(@NonNull String apiKey, @NonNull String apiSecret, @NonNull String passPhrase) {
        final ExchangeSpecification exSpec = new OkexExchange().getDefaultExchangeSpecification();
        exSpec.setApiKey(apiKey);
        exSpec.setSecretKey(apiSecret);
        exSpec.setExchangeSpecificParametersItem(OkexExchange.PARAM_PASSPHRASE, passPhrase);
        this.exchange = ExchangeFactory.INSTANCE.createExchange(exSpec);
    }

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public DownloadResult getTransactions(String lastTransactionId) {
        final OkxDownloader okxDownloader = new OkxDownloader(lastTransactionId, exchange);

        List<UserTrade> userTrades = okxDownloader.downloadTrades();
        List<FundingRecord> deposits = okxDownloader.downloadDeposits();
        List<FundingRecord> withdrawals = okxDownloader.downloadWithdrawals();

        final ParseResult parseResult = new XChangeConnectorParser().getOkxParseResult(userTrades, deposits, withdrawals);
        return new DownloadResult(parseResult, okxDownloader.serializeState());

    }
}
