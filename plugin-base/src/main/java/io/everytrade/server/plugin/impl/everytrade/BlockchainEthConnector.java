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

    private static final ConnectorParameterDescriptor PARAMETER_IMPORT_FEES_FROM_DEPOSITS =
        new ConnectorParameterDescriptor(
            "importFeesFromDeposits",
            ConnectorParameterType.BOOLEAN,
            UiKey.CONNECTION_DEPOSIT_MINING_FEE_AS_BUY_OPT,
            "",
            false
        );

    private static final ConnectorParameterDescriptor PARAMETER_IMPORT_FEES_FROM_WITHDRAWALS =
        new ConnectorParameterDescriptor(
            "importFeesFromWithdrawals",
            ConnectorParameterType.BOOLEAN,
            UiKey.CONNECTION_WITHDRAWAL_MINING_FEE_AS_SELL_OPT,
            "",
            true
        );

    public static final ConnectorDescriptor DESCRIPTOR = new ConnectorDescriptor(
        ID,
        "Blockchain ETH Connector",
        "",
        SupportedExchange.BLOCKCHAINETH.getInternalId(),
        List.of(
            PARAMETER_ADDRESS,
            PARAMETER_FIAT_CURRENCY,
            PARAMETER_IMPORT_DEPOSITS_AS_BUYS,
            PARAMETER_IMPORT_WITHDRAWALS_AS_SELLS,
            PARAMETER_IMPORT_FEES_FROM_DEPOSITS,
            PARAMETER_IMPORT_FEES_FROM_WITHDRAWALS
        )
    );

    private static final String ETHERSCAN_API_KEY_PARAM = "apiKeyToken";

    String address;
    String apiKeyToken;
    String fiatCurrency;
    String importDepositsAsBuys;
    String importWithdrawalsAsSells;
    String importFeesFromDeposits;
    String importFeesFromWithdrawals;

    public BlockchainEthConnector(Map<String, String> parameters) {
        this(
            parameters.get(PARAMETER_ADDRESS.getId()),
            parameters.get(ETHERSCAN_API_KEY_PARAM),
            parameters.get(PARAMETER_FIAT_CURRENCY.getId()),
            parameters.get(PARAMETER_IMPORT_DEPOSITS_AS_BUYS.getId()),
            parameters.get(PARAMETER_IMPORT_WITHDRAWALS_AS_SELLS.getId()),
            parameters.get(PARAMETER_IMPORT_FEES_FROM_DEPOSITS.getId()),
            parameters.get(PARAMETER_IMPORT_FEES_FROM_WITHDRAWALS.getId())
        );
    }

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public DownloadResult getTransactions(String downloadState) {
        synchronized (LOCK) {
            final var blockchainEthDownloader = new BlockchainEthDownloader(
                address,
                apiKeyToken,
                fiatCurrency,
                importDepositsAsBuys,
                importWithdrawalsAsSells,
                importFeesFromDeposits,
                importFeesFromWithdrawals
            );
            return blockchainEthDownloader.download(downloadState);
        }
    }
}
