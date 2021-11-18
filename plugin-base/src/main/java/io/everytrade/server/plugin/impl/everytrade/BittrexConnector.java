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
import org.knowm.xchange.dto.trade.UserTrade;
import org.knowm.xchange.service.trade.TradeService;
import org.knowm.xchange.service.trade.params.TradeHistoryParams;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.google.common.base.Strings.emptyToNull;
import static io.everytrade.server.plugin.impl.everytrade.ConnectorUtils.findDuplicateTransaction;
import static lombok.AccessLevel.PRIVATE;
import static org.apache.commons.lang3.StringUtils.isEmpty;

@AllArgsConstructor
@FieldDefaults(level = PRIVATE, makeFinal = true)
public class BittrexConnector implements IConnector {
    private static final String ID = EveryTradePlugin.ID + IPlugin.PLUGIN_PATH_SEPARATOR + "bittrexApiConnector";

    private static final int DEPOSIT_WITHDRAWAL_PAGE_SIZE = 200;

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
        TradeService tradeService = exchange.getTradeService();
        TradeHistoryParams params = tradeService.createTradeHistoryParams();
        List<UserTrade> userTradesBlock;
        try {
            userTradesBlock = tradeService.getTradeHistory(params).getUserTrades();
        } catch (Exception e) {
            throw new IllegalStateException("User trade history download failed.", e);
        }

        if (downloadState.lastTxId == null) {
            return userTradesBlock;
        }

        final List<UserTrade> userTradesToAdd;
        final int duplicateTxIndex = findDuplicateTransaction(downloadState.lastTxId, userTradesBlock);
        if (duplicateTxIndex > -1) {
            if (duplicateTxIndex < userTradesBlock.size() - 1) {
                userTradesToAdd = userTradesBlock.subList(duplicateTxIndex + 1, userTradesBlock.size());
            } else {
                userTradesToAdd = List.of();
            }
        } else {
            userTradesToAdd = userTradesBlock;
        }

        if (!userTradesToAdd.isEmpty()) {
            downloadState.setLastTxId(userTradesToAdd.get(userTradesToAdd.size() - 1).getId());
        }
        return userTradesToAdd;
    }

    private List<BittrexDepositHistory> downloadDeposits(DownloadState downloadState) {
        var accountService = (BittrexAccountServiceRaw) exchange.getAccountService();

        List<BittrexDepositHistory> deposits = new ArrayList<>();
        try {
            deposits.addAll(accountService.getBittrexDepositsClosed(
                null, downloadState.lastDepositId,null, DEPOSIT_WITHDRAWAL_PAGE_SIZE
            ));
        } catch (IOException e) {
            throw new IllegalStateException("User deposit history download failed.", e);
        }

        if (!deposits.isEmpty()) {
            downloadState.setLastDepositId(deposits.get(deposits.size() -1).getId());
        }
        return deposits;
    }

    private List<BittrexWithdrawalHistory> downloadWithdrawals(DownloadState downloadState) {
        var accountService = (BittrexAccountServiceRaw) exchange.getAccountService();

        List<BittrexWithdrawalHistory> withdrawals = new ArrayList<>();
        try {
            withdrawals.addAll(accountService.getBittrexWithdrawalsClosed(
                null, downloadState.lastWithdrawalId,null, DEPOSIT_WITHDRAWAL_PAGE_SIZE
            ));
        } catch (IOException e) {
            throw new IllegalStateException("User withdrawal history download failed.", e);
        }

        if (!withdrawals.isEmpty()) {
            downloadState.setLastDepositId(withdrawals.get(withdrawals.size() -1).getId());
        }
        return withdrawals;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @FieldDefaults(level = PRIVATE)
    private static class DownloadState {
        private static final String SEPARATOR = "=";

        String lastTxId;
        String lastDepositId;
        String lastWithdrawalId;

        public static DownloadState deserialize(String state) {
            if (isEmpty(state)) {
                return new DownloadState();
            }
            var strA = state.split(SEPARATOR);
            return new DownloadState(
                strA[0],
                strA.length > 1 ? emptyToNull(strA[1]) : null,
                strA.length > 2 ? emptyToNull(strA[2]) : null
            );
        }

        public String serialize() {
            return (lastTxId == null ? "" : lastTxId) + SEPARATOR +
                (lastDepositId == null ? "" : lastDepositId) + SEPARATOR +
                (lastWithdrawalId == null ? "" : lastWithdrawalId);
        }
    }
}