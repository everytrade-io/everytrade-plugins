package io.everytrade.server.plugin.impl.everytrade;

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
import org.knowm.xchange.dase.DaseExchange;
import org.knowm.xchange.dto.account.FundingRecord;
import org.knowm.xchange.dto.trade.UserTrade;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public class DaseConnector implements IConnector {
    private static final String ID = WhaleBooksPlugin.ID + IPlugin.PLUGIN_PATH_SEPARATOR + "daseApiConnector";

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

    public static final ConnectorDescriptor DESCRIPTOR = new ConnectorDescriptor(
        ID,
        "DASE Connector",
        "",
        SupportedExchange.DASE.getInternalId(),
        List.of(PARAMETER_API_KEY, PARAMETER_API_SECRET)
    );

    Exchange exchange;

    public DaseConnector(Map<String, String> parameters) {
        this(
            Objects.requireNonNull(parameters.get(PARAMETER_API_KEY.getId())),
            Objects.requireNonNull(parameters.get(PARAMETER_API_SECRET.getId()))
        );
    }

    public DaseConnector(@NonNull String apiKey, @NonNull String apiSecret) {
        final ExchangeSpecification exSpec = new DaseExchange().getDefaultExchangeSpecification();
        exSpec.setApiKey(apiKey);
        exSpec.setSecretKey(apiSecret);
        this.exchange = ExchangeFactory.INSTANCE.createExchange(exSpec);
    }

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public DownloadResult getTransactions(String lastTransactionId) {
        final DaseDownloader daseDownloader = new DaseDownloader(lastTransactionId, exchange);

        List<UserTrade> userTrades = daseDownloader.downloadTrades();
        List<FundingRecord> fundings = daseDownloader.downloadFundings();

        final ParseResult parseResult = new XChangeConnectorParser().getDaseParseResult(userTrades, fundings);
        return new DownloadResult(parseResult, daseDownloader.serializeState());
    }
}
