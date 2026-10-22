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
public class BlockchainEthConnector implements IConnector {
    private static final Object LOCK = new Object();
    private static final String ID = WhaleBooksPlugin.ID + IPlugin.PLUGIN_PATH_SEPARATOR + "blockchainEthApiConnector";

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

    private static final ConnectorParameterDescriptor SOURCES_LABEL =
        new ConnectorParameterDescriptor(
            "sourcesLabel",
            ConnectorParameterType.LABEL,
            UiKey.SOURCES_LABEL,
            "",
            false
        );

    private static final ConnectorParameterDescriptor PARAMETER_IMPORT_NORMAL_TXS =
        new ConnectorParameterDescriptor(
            "importNormalTxs",
            ConnectorParameterType.BOOLEAN,
            UiKey.NORMAL_TXS,
            "",
            true
        );

    private static final ConnectorParameterDescriptor PARAMETER_IMPORT_ERC20_TXS =
        new ConnectorParameterDescriptor(
            "importErc20Txs",
            ConnectorParameterType.BOOLEAN,
            UiKey.ERC20_TXS,
            "",
            false
        );

    public static final ConnectorDescriptor DESCRIPTOR = new ConnectorDescriptor(
        ID,
        "Blockchain ETH Connector",
        "",
        SupportedExchange.BLOCKCHAINETH.getInternalId(),
        List.of(
            PARAMETER_ADDRESS,
            PARAMETER_FIAT_CURRENCY,
            INTERPRETATION_LABEL,
            PARAMETER_IMPORT_DEPOSITS_AS_BUYS,
            PARAMETER_IMPORT_WITHDRAWALS_AS_SELLS,
            ChainFeeOptions.FEES_LABEL,
            ChainFeeOptions.PARAMETER_FEES_PAID_BY_WALLET,
            ChainFeeOptions.PARAMETER_SENDER_FEE_ON_DEPOSITS,
            SOURCES_LABEL,
            PARAMETER_IMPORT_NORMAL_TXS,
            PARAMETER_IMPORT_ERC20_TXS
        )
    );

    private static final String ETHERSCAN_API_KEY_PARAM = "apiKeyToken";

    String address;
    String apiKeyToken;
    String fiatCurrency;
    String interpretationLabel;
    String importDepositsAsBuys;
    String importWithdrawalsAsSells;
    /** {@code importFeesFromWithdrawals}, see {@link ChainFeeOptions}. */
    boolean feesPaidByWallet;
    /** {@code importFeesFromDeposits}, see {@link ChainFeeOptions}. */
    boolean senderFeeOnDeposits;
    String sourcesLabel;
    String importNormalTxs;
    String importErc20Txs;

    /**
     * {@code importFeesFromDeposits} / {@code importFeesFromWithdrawals} are read with the meaning of 2026-10-20 (see
     * {@link ChainFeeOptions}): a saved value is kept, a missing one is the default. A saved {@code feesLabel} (a
     * heading, no value) is ignored.
     */
    public BlockchainEthConnector(Map<String, String> parameters) {
        this(
            parameters.get(PARAMETER_ADDRESS.getId()),
            parameters.get(ETHERSCAN_API_KEY_PARAM),
            parameters.get(PARAMETER_FIAT_CURRENCY.getId()),
            parameters.get(INTERPRETATION_LABEL.getId()),
            parameters.get(PARAMETER_IMPORT_DEPOSITS_AS_BUYS.getId()),
            parameters.get(PARAMETER_IMPORT_WITHDRAWALS_AS_SELLS.getId()),
            ChainFeeOptions.feesPaidByWallet(parameters),
            ChainFeeOptions.senderFeeOnDeposits(parameters),
            parameters.get(SOURCES_LABEL.getId()),
            parameters.get(PARAMETER_IMPORT_NORMAL_TXS.getId()),
            parameters.get(PARAMETER_IMPORT_ERC20_TXS.getId())
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
            return downloader().download(downloadState);
        }
    }

    BlockchainEthDownloader downloader() {
        return new BlockchainEthDownloader(
            address,
            apiKeyToken,
            fiatCurrency,
            importDepositsAsBuys,
            importWithdrawalsAsSells,
            senderFeeOnDeposits,
            feesPaidByWallet,
            Boolean.parseBoolean(importNormalTxs),
            Boolean.parseBoolean(importErc20Txs)
        );
    }
}
