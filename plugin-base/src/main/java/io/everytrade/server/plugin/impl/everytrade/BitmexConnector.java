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
import org.knowm.xchange.bitmex.BitmexExchange;
import org.knowm.xchange.bitmex.service.BitmexTradeHistoryParams;
import org.knowm.xchange.dto.trade.UserTrade;
import org.knowm.xchange.service.trade.TradeService;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static io.everytrade.server.plugin.impl.everytrade.ConnectorUtils.findDuplicate;

public class BitmexConnector implements IConnector {
    private static final String ID = EveryTradePlugin.ID + IPlugin.PLUGIN_PATH_SEPARATOR + "bitmexApiConnector";
    //https://www.bitmex.com/app/restAPI#Limits - max 60 requests per minute
    private static final int MAX_REQUESTS_PER_MINUTE = 60;
    //assumption that usually less then 20.000  new transactions
    private static final int FAST_REQUESTS = 40;
    private static final int SLOW_REQUESTS = MAX_REQUESTS_PER_MINUTE - FAST_REQUESTS;
    private static final Duration SLEEP_BETWEEN_SLOW_REQUESTS
        = Duration.ofSeconds(MAX_REQUESTS_PER_MINUTE/SLOW_REQUESTS);
    private static final Duration SLEEP_BETWEEN_REQUESTS = Duration.ofSeconds(1);
    private static final int MAX_REQUESTS = 600;
    private static final int MAX_TXS_PER_REQUEST = 500;
    private static final String LAST_TX_ID_FORMAT = "%s:%s";
    private static final Pattern LAST_TX_ID_SPLITTER = Pattern.compile("^([^:]*):([^:]*)$");

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
        "BitMEX Connector",
        SupportedExchange.BITMEX.getInternalId(),
        List.of(PARAMETER_API_KEY, PARAMETER_API_SECRET)
    );


    private final String apiKey;
    private final String apiSecret;

    public BitmexConnector(Map<String, String> parameters) {
        Objects.requireNonNull(this.apiKey = parameters.get(PARAMETER_API_KEY.getId()));
        Objects.requireNonNull(this.apiSecret = parameters.get(PARAMETER_API_SECRET.getId()));
    }

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public DownloadResult getTransactions(String lastTransactionId) {
        final ExchangeSpecification exSpec = new BitmexExchange().getDefaultExchangeSpecification();
        exSpec.setApiKey(apiKey);
        exSpec.setSecretKey(apiSecret);
        final Exchange exchange = ExchangeFactory.INSTANCE.createExchange(exSpec);
        final TradeService tradeService = exchange.getTradeService();

        final TransactionIdentifier lastTransactionIdentifier = parseFrom(lastTransactionId);
        final List<UserTrade> userTrades = download(lastTransactionIdentifier, tradeService);

        final String actualLastTransactionId;
        if (!userTrades.isEmpty()) {
            final String lastUserTradeId = userTrades.get(userTrades.size() - 1).getId();
            final long lastUserTradeOffset = lastTransactionIdentifier.offset + userTrades.size();
            actualLastTransactionId = String.format(LAST_TX_ID_FORMAT, lastUserTradeOffset, lastUserTradeId);
        } else {
            actualLastTransactionId = lastTransactionId;
        }

        final ParseResult parseResult = XChangeConnectorParser.getParseResult(userTrades, SupportedExchange.BITMEX);

        return new DownloadResult(parseResult, actualLastTransactionId);
    }

    private List<UserTrade> download(TransactionIdentifier lastTransactionId, TradeService tradeService) {
        final BitmexTradeHistoryParams tradeHistoryParams
            = (BitmexTradeHistoryParams) tradeService.createTradeHistoryParams();
        tradeHistoryParams.setLimit(MAX_TXS_PER_REQUEST);
        final List<UserTrade> userTrades = new ArrayList<>();
        TransactionIdentifier lastDownloadedTx
            = new TransactionIdentifier(lastTransactionId.offset, lastTransactionId.id);
        int sentRequests = 0;

        while (sentRequests < MAX_REQUESTS) {
            sleep(sentRequests);
            tradeHistoryParams.setOffset(lastDownloadedTx.offset);
            final List<UserTrade> userTradesBlock;
            try {
                userTradesBlock = tradeService.getTradeHistory(tradeHistoryParams).getUserTrades();
            } catch (IOException e) {
                throw new IllegalStateException("User trade history download failed. ", e);
            }
            final List<UserTrade> userTradesToAdd;
            final int duplicateTxIndex = findDuplicate(lastDownloadedTx.id, userTradesBlock);
            if (duplicateTxIndex > -1) {
                if (duplicateTxIndex < userTradesBlock.size() - 1) {
                    userTradesToAdd = userTradesBlock.subList(duplicateTxIndex + 1, userTradesBlock.size());
                } else {
                    userTradesToAdd = List.of();
                }
            } else {
                userTradesToAdd = userTradesBlock;
            }

            if (userTradesToAdd.isEmpty()) {
                break;
            }

            final UserTrade userTradeLast = userTradesToAdd.get(userTradesToAdd.size() - 1);
            final long actualOffset = lastTransactionId.offset + userTradesBlock.size();
            lastDownloadedTx = new TransactionIdentifier(actualOffset, userTradeLast.getId());

            userTrades.addAll(userTradesToAdd);
            ++sentRequests;
        }

        return userTrades;
    }

    private void sleep(int sentRequests) {
        final Duration sleep;
        if (sentRequests < FAST_REQUESTS) {
            return;
        } else if (sentRequests < FAST_REQUESTS + SLOW_REQUESTS) {
            sleep = SLEEP_BETWEEN_SLOW_REQUESTS;
        } else {
            sleep = SLEEP_BETWEEN_REQUESTS;
        }

        try {
            Thread.sleep(sleep.toMillis());
        } catch (InterruptedException e) {
            throw new IllegalStateException("User trade history download sleep interrupted.", e);
        }
    }

    private TransactionIdentifier parseFrom(String lastTransactionUid) {
        if (lastTransactionUid == null) {
            return new TransactionIdentifier(0L, null);
        }
        Matcher matcher = LAST_TX_ID_SPLITTER.matcher(lastTransactionUid);
        if (!matcher.find()) {
            throw new IllegalArgumentException(
                String.format("Illegal value of lastTransactionUid '%s'.", lastTransactionUid)
            );
        }
        final String offset = matcher.group(1);
        try {
            return new TransactionIdentifier(Long.parseLong(offset), matcher.group(2));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                String.format(
                    "Illegal value of offset part '%s' of lastTransactionUid '%s'.",
                    offset,
                    lastTransactionUid
                ),
                e
            );
        }
    }

    @Override
    public void close() {
        //AutoCloseable
    }

    private static class TransactionIdentifier {
        private final long offset;
        private final String id;

        public TransactionIdentifier(long offset, String id) {
            this.offset = offset;
            this.id = id;
        }
    }

}
