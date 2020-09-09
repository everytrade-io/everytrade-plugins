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
import org.knowm.xchange.coinbasepro.CoinbaseProExchange;
import org.knowm.xchange.coinbasepro.dto.trade.CoinbaseProTradeHistoryParams;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.trade.UserTrade;
import org.knowm.xchange.service.trade.TradeService;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class CoinbaseProConnector implements IConnector {
    private static final String ID = EveryTradePlugin.ID + IPlugin.PLUGIN_PATH_SEPARATOR + "coinbaseProApiConnector";
    //https://docs.pro.coinbase.com/#rate-limits - max 5 request per user per second
    private static final int TX_PER_REQUEST = 100;
    private static final int MAX_REQUEST_COUNT = 3000;
    private static final int MAX_REQUESTS_PER_SECOND = 5;
    private static final int REQUESTS_WINDOW_MS = 1000;
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

    private static final ConnectorParameterDescriptor PARAMETER_API_PASS_PHRASE =
            new ConnectorParameterDescriptor(
                    "apiPassPhrase",
                    ConnectorParameterType.SECRET,
                    "API PassPhrase",
                    ""
            );

    private static final ConnectorParameterDescriptor PARAMETER_API_SYMBOLS =
            new ConnectorParameterDescriptor(
                    "apiSymbols",
                    ConnectorParameterType.STRING,
                    "Trade pairs (e.g.: LTC/EUR,LTC/BTC)",
                    ""
            );

    public static final ConnectorDescriptor DESCRIPTOR = new ConnectorDescriptor(
            ID,
            "Coinbase Pro Connector",
            SUPPORTED_EXCHANGE.getInternalId(),
            List.of(PARAMETER_API_KEY, PARAMETER_API_SECRET, PARAMETER_API_SYMBOLS, PARAMETER_API_PASS_PHRASE)
    );

    private final String apiKey;
    private final String apiSecret;
    private final String apiSymbols;
    private final String apiPassPhrase;

    public CoinbaseProConnector(Map<String, String> parameters) {
        Objects.requireNonNull(this.apiKey = parameters.get(PARAMETER_API_KEY.getId()));
        Objects.requireNonNull(this.apiSecret = parameters.get(PARAMETER_API_SECRET.getId()));
        Objects.requireNonNull(this.apiSymbols = parameters.get(PARAMETER_API_SYMBOLS.getId()));
        Objects.requireNonNull(this.apiPassPhrase = parameters.get(PARAMETER_API_PASS_PHRASE.getId()));
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
        exSpec.setExchangeSpecificParametersItem("passphrase", apiPassPhrase);
        final Exchange exchange = ExchangeFactory.INSTANCE.createExchange(exSpec);
        final TradeService tradeService = exchange.getTradeService();
        final CoinbaseProDownloadState downloadState = CoinbaseProDownloadState.parseFrom(lastTransactionId);

        final List<UserTrade> userTrades = download(tradeService, downloadState);
        final ParseResult parseResult = XChangeConnectorParser.getParseResult(userTrades, SUPPORTED_EXCHANGE);

        return new DownloadResult(parseResult, downloadState.toLastTransactionId());
    }

    private List<UserTrade> download(TradeService tradeService, CoinbaseProDownloadState downloadState) {
        final CoinbaseProTradeHistoryParams tradeHistoryParams
                = (CoinbaseProTradeHistoryParams) tradeService.createTradeHistoryParams();
        tradeHistoryParams.setLimit(TX_PER_REQUEST);

        final List<CurrencyPair> currencyPairs = symbolsToPairs(apiSymbols);
        final List<UserTrade> userTrades = new ArrayList<>();
        int counter = 0;

        for (CurrencyPair currencyPair : currencyPairs) {
            tradeHistoryParams.setCurrencyPair(currencyPair);
            Integer lastDownloadedTx = downloadState.getLastTransactionId(currencyPair.toString());
            tradeHistoryParams.setBeforeTradeId(lastDownloadedTx == null ? 1 : lastDownloadedTx );

            while (counter++ < MAX_REQUEST_COUNT) {
                final List<UserTrade> userTradesBlock;
                downloadSleep(counter);
                try {
                    userTradesBlock = tradeService.getTradeHistory(tradeHistoryParams).getUserTrades();
                } catch (Exception e) {
                    throw new IllegalStateException("User trade history download failed. ", e);
                }
                if (userTradesBlock.isEmpty()) {
                    break;
                }
                userTrades.addAll(userTradesBlock);
                lastDownloadedTx = Integer.valueOf(userTradesBlock.get(userTradesBlock.size() - 1).getId());
                tradeHistoryParams.setBeforeTradeId(lastDownloadedTx);
            }
            if (lastDownloadedTx != null) {
                downloadState.updateLastTransactionId(currencyPair.toString(), lastDownloadedTx);
            }
        }

        return userTrades;
    }

    private void downloadSleep(int counter) {
        if (counter % MAX_REQUESTS_PER_SECOND == 0) {
            try {
                Thread.sleep(REQUESTS_WINDOW_MS);
            } catch (InterruptedException e) {
                throw new IllegalStateException("User trade history download sleep interrupted.", e);
            }
        }
    }

    private List<CurrencyPair> symbolsToPairs(String symbols) {
        return Arrays.stream(symbols.split(","))
                .map(String::strip)
                .map(this::createPair)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private CurrencyPair createPair(String symbol) {
        if (symbol == null) {
            return null;
        }
        final String[] split = symbol.split("/");
        if (split.length != 2) {
            return null;
        }
        final Currency base = Currency.getInstanceNoCreate(split[0]);
        final Currency quote = Currency.getInstanceNoCreate(split[1]);
        if (base == null || quote == null) {
            return null;
        }
        return new CurrencyPair(base, quote);
    }
}
