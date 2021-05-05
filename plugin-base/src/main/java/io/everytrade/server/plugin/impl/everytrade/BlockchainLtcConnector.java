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

public class BlockchainLtcConnector implements IConnector {
    private static final String ID = EveryTradePlugin.ID + IPlugin.PLUGIN_PATH_SEPARATOR + "blockchainLtcApiConnector";

    private static final ConnectorParameterDescriptor PARAMETER_ADDRESS =
        new ConnectorParameterDescriptor(
            "source",
            ConnectorParameterType.STRING,
            "Wallet address / ltub key",
            ""
        );

    private static final ConnectorParameterDescriptor PARAMETER_FIAT_CURRENCY =
        new ConnectorParameterDescriptor(
            "fiatCurrency",
            ConnectorParameterType.FIAT_CURRENCY,
            "Fiat currency",
            ""
        );

    private static final ConnectorParameterDescriptor PARAMETER_IS_IMPORT_BUY =
        new ConnectorParameterDescriptor(
            "isImportBuy",
            ConnectorParameterType.BOOLEAN,
            "Import deposits as BUY transactions",
            ""
        );

    private static final ConnectorParameterDescriptor PARAMETER_IS_IMPORT_SELL =
        new ConnectorParameterDescriptor(
            "isImportSell",
            ConnectorParameterType.BOOLEAN,
            "Import withdrawals as SELL transactions",
            ""
        );

    private static final ConnectorParameterDescriptor PARAMETER_IS_BUY_WITH_FEE =
        new ConnectorParameterDescriptor(
            "isBuyWithFee",
            ConnectorParameterType.BOOLEAN,
            "Import deposit mining fee as BUY FEE",
            ""
        );

    private static final ConnectorParameterDescriptor PARAMETER_IS_SELL_WITH_FEE =
        new ConnectorParameterDescriptor(
            "isSellWithFee",
            ConnectorParameterType.BOOLEAN,
            "Import withdrawal mining fee as SELL FEE",
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
            PARAMETER_IS_IMPORT_BUY,
            PARAMETER_IS_IMPORT_SELL,
            PARAMETER_IS_BUY_WITH_FEE,
            PARAMETER_IS_SELL_WITH_FEE
        )
    );

    private static final String CRYPTO_CURRENCY = "LTC";
    private final String source;
    private final String fiatCurrency;
    private final String isImportBuy;
    private final String isImportSell;
    private final String isBuyWithFee;
    private final String isSellWithFee;

    public BlockchainLtcConnector(Map<String, String> parameters) {
        this(
            parameters.get(PARAMETER_ADDRESS.getId()),
            parameters.get(PARAMETER_FIAT_CURRENCY.getId()),
            parameters.get(PARAMETER_IS_IMPORT_BUY.getId()),
            parameters.get(PARAMETER_IS_IMPORT_SELL.getId()),
            parameters.get(PARAMETER_IS_BUY_WITH_FEE.getId()),
            parameters.get(PARAMETER_IS_SELL_WITH_FEE.getId())
        );
    }

    public BlockchainLtcConnector(
        String source,
        String fiatCurrency,
        String isImportBuy,
        String isImportSell,
        String isBuyWithFee,
        String isSellWithFee
    ) {
        Objects.requireNonNull(this.source = source);
        Objects.requireNonNull(this.fiatCurrency = fiatCurrency);
        Objects.requireNonNull(this.isImportBuy = isImportBuy);
        Objects.requireNonNull(this.isImportSell = isImportSell);
        Objects.requireNonNull(this.isBuyWithFee = isBuyWithFee);
        Objects.requireNonNull(this.isSellWithFee = isSellWithFee);
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
            isImportBuy,
            isImportSell,
            isBuyWithFee,
            isSellWithFee
        );

        return blockchainDownloader.download(source);
    }

    @Override
    public void close() {
        //AutoCloseable
    }
}
