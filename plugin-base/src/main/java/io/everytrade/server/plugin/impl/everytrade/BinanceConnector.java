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
import org.knowm.xchange.binance.BinanceExchange;
import org.knowm.xchange.binance.service.BinanceTradeHistoryParams;
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

public class BinanceConnector implements IConnector {

    private static final String ID = EveryTradePlugin.ID + IPlugin.PLUGIN_PATH_SEPARATOR + "binanceApiConnector";
    //org.knowm.xchange.binance.BinanceResilience - 1200 request / 1 minute --> 10 user in 1 minute will be enough
    private static final int MAX_REQUEST_COUNT = 120;
    private static final int TX_PER_REQUEST = 1000;

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

    private static final ConnectorParameterDescriptor PARAMETER_API_SYMBOLS =
        new ConnectorParameterDescriptor(
            "apiSymbols",
            ConnectorParameterType.STRING,
            "Trade pairs (e.g.: BTC/USDT,LTC/ETH)",
            ""
        );

    public static final ConnectorDescriptor DESCRIPTOR = new ConnectorDescriptor(
        ID,
        "Binance Connector",
        SupportedExchange.BINANCE.getInternalId(),
        List.of(PARAMETER_API_KEY, PARAMETER_API_SECRET, PARAMETER_API_SYMBOLS)
    );

    private final String apiKey;
    private final String apiSecret;
    private final String apiSymbols;

    public BinanceConnector(Map<String, String> parameters) {
        Objects.requireNonNull(this.apiKey = parameters.get(PARAMETER_API_KEY.getId()));
        Objects.requireNonNull(this.apiSecret = parameters.get(PARAMETER_API_SECRET.getId()));
        Objects.requireNonNull(this.apiSymbols = parameters.get(PARAMETER_API_SYMBOLS.getId()));
    }

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public DownloadResult getTransactions(String lastTransactionId) {
        final ExchangeSpecification exSpec = new BinanceExchange().getDefaultExchangeSpecification();
        exSpec.setApiKey(apiKey);
        exSpec.setSecretKey(apiSecret);
        final Exchange exchange = ExchangeFactory.INSTANCE.createExchange(exSpec);
        final TradeService tradeService = exchange.getTradeService();
        final BinanceDownloadState downloadState = BinanceDownloadState.parseFrom(lastTransactionId);

        List<UserTrade> userTrades = download(tradeService, downloadState);
        final ParseResult parseResult = XChangeConnectorParser.getParseResult(userTrades, SupportedExchange.BINANCE);

        return new DownloadResult(parseResult, downloadState.toLastTransactionId());
    }

    private List<UserTrade> download(
        TradeService tradeService,
        BinanceDownloadState downloadState
    ) {

        final BinanceTradeHistoryParams tradeHistoryParams
            = (BinanceTradeHistoryParams) tradeService.createTradeHistoryParams();
        tradeHistoryParams.setLimit(TX_PER_REQUEST);

        final List<CurrencyPair> currencyPairs = symbolsToPairs(apiSymbols);
        final List<UserTrade> userTrades = new ArrayList<>();
        int counter = 0;

        for (CurrencyPair currencyPair : currencyPairs) {
            tradeHistoryParams.setCurrencyPair(currencyPair);
            String lastDownloadedTx = downloadState.getLastTransactionId(currencyPair.toString());
            tradeHistoryParams.setStartId(lastDownloadedTx);

            while (counter++ < MAX_REQUEST_COUNT) {
                final List<UserTrade> userTradesBlock;
                try {
                    userTradesBlock = tradeService.getTradeHistory(tradeHistoryParams).getUserTrades();
                } catch (Exception e) {
                    throw new IllegalStateException("User trade history download failed. ", e);
                }
                if (
                    lastDownloadedTx != null
                        && !userTradesBlock.isEmpty()
                        && userTradesBlock.get(0).getId().equals(lastDownloadedTx)
                ) {
                    userTradesBlock.remove(0);
                }
                if (userTradesBlock.isEmpty()) {
                    break;
                }
                userTrades.addAll(userTradesBlock);
                lastDownloadedTx = userTradesBlock.get(userTradesBlock.size() - 1).getId();
                tradeHistoryParams.setStartId(lastDownloadedTx);
            }
            downloadState.updateLastTransactionId(currencyPair.toString(), lastDownloadedTx);
        }

        return userTrades;

    }

    @Override
    public void close() {
        //AutoCloseable
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
