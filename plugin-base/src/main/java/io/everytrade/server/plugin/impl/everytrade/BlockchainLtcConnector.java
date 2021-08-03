package io.everytrade.server.plugin.impl.everytrade;

import io.everytrade.server.UiKey;
import io.everytrade.server.model.SupportedExchange;
import io.everytrade.server.plugin.api.IPlugin;
import io.everytrade.server.plugin.api.connector.ConnectorDescriptor;
import io.everytrade.server.plugin.api.connector.ConnectorParameterDescriptor;
import io.everytrade.server.plugin.api.connector.ConnectorParameterType;
import io.everytrade.server.plugin.api.connector.DownloadResult;
import io.everytrade.server.plugin.api.connector.IConnector;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public class BlockchainLtcConnector implements IConnector {
    private static final String ID = EveryTradePlugin.ID + IPlugin.PLUGIN_PATH_SEPARATOR + "blockchainLtcApiConnector";

    private static final ConnectorParameterDescriptor PARAMETER_ADDRESS =
        new ConnectorParameterDescriptor(
            "source",
            ConnectorParameterType.STRING,
            UiKey.CONNECTION_WALLET_ADDR_LTUB,
            ""
        );

    private static final ConnectorParameterDescriptor PARAMETER_FIAT_CURRENCY =
        new ConnectorParameterDescriptor(
            "fiatCurrency",
            ConnectorParameterType.FIAT_CURRENCY,
            UiKey.CONNECTION_FIAT_CURRENCY,
            ""
        );

    private static final ConnectorParameterDescriptor PARAMETER_IMPORT_DEPOSITS_AS_BUYS =
        new ConnectorParameterDescriptor(
            "importDepositsAsBuys",
            ConnectorParameterType.BOOLEAN,
            UiKey.CONNECTION_DEPOSITS_AS_BUY_OPT,
            ""
        );

    private static final ConnectorParameterDescriptor PARAMETER_IMPORT_WITHDRAWALS_AS_SELLS =
        new ConnectorParameterDescriptor(
            "importWithdrawalsAsSells",
            ConnectorParameterType.BOOLEAN,
            UiKey.CONNECTION_WITHDRAWAL_AS_SELL_OPT,
            ""
        );

    private static final ConnectorParameterDescriptor PARAMETER_IMPORT_FEES_FROM_DEPOSITS =
        new ConnectorParameterDescriptor(
            "importFeesFromDeposits",
            ConnectorParameterType.BOOLEAN,
            UiKey.CONNECTION_DEPOSIT_MINING_FEE_AS_BUY_OPT,
            ""
        );

    private static final ConnectorParameterDescriptor PARAMETER_IMPORT_FEES_FROM_WITHDRAWALS =
        new ConnectorParameterDescriptor(
            "importFeesFromWithdrawals",
            ConnectorParameterType.BOOLEAN,
            UiKey.CONNECTION_WITHDRAWAL_MINING_FEE_AS_SELL_OPT,
            ""
        );

    public static final ConnectorDescriptor DESCRIPTOR = new ConnectorDescriptor(
        ID,
        "Blockchain LTC Connector",
        "",
        SupportedExchange.BLOCKCHAIN.getInternalId(),
        List.of(
            PARAMETER_ADDRESS,
            PARAMETER_FIAT_CURRENCY,
            PARAMETER_IMPORT_DEPOSITS_AS_BUYS,
            PARAMETER_IMPORT_WITHDRAWALS_AS_SELLS,
            PARAMETER_IMPORT_FEES_FROM_DEPOSITS,
            PARAMETER_IMPORT_FEES_FROM_WITHDRAWALS
        )
    );

    private static final String CRYPTO_CURRENCY = "LTC";
    private final String source;
    private final String fiatCurrency;
    private final String importDepositsAsBuys;
    private final String importWithdrawalsAsSells;
    private final String importFeesFromDeposits;
    private final String importFeesFromWithdrawals;

    public BlockchainLtcConnector(Map<String, String> parameters) {
        this(
            parameters.get(PARAMETER_ADDRESS.getId()),
            parameters.get(PARAMETER_FIAT_CURRENCY.getId()),
            parameters.get(PARAMETER_IMPORT_DEPOSITS_AS_BUYS.getId()),
            parameters.get(PARAMETER_IMPORT_WITHDRAWALS_AS_SELLS.getId()),
            parameters.get(PARAMETER_IMPORT_FEES_FROM_DEPOSITS.getId()),
            parameters.get(PARAMETER_IMPORT_FEES_FROM_WITHDRAWALS.getId())
        );
    }

    public BlockchainLtcConnector(
        String source,
        String fiatCurrency,
        String importDepositsAsBuys,
        String importWithdrawalsAsSells,
        String importFeesFromDeposits,
        String importFeesFromWithdrawals
    ) {
        Objects.requireNonNull(this.source = source);
        Objects.requireNonNull(this.fiatCurrency = fiatCurrency);
        Objects.requireNonNull(this.importDepositsAsBuys = importDepositsAsBuys);
        Objects.requireNonNull(this.importWithdrawalsAsSells = importWithdrawalsAsSells);
        Objects.requireNonNull(this.importFeesFromDeposits = importFeesFromDeposits);
        Objects.requireNonNull(this.importFeesFromWithdrawals = importFeesFromWithdrawals);
    }

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public DownloadResult getTransactions(String lastTransactionUid) {

        final BlockchainDownloader blockchainDownloader
            = new BlockchainDownloader(
            lastTransactionUid,
            CRYPTO_CURRENCY,
            fiatCurrency,
            importDepositsAsBuys,
            importWithdrawalsAsSells,
            importFeesFromDeposits,
            importFeesFromWithdrawals
        );

        return blockchainDownloader.download(source);
    }

    @Override
    public void close() {
        //AutoCloseable
    }
}
