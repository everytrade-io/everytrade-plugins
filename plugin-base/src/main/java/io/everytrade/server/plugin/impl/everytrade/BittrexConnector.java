package io.everytrade.server.plugin.impl.everytrade;

import io.everytrade.server.model.SupportedExchange;
import io.everytrade.server.plugin.api.IPlugin;
import io.everytrade.server.plugin.api.connector.ConnectorDescriptor;
import io.everytrade.server.plugin.api.connector.ConnectorParameterDescriptor;
import io.everytrade.server.plugin.api.connector.ConnectorParameterType;
import io.everytrade.server.plugin.api.connector.DownloadResult;
import io.everytrade.server.plugin.api.connector.IConnector;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.experimental.FieldDefaults;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.ExchangeFactory;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.bittrex.BittrexExchange;
import org.knowm.xchange.bittrex.dto.account.BittrexDepositHistory;
import org.knowm.xchange.bittrex.dto.account.BittrexWithdrawalHistory;
import org.knowm.xchange.bittrex.service.BittrexAccountServiceRaw;

import org.knowm.xchange.bittrex.service.BittrexTradeHistoryParams;
import org.knowm.xchange.dto.trade.UserTrade;
import org.knowm.xchange.service.trade.TradeService;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static lombok.AccessLevel.PRIVATE;
import static org.apache.commons.lang3.StringUtils.isEmpty;

@AllArgsConstructor
@FieldDefaults(level = PRIVATE, makeFinal = true)
public class BittrexConnector implements IConnector {
    private static final String ID = EveryTradePlugin.ID + IPlugin.PLUGIN_PATH_SEPARATOR + "bittrexApiConnector";

