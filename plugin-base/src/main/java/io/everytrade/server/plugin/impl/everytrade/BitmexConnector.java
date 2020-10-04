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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static io.everytrade.server.plugin.impl.everytrade.ConnectorUtils.findDuplicate;

public class BitmexConnector implements IConnector {
    private static final String ID = EveryTradePlugin.ID + IPlugin.PLUGIN_PATH_SEPARATOR + "bitmexApiConnector";
    //https://www.bitmex.com/app/restAPI#Limits - max 60 requests per minute --> 60 rqsts in minute, then 1 per sec
    private static final int MAX_REQUESTS_COUNT_PER_MINUTE = 60;
    private static final int MAX_REQUESTS_COUNT = 660;
    private static final int SLEEP_BETWEEN_REQUESTS_MS = 1000;
    private static final int MAX_TXS_PER_REQUEST = 500;
    private static final String TX_SPLITER = ":";
    private static final Pattern SPLIT_PATTERN = Pattern.compile(
        String.format("^([^\\%1$s]*)\\%1$s([^\\%1$s]*)$", TX_SPLITER)
    );

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
            actualLastTransactionId = String.format("%s%s%s", lastUserTradeOffset, TX_SPLITER, lastUserTradeId);
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
        final long requestStart = System.currentTimeMillis();

        while (sentRequests < MAX_REQUESTS_COUNT) {
            if (sentRequests >= MAX_REQUESTS_COUNT_PER_MINUTE) {
                sleep(sentRequests, requestStart);
            }
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

    private void sleep(int sentRequests, long requestStart) {
        long sleepTime;
        if (sentRequests == MAX_REQUESTS_COUNT_PER_MINUTE) {
            final long maxRequestsDuration = System.currentTimeMillis() - requestStart;
            sleepTime = 60_000L - maxRequestsDuration;
        } else {
            sleepTime = SLEEP_BETWEEN_REQUESTS_MS;
        }
        if (sleepTime > 0) {
            try {
                Thread.sleep(sleepTime);
            } catch (InterruptedException e) {
                throw new IllegalStateException("User trade history download sleep interrupted.", e);
            }
        }
    }

    private TransactionIdentifier parseFrom(String lastTransactionUid) {
        if (lastTransactionUid == null) {
            return new TransactionIdentifier(0L, null);
        }
        Matcher matcher = SPLIT_PATTERN.matcher(lastTransactionUid);
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
