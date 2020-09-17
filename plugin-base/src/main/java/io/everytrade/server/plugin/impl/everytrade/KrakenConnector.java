package io.everytrade.server.plugin.impl.everytrade;

import io.everytrade.server.plugin.api.connector.ConnectorParameterType;
import io.everytrade.server.model.SupportedExchange;
import io.everytrade.server.plugin.api.connector.DownloadResult;
import io.everytrade.server.plugin.api.IPlugin;
import io.everytrade.server.plugin.api.connector.ConnectorDescriptor;
import io.everytrade.server.plugin.api.connector.ConnectorParameterDescriptor;
import io.everytrade.server.plugin.api.connector.IConnector;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.ExchangeFactory;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.dto.trade.UserTrade;
import org.knowm.xchange.kraken.KrakenExchange;
import org.knowm.xchange.kraken.service.KrakenTradeService;
import org.knowm.xchange.service.trade.TradeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class KrakenConnector implements IConnector {
    private final Logger log = LoggerFactory.getLogger(this.getClass());
    // According Kraken rules https://support.kraken.com/hc/en-us/articles/206548367-What-are-the-API-rate-limits-
    private static final int MAX_REQUESTS_COUNT = 7;
    private static final String ID = EveryTradePlugin.ID + IPlugin.PLUGIN_PATH_SEPARATOR + "krkApiConnector";

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
        "Kraken Connector",
        SupportedExchange.KRAKEN.getInternalId(),
        List.of(PARAMETER_API_KEY, PARAMETER_API_SECRET)
    );

    private final String apiKey;
    private final String apiSecret;

    public KrakenConnector(Map<String, String> parameters) {
        this(
            parameters.get(PARAMETER_API_KEY.getId()),
            parameters.get(PARAMETER_API_SECRET.getId())
        );
    }

    public KrakenConnector(String apiKey, String apiSecret) {
        Objects.requireNonNull(this.apiKey = apiKey);
        Objects.requireNonNull(this.apiSecret = apiSecret);
    }

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public DownloadResult getTransactions(String lastTransactionUid) {
        final KrakenDownloadState downloadState = KrakenDownloadState.parseFrom(lastTransactionUid);
        final ExchangeSpecification exSpec = new KrakenExchange().getDefaultExchangeSpecification();
        exSpec.setApiKey(apiKey);
        exSpec.setSecretKey(apiSecret);
        final Exchange exchange = ExchangeFactory.INSTANCE.createExchange(exSpec);
        final TradeService tradeService = exchange.getTradeService();

        final boolean firstDownload = downloadState.getLastContinuousTxUid() == null;

        final List<UserTrade> userTrades = new ArrayList<>();
        int sentRequests = 0;
        while (sentRequests < MAX_REQUESTS_COUNT) {
            final List<UserTrade> downloadResult = download(tradeService, firstDownload, downloadState);
            if (downloadResult.isEmpty()) {
                break;
            }
            userTrades.addAll(downloadResult);
            ++sentRequests;
        }

        return new DownloadResult(XChangeConnectorParser.getParseResult(userTrades, SupportedExchange.KRAKEN),
            downloadState.toLastDownloadedTxUid());
    }

    private List<UserTrade> download(TradeService tradeService, boolean firstDownload, KrakenDownloadState state) {
        final KrakenTradeService.KrakenTradeHistoryParams krakenTradeHistoryParams =
            (KrakenTradeService.KrakenTradeHistoryParams) tradeService.createTradeHistoryParams();

        if (!firstDownload) {
            krakenTradeHistoryParams.setStartId(state.getLastContinuousTxUid());
        }

        final boolean isGap = state.getFirstTxUidAfterGap() != null;
        if (isGap) {
            krakenTradeHistoryParams.setEndId(state.getFirstTxUidAfterGap());
        }

        final List<UserTrade> downloadedBlock;
        try {
            downloadedBlock = tradeService.getTradeHistory(krakenTradeHistoryParams).getUserTrades();
        } catch (IOException e) {
            throw new IllegalStateException("Download user trade history failed.", e);
        }

        if (downloadedBlock.isEmpty()) {
            log.info("No transactions in Kraken user history.");
            return Collections.emptyList();
        }

        if (isGap) {
            // because of KrakenTradeHistoryParams#setEndId is inclusive
            downloadedBlock.remove(downloadedBlock.size() - 1);
        }

        final boolean noDataLeft = downloadedBlock.isEmpty();
        if (noDataLeft) {
            state.setLastContinuousTxUid(state.getLastTxUidAfterGap());
            state.setFirstTxUidAfterGap(null);
            state.setLastTxUidAfterGap(null);
            return Collections.emptyList();
        }

        final UserTrade firstDownloadedTx = downloadedBlock.get(0);
        state.setFirstTxUidAfterGap(firstDownloadedTx.getId());
        if (state.getLastTxUidAfterGap() == null) {
            final UserTrade lastDownloadedTx = downloadedBlock.get(downloadedBlock.size() - 1);
            state.setLastTxUidAfterGap(lastDownloadedTx.getId());
        }

        return downloadedBlock;

    }

    @Override
    public void close() {
        //AutoCloseable
    }
}