    private static final int DEPOSIT_WITHDRAWAL_PAGE_SIZE = 50;
    private static final int TRADE_PAGE_SIZE = 200;
    private static final int MAX_REQUEST_COUNT = 3;

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
        "Bittrex Connector",
        "",
        SupportedExchange.BITTREX.getInternalId(),
        List.of(PARAMETER_API_KEY, PARAMETER_API_SECRET)
    );

    Exchange exchange;

    public BittrexConnector(Map<String, String> parameters) {
        this(parameters.get(PARAMETER_API_KEY.getId()), parameters.get(PARAMETER_API_SECRET.getId()));
    }

    public BittrexConnector(@NonNull String apiKey, @NonNull String secret) {
        final ExchangeSpecification exSpec = new BittrexExchange().getDefaultExchangeSpecification();
        exSpec.setApiKey(apiKey);
        exSpec.setSecretKey(secret);
        this.exchange = ExchangeFactory.INSTANCE.createExchange(exSpec);
    }

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public DownloadResult getTransactions(String stateStr) {
        var downloadState = DownloadState.deserialize(stateStr);

        List<UserTrade> userTrades = downloadTrades(downloadState);
        List<BittrexDepositHistory> deposits = downloadDeposits(downloadState);
        List<BittrexWithdrawalHistory> withdrawals = downloadWithdrawals(downloadState);

        return new DownloadResult(
            new XChangeConnectorParser().getBittrexResult(userTrades, deposits, withdrawals),
            downloadState.serialize()
        );
    }

    private List<UserTrade> downloadTrades(DownloadState downloadState) {
        final List<UserTrade> trades = new ArrayList<>();
        TradeService tradeService = exchange.getTradeService();
        BittrexTradeHistoryParams params = (BittrexTradeHistoryParams) tradeService.createTradeHistoryParams();
        String startTxTime = downloadState.getStartTxTime();
        String endTxTime = downloadState.getEndTxTime();

        if (startTxTime != null && endTxTime != null && Long.parseLong(endTxTime) > Long.parseLong(startTxTime)) {
            params.setStartTime(new Date(Long.parseLong(downloadState.getStartTxTime())));
        }

        if (startTxTime != null && endTxTime == null) {
            params.setStartTime(new Date(Long.parseLong(downloadState.getStartTxTime())));
        }

        if(endTxTime != null) {
            params.setEndTime(new Date(Long.parseLong(downloadState.getEndTxTime())));
        }
        int sentRequests = 0;
        while (sentRequests < MAX_REQUEST_COUNT) {
            ++sentRequests;
            final List<UserTrade> userTradesBlock;
            try {
                userTradesBlock = tradeService.getTradeHistory(params).getUserTrades();
            } catch (Exception e) {
                throw new IllegalStateException("User trade history download failed.", e);
            }

            if(downloadState.getEndTxTime() == null && !userTradesBlock.isEmpty()) {
                long startTime = userTradesBlock.get(userTradesBlock.size() - 1).getTimestamp().getTime() + (1 * 1000); // add one second;
                downloadState.setStartTxTime(String.valueOf(startTime));
            }
            if(userTradesBlock.isEmpty()) {
                downloadState.setEndTxTime(null);
                break;
            }
            Date timestamp = userTradesBlock.get(0).getTimestamp();
            params.setEndTime(timestamp);
            downloadState.setEndTxTime(String.valueOf(timestamp.getTime()));
            Collections.reverse(userTradesBlock);
            trades.addAll(userTradesBlock);
        }
        return trades;
    }


    private List<BittrexDepositHistory> downloadDeposits(DownloadState downloadState) {
        var accountService = (BittrexAccountServiceRaw) exchange.getAccountService();
        List<BittrexDepositHistory> deposits = new ArrayList<>();

        String startStateItemId = downloadState.getStartDepositId();
        String endStateItemId = downloadState.getEndDepositId();

        String startItemId = null;
        String endItemId = null;

        if (startStateItemId != null && endStateItemId == null) {
            startItemId = startStateItemId;
        }

        if(endStateItemId != null) {
            endItemId = endStateItemId;
        }

        int sentRequests = 0;
        while (sentRequests < MAX_REQUEST_COUNT) {
            ++sentRequests;
            final List<BittrexDepositHistory> depositsBlock = new ArrayList<>();

            try {
                depositsBlock.addAll(accountService.getBittrexDepositsClosed(
                    null, endItemId, startItemId, DEPOSIT_WITHDRAWAL_PAGE_SIZE
                ));
            } catch (IOException e) {
                throw new IllegalStateException("User deposit history download failed.", e);
            }
            // first round
            if(endItemId == null && !depositsBlock.isEmpty()) {
                String id = depositsBlock.get(0).getId(); // add one
                downloadState.setStartDepositId(id);
            }

            if(depositsBlock.isEmpty() || depositsBlock.size() < DEPOSIT_WITHDRAWAL_PAGE_SIZE) {
                downloadState.setEndDepositId(null);
                break;
            }
            endItemId = depositsBlock.get(depositsBlock.size() - 1).getId();
            downloadState.setEndDepositId(endItemId);
            deposits.addAll(depositsBlock);
        }
        return deposits;
    }

    private List<BittrexWithdrawalHistory> downloadWithdrawals(DownloadState downloadState) {
        var accountService = (BittrexAccountServiceRaw) exchange.getAccountService();
        List<BittrexWithdrawalHistory> withdrawals = new ArrayList<>();

        String startStateItemId = downloadState.getStartWithdrawalId();
        String endStateItemId = downloadState.getEndWithdrawalId();

        String startItemId = null;
        String endItemId = null;

        if (startStateItemId != null && endStateItemId == null) {
            startItemId = startStateItemId;
        }

        if(endStateItemId != null) {
            endItemId = endStateItemId;
        }

        int sentRequests = 0;
        while (sentRequests < MAX_REQUEST_COUNT) {
            ++sentRequests;
            final List<BittrexWithdrawalHistory> withdrawalBlock = new ArrayList<>();

            try {
                withdrawalBlock.addAll(accountService.getBittrexWithdrawalsClosed(
                    null, endItemId, startItemId, DEPOSIT_WITHDRAWAL_PAGE_SIZE
                ));
            } catch (IOException e) {
                throw new IllegalStateException("User withdrawal history download failed.", e);
            }
            // first round
            if(endItemId == null && !withdrawalBlock.isEmpty()) {
                String id = withdrawalBlock.get(0).getId(); // add one
                downloadState.setStartWithdrawalId(id);
            }

            if(withdrawalBlock.isEmpty() || withdrawalBlock.size() < DEPOSIT_WITHDRAWAL_PAGE_SIZE) {
                downloadState.setEndWithdrawalId(null);
                break;
            }
            endItemId = withdrawalBlock.get(withdrawalBlock.size() - 1).getId();
            downloadState.setEndDepositId(endItemId);
            withdrawals.addAll(withdrawalBlock);
        }
        return withdrawals;
    }


    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @FieldDefaults(level = PRIVATE)
    private static class DownloadState {
        private static final String SEPARATOR = "=";

        String startTxTime;
        String endTxTime;
        String startDepositId;
        String endDepositId;
        String startWithdrawalId;
        String endWithdrawalId;

        public static DownloadState deserialize(String state) {
            if (isEmpty(state)) {
                return new DownloadState();
            }
            var stringState = state.split(SEPARATOR);

            return new DownloadState(
                stringState[0].equals(" ") ? null : stringState[0],
                stringState[1].equals(" ") ? null : stringState[1],
                stringState[2].equals(" ") ? null : stringState[2],
                stringState[3].equals(" ") ? null : stringState[3],
                stringState[4].equals(" ") ? null : stringState[4],
                stringState[5].equals(" ") ? null : stringState[5]
            );
        }

        public String serialize() {
            return
                (startTxTime == null ? " " : startTxTime) + SEPARATOR +
                (endTxTime == null ? " " : endTxTime) + SEPARATOR +
                (startDepositId == null ? " " : startDepositId) + SEPARATOR +
                (endDepositId == null ? " " : endDepositId) + SEPARATOR +
                (startWithdrawalId == null ? " " : startWithdrawalId) + SEPARATOR +
                (endWithdrawalId == null ? " " : endWithdrawalId);
        }
    }
}