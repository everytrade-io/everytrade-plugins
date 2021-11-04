package io.everytrade.server.plugin.impl.everytrade;

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
import org.knowm.xchange.coinbase.v2.CoinbaseExchange;
import org.knowm.xchange.coinbase.v2.service.CoinbaseTradeService;
import org.knowm.xchange.dto.trade.UserTrade;
import org.knowm.xchange.service.account.AccountService;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.Collections.emptyList;

public class CoinbaseConnector implements IConnector {
    private static final String ID = EveryTradePlugin.ID + IPlugin.PLUGIN_PATH_SEPARATOR + "coinbaseApiConnector";
    private static final SupportedExchange SUPPORTED_EXCHANGE = SupportedExchange.COINBASE;

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

    public static final ConnectorDescriptor DESCRIPTOR = new ConnectorDescriptor(
        ID,
        "Coinbase Connector",
        "",
        SUPPORTED_EXCHANGE.getInternalId(),
        List.of(PARAMETER_API_KEY, PARAMETER_API_SECRET)
    );
    private static final int REAL_WALLET_ID_LENGTH = 36;
    private final String apiKey;
    private final String apiSecret;

    public CoinbaseConnector(Map<String, String> parameters) {
        Objects.requireNonNull(this.apiKey = parameters.get(PARAMETER_API_KEY.getId()));
        Objects.requireNonNull(this.apiSecret = parameters.get(PARAMETER_API_SECRET.getId()));
    }

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public DownloadResult getTransactions(String lastTransactionId) {
        final ExchangeSpecification exSpec = new CoinbaseExchange().getDefaultExchangeSpecification();
        exSpec.setApiKey(apiKey);
        exSpec.setSecretKey(apiSecret);
        final Exchange exchange = ExchangeFactory.INSTANCE.createExchange(exSpec);
        final CoinbaseTradeService tradeService = (CoinbaseTradeService) exchange.getTradeService();
        final AccountService accountService = exchange.getAccountService();
        final Set<String> walletIds;
        try {
            walletIds = filterWalletIds(accountService.getAccountInfo().getWallets().keySet());
        } catch (IOException e) {
            throw new IllegalStateException("Wallets download failed.", e);
        }
        final CoinbaseDownloader coinbaseDownloader
            = new CoinbaseDownloader(tradeService, lastTransactionId, walletIds);
        final List<UserTrade> userTrades = coinbaseDownloader.download();
        final ParseResult parseResult = new XChangeConnectorParser().getParseResult(userTrades, emptyList());

        return new DownloadResult(parseResult, coinbaseDownloader.getLastTransactionId());
    }

    private Set<String> filterWalletIds(Set<String> walletsIds) {
        return walletsIds
            .stream()
            .filter(s -> s.length() == REAL_WALLET_ID_LENGTH)
            .collect(Collectors.toSet());
    }
}
