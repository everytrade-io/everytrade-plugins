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
            "address",
            ConnectorParameterType.STRING,
            "Wallet address / xpub",
            ""
        );

    private static final ConnectorParameterDescriptor PARAMETER_FIAT_CURRENCY =
        new ConnectorParameterDescriptor(
            "fiatCurrency",
            ConnectorParameterType.FIAT_LIST,
            "Fiat currency",
            ""
        );

    private static final ConnectorParameterDescriptor PARAMETER_IS_WITH_FEE =
        new ConnectorParameterDescriptor(
            "isWithFee",
            ConnectorParameterType.BOOLEAN,
            "With fee transactions",
            ""
        );

    public static final ConnectorDescriptor DESCRIPTOR = new ConnectorDescriptor(
        ID,
        "Blockchain BTC Connector",
        "",
        SupportedExchange.BLOCKCHAIN.getInternalId(),
        List.of(PARAMETER_ADDRESS, PARAMETER_FIAT_CURRENCY, PARAMETER_IS_WITH_FEE)
    );

    private static final String CRYPTO_CURRENCY = "BTC";
    private final String address;
    private final String fiatCurrency;
    private final String isWithFee;

    public BlockchainBtcConnector(Map<String, String> parameters) {
        this(
            parameters.get(PARAMETER_ADDRESS.getId()),
            parameters.get(PARAMETER_FIAT_CURRENCY.getId()),
            parameters.get(PARAMETER_IS_WITH_FEE.getId())
        );
    }

    public BlockchainBtcConnector(String address, String fiatCurrency, String isWithFee) {
        Objects.requireNonNull(this.address = address);
        Objects.requireNonNull(this.fiatCurrency = fiatCurrency);
        Objects.requireNonNull(this.isWithFee = isWithFee);
    }

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public DownloadResult getTransactions(String lastTransactionUid) {

        final BlockchainDownloader blockchainDownloader
            = new BlockchainDownloader(lastTransactionUid, CRYPTO_CURRENCY, fiatCurrency, isWithFee);

        return blockchainDownloader.download(address);
    }

    @Override
    public void close() {
        //AutoCloseable
    }
}
