package io.everytrade.server.plugin.impl.everytrade;

import io.everytrade.server.UiKey;
import io.everytrade.server.model.SupportedExchange;
import io.everytrade.server.plugin.api.IPlugin;
import io.everytrade.server.plugin.api.connector.ConnectorDescriptor;
import io.everytrade.server.plugin.api.connector.ConnectorParameterDescriptor;
import io.everytrade.server.plugin.api.connector.ConnectorParameterType;
import io.everytrade.server.plugin.api.connector.DownloadResult;
import io.everytrade.server.plugin.api.connector.IConnector;
import lombok.AllArgsConstructor;
import lombok.Value;

import java.util.List;
import java.util.Map;

@Value
@AllArgsConstructor
public class BlockchainSolConnector implements IConnector {
    /**
     * One SOL sync at a time per JVM. The JSON-RPC calls are paced JVM-wide by {@code HeliusRpcPacer}, but the Wallet
     * API history pacing (500 ms sleep per page in {@link BlockchainSolDownloader}, FREE limit 2/s) is per caller and
     * is JVM-wide ONLY because of this lock; removing the lock needs a JVM-wide Wallet API limiter first.
     */
    private static final Object LOCK = new Object();
    private static final String ID = WhaleBooksPlugin.ID + IPlugin.PLUGIN_PATH_SEPARATOR + "blockchainSolApiConnector";

    private static final ConnectorParameterDescriptor PARAMETER_ADDRESS =
        new ConnectorParameterDescriptor(
            "address",
            ConnectorParameterType.STRING,
            UiKey.CONNECTION_WALLET_ADDR,
            "",
            false
        );

    private static final ConnectorParameterDescriptor PARAMETER_FIAT_CURRENCY =
        new ConnectorParameterDescriptor(
            "fiatCurrency",
            ConnectorParameterType.FIAT_CURRENCY,
            UiKey.CONNECTION_FIAT_CURRENCY,
            "",
            false
        );

    private static final ConnectorParameterDescriptor INTERPRETATION_LABEL =
        new ConnectorParameterDescriptor(
            "interpretationLabel",
            ConnectorParameterType.LABEL,
            UiKey.INTERPRETATION_LABEL,
            "",
            false
        );

    private static final ConnectorParameterDescriptor PARAMETER_IMPORT_DEPOSITS_AS_BUYS =
        new ConnectorParameterDescriptor(
            "importDepositsAsBuys",
            ConnectorParameterType.BOOLEAN,
            UiKey.CONNECTION_DEPOSITS_AS_BUY_OPT,
            "",
            false
        );

    private static final ConnectorParameterDescriptor PARAMETER_IMPORT_WITHDRAWALS_AS_SELLS =
        new ConnectorParameterDescriptor(
            "importWithdrawalsAsSells",
            ConnectorParameterType.BOOLEAN,
            UiKey.CONNECTION_WITHDRAWAL_AS_SELL_OPT,
            "",
            false
        );

    public static final ConnectorDescriptor DESCRIPTOR = new ConnectorDescriptor(
        ID,
        "Blockchain SOL Connector",
        "",
        SupportedExchange.BLOCKCHAINSOL.getInternalId(),
        List.of(
            PARAMETER_ADDRESS,
            PARAMETER_FIAT_CURRENCY,
            INTERPRETATION_LABEL,
            PARAMETER_IMPORT_DEPOSITS_AS_BUYS,
            PARAMETER_IMPORT_WITHDRAWALS_AS_SELLS,
            ChainFeeOptions.FEES_LABEL,
            ChainFeeOptions.PARAMETER_FEES_PAID_BY_WALLET,
            ChainFeeOptions.PARAMETER_SENDER_FEE_ON_DEPOSITS
        )
    );

    private static final String HELIUS_API_KEY_PARAM = "apiKey";

    String address;
    String apiKey;
    String fiatCurrency;
    String importDepositsAsBuys;
    String importWithdrawalsAsSells;
    /** {@code importFeesFromWithdrawals}, see {@link ChainFeeOptions}. */
    boolean feesPaidByWallet;
    /** {@code importFeesFromDeposits}, see {@link ChainFeeOptions}. */
    boolean senderFeeOnDeposits;

    /**
     * {@code importFeesFromDeposits} / {@code importFeesFromWithdrawals} are read with the meaning of 2026-10-20 (see
     * {@link ChainFeeOptions}): a saved value is kept, a missing one is the default.
     */
    public BlockchainSolConnector(Map<String, String> parameters) {
        this(
            parameters.get(PARAMETER_ADDRESS.getId()),
            parameters.get(HELIUS_API_KEY_PARAM),
            parameters.get(PARAMETER_FIAT_CURRENCY.getId()),
            parameters.get(PARAMETER_IMPORT_DEPOSITS_AS_BUYS.getId()),
            parameters.get(PARAMETER_IMPORT_WITHDRAWALS_AS_SELLS.getId()),
            ChainFeeOptions.feesPaidByWallet(parameters),
            ChainFeeOptions.senderFeeOnDeposits(parameters)
        );
    }

    boolean feesPaidByWallet() {
        return feesPaidByWallet;
    }

    boolean senderFeeOnDeposits() {
        return senderFeeOnDeposits;
    }

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public DownloadResult getTransactions(String downloadState) {
        synchronized (LOCK) {
            final var downloader = new BlockchainSolDownloader(
                address,
                apiKey,
                fiatCurrency,
                importDepositsAsBuys,
                importWithdrawalsAsSells,
                senderFeeOnDeposits,
                feesPaidByWallet
            );
            return downloader.download(downloadState);
        }
    }
}
