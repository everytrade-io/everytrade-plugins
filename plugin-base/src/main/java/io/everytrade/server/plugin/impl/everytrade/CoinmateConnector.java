package io.everytrade.server.plugin.impl.everytrade;

import io.everytrade.server.model.SupportedExchange;
import io.everytrade.server.plugin.api.IPlugin;
import io.everytrade.server.plugin.api.connector.ConnectorDescriptor;
import io.everytrade.server.plugin.api.connector.ConnectorParameterDescriptor;
import io.everytrade.server.plugin.api.connector.ConnectorParameterType;
import io.everytrade.server.plugin.api.connector.DownloadResult;
import io.everytrade.server.plugin.api.connector.IConnector;
import io.everytrade.server.plugin.api.parser.ParseResult;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.experimental.FieldDefaults;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.ExchangeFactory;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.coinmate.CoinmateException;
import org.knowm.xchange.coinmate.CoinmateExchange;
import org.knowm.xchange.coinmate.dto.trade.CoinmateTransactionHistoryEntry;
import org.knowm.xchange.coinmate.service.CoinmateTradeService;
import org.knowm.xchange.coinmate.service.CoinmateTradeServiceRaw;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static lombok.AccessLevel.PRIVATE;
import static org.apache.commons.lang3.StringUtils.isEmpty;

@AllArgsConstructor
@FieldDefaults(makeFinal = true, level = PRIVATE)
public class CoinmateConnector implements IConnector {

    private static final String ID = WhaleBooksPlugin.ID + IPlugin.PLUGIN_PATH_SEPARATOR + "coinmateApiConnector";
    private static final String SORT_DESC = "DESC";
    private static final long DELAY = 24 * 60 * 60 * 1000L;
    // MAX 100 request per minute per user, https://coinmate.docs.apiary.io/#reference/request-limits
    private static final int MAX_REQUEST_COUNT = 7;
    // https://coinmate.docs.apiary.io/#reference/transaction-history/get-transaction-history
    private static final int TX_PER_REQUEST = 990;
    private static final Logger LOG = LoggerFactory.getLogger(CoinmateConnector.class);

