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
            "ltubKey",
            ConnectorParameterType.STRING,
            "Ltub key",
            ""
        );

    private static final ConnectorParameterDescriptor PARAMETER_FIAT_CURRENCY =
        new ConnectorParameterDescriptor(
            "fiatCurrency",
            ConnectorParameterType.STRING,
            "Fiat currency",
            ""
        );

    private static final ConnectorParameterDescriptor PARAMETER_IS_WITH_FEE =
        new ConnectorParameterDescriptor(
            "isWithFee",
            ConnectorParameterType.STRING,
            "With fee transactions",
            ""
        );

    public static final ConnectorDescriptor DESCRIPTOR = new ConnectorDescriptor(
        ID,
        "Blockchain LTC Connector",
        "",
        SupportedExchange.BLOCKCHAIN.getInternalId(),
        List.of(PARAMETER_ADDRESS, PARAMETER_FIAT_CURRENCY, PARAMETER_IS_WITH_FEE)
    );

    private static final String CRYPTO_CURRENCY = "LTC";
    private final String ltubKey;
    private final String fiatCurrency;
    private final String isWithFee;

    public BlockchainLtcConnector(Map<String, String> parameters) {
        this(
            parameters.get(PARAMETER_ADDRESS.getId()),
            parameters.get(PARAMETER_FIAT_CURRENCY.getId()),
            parameters.get(PARAMETER_IS_WITH_FEE.getId())
        );
    }

    public BlockchainLtcConnector(String ltubKey, String fiatCurrency, String isWithFee) {
        Objects.requireNonNull(this.ltubKey = ltubKey);
        if (ltubKey.startsWith(BlockchainDownloader.LTUB_PREFIX)) {
            throw new IllegalArgumentException(String.format(
                "Incorrect value of Ltub key '%s' It should starts with '%s'.",
                ConnectorUtils.truncate(ltubKey, BlockchainDownloader.TRUNCATE_LIMIT),
                BlockchainDownloader.LTUB_PREFIX
            ));
        }
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

        return blockchainDownloader.download(ltubKey);
    }

    @Override
    public void close() {
        //AutoCloseable
    }
}
