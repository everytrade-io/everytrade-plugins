package io.everytrade.server.plugin.impl.everytrade;

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

public class BlockchainBtcConnector implements IConnector {
    private static final String ID = EveryTradePlugin.ID + IPlugin.PLUGIN_PATH_SEPARATOR + "blockchainBtcApiConnector";

    private static final ConnectorParameterDescriptor PARAMETER_ADDRESS =
        new ConnectorParameterDescriptor(
            "source",
            ConnectorParameterType.STRING,
            "Wallet address / xpub key",
            ""
        );

    private static final ConnectorParameterDescriptor PARAMETER_FIAT_CURRENCY =
        new ConnectorParameterDescriptor(
            "fiatCurrency",
            ConnectorParameterType.FIAT_CURRENCY,
            "Fiat currency",
            ""
        );

    private static final ConnectorParameterDescriptor PARAMETER_IMPORT_DEPOSITS_AS_BUYS =
        new ConnectorParameterDescriptor(
            "importDepositsAsBuys",
            ConnectorParameterType.BOOLEAN,
            "Import deposits as BUY transactions",
            ""
        );

    private static final ConnectorParameterDescriptor PARAMETER_IMPORT_WITHDRAWALS_AS_SELLS =
        new ConnectorParameterDescriptor(
            "importWithdrawalsAsSells",
            ConnectorParameterType.BOOLEAN,
            "Import withdrawals as SELL transactions",
            ""
        );

    private static final ConnectorParameterDescriptor PARAMETER_IMPORT_FEES_FROM_DEPOSITS =
        new ConnectorParameterDescriptor(
            "importFeesFromDeposits",
            ConnectorParameterType.BOOLEAN,
            "Import deposit mining fee as BUY FEE",
            ""
        );

    private static final ConnectorParameterDescriptor PARAMETER_IMPORT_FEES_FROM_WITHDRAWALS =
        new ConnectorParameterDescriptor(
            "importFeesFromWithdrawals",
            ConnectorParameterType.BOOLEAN,
            "Import withdrawal mining fee as SELL FEE",
            ""
        );

    public static final ConnectorDescriptor DESCRIPTOR = new ConnectorDescriptor(
        ID,
        "Blockchain BTC Connector",
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

    private static final String CRYPTO_CURRENCY = "BTC";
    private final String source;
    private final String fiatCurrency;
    private final String importDepositsAsBuys;
    private final String importWithdrawalsAsSells;
    private final String importFeesFromDeposits;
    private final String importFeesFromWithdrawals;

    public BlockchainBtcConnector(Map<String, String> parameters) {
        this(
            parameters.get(PARAMETER_ADDRESS.getId()),
            parameters.get(PARAMETER_FIAT_CURRENCY.getId()),
            parameters.get(PARAMETER_IMPORT_DEPOSITS_AS_BUYS.getId()),
            parameters.get(PARAMETER_IMPORT_WITHDRAWALS_AS_SELLS.getId()),
            parameters.get(PARAMETER_IMPORT_FEES_FROM_DEPOSITS.getId()),
            parameters.get(PARAMETER_IMPORT_FEES_FROM_WITHDRAWALS.getId())
        );
    }

    public BlockchainBtcConnector(
        String source,
        String fiatCurrency,
        String importDepositsAsBuys,
        String isImportSell,
        String importFeesFromDeposits,
        String importFeesFromWithdrawals
    ) {
        Objects.requireNonNull(this.source = source);
        Objects.requireNonNull(this.fiatCurrency = fiatCurrency);
        Objects.requireNonNull(this.importDepositsAsBuys = importDepositsAsBuys);
        Objects.requireNonNull(this.importWithdrawalsAsSells = isImportSell);
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
