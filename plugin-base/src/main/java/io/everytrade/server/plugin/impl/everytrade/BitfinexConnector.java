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
import org.knowm.xchange.bitfinex.BitfinexExchange;
import org.knowm.xchange.bitfinex.service.BitfinexTradeService;
import org.knowm.xchange.dto.trade.UserTrade;
import org.knowm.xchange.service.trade.TradeService;
import org.knowm.xchange.service.trade.params.TradeHistoryParamsSorted;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BitfinexConnector implements IConnector {

    private static final String ID = EveryTradePlugin.ID + IPlugin.PLUGIN_PATH_SEPARATOR + "bitfinexApiConnector";
    //https://docs.bitfinex.com/reference#rest-public-trades - 30 request / 1 minute, than 60 s no resp.
    private static final int MAX_REQUEST_COUNT = 5;
    private static final int TX_PER_REQUEST = 1000;
    private static final String TX_SPLITER = "|";
    private static final Pattern SPLIT_PATTERN = Pattern.compile(String.format("(.*)\\%s(.*)", TX_SPLITER));

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
            "Bitfinex Connector",
            SupportedExchange.BITFINEX.getInternalId(),
            List.of(PARAMETER_API_KEY, PARAMETER_API_SECRET)
    );

    private final String apiKey;
    private final String apiSecret;

    public BitfinexConnector(Map<String, String> parameters) {
        Objects.requireNonNull(this.apiKey = parameters.get(PARAMETER_API_KEY.getId()));
        Objects.requireNonNull(this.apiSecret = parameters.get(PARAMETER_API_SECRET.getId()));
    }

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public DownloadResult getTransactions(String lastTransactionId) {
        final ExchangeSpecification exSpec = new BitfinexExchange().getDefaultExchangeSpecification();
        exSpec.setApiKey(apiKey);
        exSpec.setSecretKey(apiSecret);
        final Exchange exchange = ExchangeFactory.INSTANCE.createExchange(exSpec);
        final TradeService tradeService = exchange.getTradeService();

        final List<UserTrade> userTrades = download(tradeService, lastTransactionId);

        final String actualLastTransactionId;
        if (!userTrades.isEmpty()) {
            final UserTrade userTrade = userTrades.get(userTrades.size() - 1);
            final String timeStamp = userTrade.getTimestamp().toInstant().toString();
            actualLastTransactionId = String.format("%s%s%s", timeStamp, TX_SPLITER, userTrade.getId());
        } else {
            actualLastTransactionId = lastTransactionId;
        }

        final ParseResult parseResult = XChangeConnectorParser.getParseResult(userTrades, SupportedExchange.BITFINEX);

        return new DownloadResult(parseResult, actualLastTransactionId);
    }

    private List<UserTrade> download(
            TradeService tradeService,
            String lastTransactionUid
    ) {
        final BitfinexTradeService.BitfinexTradeHistoryParams tradeHistoryParams
                = (BitfinexTradeService.BitfinexTradeHistoryParams) tradeService.createTradeHistoryParams();
        tradeHistoryParams.setLimit(TX_PER_REQUEST);
        tradeHistoryParams.setOrder(TradeHistoryParamsSorted.Order.asc);
        final List<UserTrade> userTrades = new ArrayList<>();
        TransactionIdentifier lastBlockDownloadedTx = parseFrom(lastTransactionUid);

        int counter = 0;
        while (counter++ < MAX_REQUEST_COUNT) {
            tradeHistoryParams.setStartTime(lastBlockDownloadedTx.date);
            final List<UserTrade> userTradesBlock;
            try {
                userTradesBlock = tradeService.getTradeHistory(tradeHistoryParams).getUserTrades();
            } catch (IOException e) {
                throw new IllegalStateException("User trade history download failed. ", e);
            }
            final List<UserTrade> userTradesToAdd;
            final int duplicityTxIndex = findDuplicity(lastBlockDownloadedTx, userTradesBlock);
            if (duplicityTxIndex > -1) {
                if (duplicityTxIndex < userTradesBlock.size() - 1) {
                    userTradesToAdd = userTradesBlock.subList(duplicityTxIndex + 1, userTradesBlock.size());
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
            lastBlockDownloadedTx = new TransactionIdentifier(userTradeLast.getTimestamp(), userTradeLast.getId());

            userTrades.addAll(userTradesToAdd);
        }

        return userTrades;
    }

    private TransactionIdentifier parseFrom(String lastTransactionUid) {
        if (lastTransactionUid == null) {
            return new TransactionIdentifier(Date.from(Instant.EPOCH), null);
        }
        Matcher matcher = SPLIT_PATTERN.matcher(lastTransactionUid);
        if (occurrenceCount(lastTransactionUid, TX_SPLITER) != 1 || !matcher.find()) {
            throw new IllegalArgumentException(
                    String.format("Illegal value of lastTransactionUid '%s'.", lastTransactionUid)
            );
        }
        final String date = matcher.group(1);
        try {
            return new TransactionIdentifier(
                    Date.from(Instant.parse(date)),
                    matcher.group(2)
            );
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    String.format(
                            "Illegal value of date part '%s' of lastTransactionUid '%s'.",
                            date,
                            lastTransactionUid
                    ),
                    e
            );
        }
    }

    private int findDuplicity(TransactionIdentifier transactionIdentifier, List<UserTrade> userTradesBlock) {
        for (int i = 0; i < userTradesBlock.size(); i++) {
            final UserTrade userTrade = userTradesBlock.get(i);
            if (userTrade.getTimestamp().equals(transactionIdentifier.date)
                    && userTrade.getId().equals(transactionIdentifier.id)
            ) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public void close() {
        //AutoCloseable
    }

    private static class TransactionIdentifier {
        private final Date date;
        private final String id;

        public TransactionIdentifier(Date date, String id) {
            this.date = date;
            this.id = id;
        }
    }

    public  int occurrenceCount(String input, String search) {
        int startIndex = 0;
        int index = 0;
        int counter = 0;
        while (index > -1 && startIndex < input.length()) {
            index = input.indexOf(search, startIndex);
            if (index > -1) {
                counter++;
            }
            startIndex = index + 1;
        }
        return counter;
    }
}