    private static final ConnectorParameterDescriptor PARAMETER_API_USERNAME =
        new ConnectorParameterDescriptor(
            "username",
            ConnectorParameterType.STRING,
            "Client ID",
            "",
            false
        );

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
        "Coinmate Connector",
        "",
        SupportedExchange.COINMATE.getInternalId(),
        List.of(PARAMETER_API_USERNAME, PARAMETER_API_KEY, PARAMETER_API_SECRET)
    );

    Exchange exchange;

    public CoinmateConnector(Map<String, String> parameters) {
        this(
            parameters.get(PARAMETER_API_USERNAME.getId()),
            parameters.get(PARAMETER_API_KEY.getId()),
            parameters.get(PARAMETER_API_SECRET.getId())
        );
    }

    public CoinmateConnector(@NonNull String username, @NonNull String apiKey, @NonNull String secret) {
        final ExchangeSpecification exSpec = new CoinmateExchange().getDefaultExchangeSpecification();
        exSpec.setUserName(username);
        exSpec.setApiKey(apiKey);
        exSpec.setSecretKey(secret);
        exSpec.setHttpReadTimeout(Integer.MAX_VALUE);
        this.exchange = ExchangeFactory.INSTANCE.createExchange(exSpec);
    }

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public DownloadResult getTransactions(String stateStr) {
        var downloadState = DownloadState.deserialize(stateStr);
        var transactions = downloadTransactions(downloadState);
        ParseResult parseResult = new XChangeConnectorParser().getCoinMateResult(transactions);
        return new DownloadResult(parseResult, downloadState.serialize());
    }

    private List<CoinmateTransactionHistoryEntry> downloadTransactions(DownloadState state) {
        var rawServices = new CoinmateTradeServiceRaw(exchange);
        setTxFromTimestamp(state);
        List<CoinmateTransactionHistoryEntry> allData = new ArrayList<>();

        long now = Instant.now().toEpochMilli();
        long txFrom = state.getTxFrom();
        long txTo = state.getTxTo() == 0L ? now : state.getTxTo();
        int offset = state.getOffset();
        int sentRequests = 0;
        while (sentRequests < MAX_REQUEST_COUNT) {
            final List<CoinmateTransactionHistoryEntry> userTransactionBlock;
            try {
                userTransactionBlock = rawServices.getCoinmateTransactionHistory(
                    offset, TX_PER_REQUEST, SORT_DESC, txFrom, txTo, null).getData();
            } catch (IOException e) {
                throw new IllegalStateException("Download user trade history failed.", e);
            }
            if (userTransactionBlock.isEmpty()) {
                state.offset = 0;
                state.txFrom = txTo;
                state.txTo = 0L;
                break;
            }
            if(userTransactionBlock.size() < TX_PER_REQUEST) {
                state.offset = 0;
                state.txFrom = txTo;
                state.txTo = 0L;
                allData.addAll(userTransactionBlock);
                break;
            } else {
                txTo = now;
                offset += TX_PER_REQUEST;
                state.txTo = now;
                state.offset = offset;
                allData.addAll(userTransactionBlock);
            }
            ++sentRequests;
        }
        return allData;
    }

    /**
     * We used to use two separate endpoints (tradehistory - buy/sell, transferHistory - deposit/withdrawal) but
     * switch to transactionHistory where are both data.
     *
     * @param state
     */
    private void setTxFromTimestamp(DownloadState state) {
        List<Long> timestamps = new ArrayList<>();
        if (state.txFrom == 0L) {
            // Old tradeHistory endpoint sent lastTxId but we require timestamp
            long lastTradeTimestamp;
            if (!"".equals(state.lastTradeId) && !"0".equals(state.lastTradeId)) {
                try {
                    var tradeService = exchange.getTradeService();
                    var params = (CoinmateTradeService.CoinmateTradeHistoryHistoryParams) tradeService.createTradeHistoryParams();
                    params.setStartId(state.lastTradeId);
                    params.setLimit(1);
                    var userTradesBlock = tradeService.getTradeHistory(params).getUserTrades();
                    lastTradeTimestamp = userTradesBlock.get(0).getTimestamp().getTime() - (1 * 1000L);
                } catch (Exception e) {
                    LOG.error("Cannot download last tx timestamp %s", e.getMessage());
                    throw new CoinmateException(String.format("Cannot download last tx timestamp %s", e.getMessage()));
                }
            } else {
                lastTradeTimestamp = 0L;
            }
            timestamps.add(lastTradeTimestamp);
            timestamps.add(state.fundingsFrom);
            timestamps.add(state.txFrom);
            state.txFrom = timestamps.stream().max(Long::compareTo).get();
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @FieldDefaults(level = PRIVATE)
    private static class DownloadState {
        private static final String SEPARATOR = "=";
        String lastTradeId = "";
        Long fundingsFrom = 0L;
        long txFrom;
        long txTo;
        int offset;

        public static DownloadState deserialize(String state) {
            if (isEmpty(state)) {
                return new DownloadState();
            }
            var strA = state.split(SEPARATOR);
            return new DownloadState(
                strA[0],
                strA.length > 1 ? Long.parseLong(strA[1]) : 0,
                strA.length > 2 ? Long.parseLong(strA[2]) : 0,
                strA.length > 3 ? Long.parseLong(strA[3]) : 0,
                strA.length > 4 ? Integer.parseInt(strA[4]) : 0
            );
        }

        // new endpoint requires only state txFrom and txTo
        public String serialize() {
            return lastTradeId + SEPARATOR + fundingsFrom + SEPARATOR + txFrom + SEPARATOR + txTo + SEPARATOR + offset;
        }

    }
}
