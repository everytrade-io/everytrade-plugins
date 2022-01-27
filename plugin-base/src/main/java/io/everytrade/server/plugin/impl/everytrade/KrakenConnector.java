package io.everytrade.server.plugin.impl.everytrade;

import io.everytrade.server.model.SupportedExchange;
import io.everytrade.server.plugin.api.IPlugin;
import io.everytrade.server.plugin.api.connector.ConnectorDescriptor;
import io.everytrade.server.plugin.api.connector.ConnectorParameterDescriptor;
import io.everytrade.server.plugin.api.connector.ConnectorParameterType;
import io.everytrade.server.plugin.api.connector.DownloadResult;
import io.everytrade.server.plugin.api.connector.IConnector;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.ExchangeFactory;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.dto.account.FundingRecord;
import org.knowm.xchange.dto.trade.UserTrade;
import org.knowm.xchange.kraken.KrakenExchange;
import org.knowm.xchange.kraken.service.KrakenAccountService;
import org.knowm.xchange.kraken.service.KrakenTradeHistoryParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static java.util.Collections.emptyList;
import static lombok.AccessLevel.PRIVATE;
import static org.knowm.xchange.dto.account.FundingRecord.Type.DEPOSIT;
import static org.knowm.xchange.dto.account.FundingRecord.Type.WITHDRAWAL;

@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = PRIVATE)
public class KrakenConnector implements IConnector {
    private static final Logger LOG = LoggerFactory.getLogger(KrakenConnector.class);
    private static final String ID = EveryTradePlugin.ID + IPlugin.PLUGIN_PATH_SEPARATOR + "krkApiConnector";

    // According Kraken rules https://support.kraken.com/hc/en-us/articles/206548367-What-are-the-API-rate-limits-
    private static final int MAX_TRADE_REQUESTS_COUNT = 7;
    private static final int MAX_FUNDING_REQUESTS_COUNT = 14;
    private static final int FUNDING_MAX_RESPONSE_SIZE = 50;

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
        "",
        SupportedExchange.KRAKEN.getInternalId(),
        List.of(PARAMETER_API_KEY, PARAMETER_API_SECRET)
    );

    Exchange exchange;

    public KrakenConnector(Map<String, String> parameters) {
        this(
            parameters.get(PARAMETER_API_KEY.getId()),
            parameters.get(PARAMETER_API_SECRET.getId())
        );
    }

    public KrakenConnector(@NonNull String apiKey, @NonNull String apiSecret) {
        ExchangeSpecification exSpec = new KrakenExchange().getDefaultExchangeSpecification();
        exSpec.setApiKey(apiKey);
        exSpec.setSecretKey(apiSecret);
        this.exchange = ExchangeFactory.INSTANCE.createExchange(exSpec);
    }

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public DownloadResult getTransactions(String downloadStateStr) {
        KrakenDownloadState downloadState = KrakenDownloadState.deserialize(downloadStateStr);

        var userTrades = new ArrayList<UserTrade>();
        var funding = new ArrayList<FundingRecord>();
        int sentRequests = 0;
        while (sentRequests < MAX_TRADE_REQUESTS_COUNT) {
            var downloadResult = downloadTrades(downloadState);
            if (downloadResult.isEmpty()) {
                break;
            }
            userTrades.addAll(downloadResult);
            ++sentRequests;
        }

        sentRequests = 0;
        while (sentRequests < MAX_FUNDING_REQUESTS_COUNT) {
            var downloadResult = downloadDepositsAndWithdrawals(downloadState);
            funding.addAll(downloadResult);
            ++sentRequests;
            if (downloadResult.size() < FUNDING_MAX_RESPONSE_SIZE) {
                break;
            }
        }

        return new DownloadResult(
            new XChangeConnectorParser().getParseResult(userTrades, funding),
            downloadState.serialize()
        );
    }

    private List<UserTrade> downloadTrades(KrakenDownloadState state) {
        var tradeService = exchange.getTradeService();
        var krakenTradeHistoryParams = (KrakenTradeHistoryParams) tradeService.createTradeHistoryParams();

        if (!state.hasEmptyState()) {
            krakenTradeHistoryParams.setStartId(state.getTradeLastContinuousTxUid());
        }

        var isGap = state.getTradeFirstTxUidAfterGap() != null;
        if (isGap) {
            krakenTradeHistoryParams.setEndId(state.getTradeFirstTxUidAfterGap());
        }

        final List<UserTrade> downloadedBlock;
        try {
            downloadedBlock = tradeService.getTradeHistory(krakenTradeHistoryParams).getUserTrades();
        } catch (IOException e) {
            throw new IllegalStateException("Download user trade history failed.", e);
        }

        if (downloadedBlock.isEmpty()) {
            LOG.debug("No funding in kraken.");
            return emptyList();
        }

        if (isGap) {
            // because of KrakenTradeHistoryParams#setEndId is inclusive
            downloadedBlock.remove(downloadedBlock.size() - 1);
        }

        final boolean noDataLeft = downloadedBlock.isEmpty();
        if (noDataLeft) {
            state.setTradeLastContinuousTxUid(state.getTradeLastTxUidAfterGap());
            state.setTradeFirstTxUidAfterGap(null);
            state.setTradeLastTxUidAfterGap(null);
            return emptyList();
        }

        final UserTrade firstDownloadedTx = downloadedBlock.get(0);
        state.setTradeFirstTxUidAfterGap(firstDownloadedTx.getId());
        if (state.getTradeLastTxUidAfterGap() == null) {
            final UserTrade lastDownloadedTx = downloadedBlock.get(downloadedBlock.size() - 1);
            state.setTradeLastTxUidAfterGap(lastDownloadedTx.getId());
        }

        return downloadedBlock;
    }

    private List<FundingRecord> downloadDepositsAndWithdrawals(KrakenDownloadState state) {
        var accountService = exchange.getAccountService();
        var result = new ArrayList<FundingRecord>();

        for (FundingRecord.Type type : List.of(DEPOSIT, WITHDRAWAL)) {
            var params = (KrakenAccountService.KrakenFundingHistoryParams) accountService.createFundingHistoryParams();
            params.setType(type);
            if (type == DEPOSIT && state.getDepositFromTimestamp() != null) {
                params.setStartTime(new Date(state.getDepositFromTimestamp()));
            } else if (type == WITHDRAWAL && state.getWithdrawalFromTimestamp() != null) {
                params.setStartTime(new Date(state.getWithdrawalFromTimestamp()));
            }

            final List<FundingRecord> downloadedBlock;
            try {
                downloadedBlock = accountService.getFundingHistory(params);
            } catch (IOException e) {
                throw new IllegalStateException("Download user trade history failed.", e);
            }

            if (downloadedBlock.isEmpty()) {
                LOG.info("No transactions in Kraken user history.");
                continue;
            }

            var lastDate = downloadedBlock.stream().map(it -> it.getDate().getTime()).max(Long::compare).get() + 1000;
            if (type == DEPOSIT) {
                state.setDepositFromTimestamp(lastDate);
            } else {
                state.setWithdrawalFromTimestamp(lastDate);
            }
            result.addAll(downloadedBlock);
        }
        return result;
    }
}
