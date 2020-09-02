package io.everytrade.server.plugin.impl.everytrade;

import io.everytrade.server.plugin.api.connector.ConnectorParameterType;
import io.everytrade.server.model.SupportedExchange;
import io.everytrade.server.plugin.api.parser.ConversionStatistic;
import io.everytrade.server.plugin.api.parser.ImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.ParseResult;
import io.everytrade.server.plugin.api.parser.RowError;
import io.everytrade.server.plugin.api.parser.RowErrorType;
import io.everytrade.server.parser.exchange.EveryTradeApiTransactionBean;
import io.everytrade.server.plugin.api.connector.DownloadResult;
import io.everytrade.server.plugin.api.IPlugin;
import io.everytrade.server.plugin.api.connector.ConnectorDescriptor;
import io.everytrade.server.plugin.api.connector.ConnectorParameterDescriptor;
import io.everytrade.server.plugin.api.connector.IConnector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import si.mazi.rescu.ClientConfig;
import si.mazi.rescu.ParamsDigest;
import si.mazi.rescu.RestProxyFactory;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class EveryTradeConnector implements IConnector {
    private final Logger log = LoggerFactory.getLogger(this.getClass());

    private static final int MAX_FETCH_SIZE = 1_000;
    private static final String ID = EveryTradePlugin.ID + IPlugin.PLUGIN_PATH_SEPARATOR + "etApiConnector";

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

    private static final ConnectorParameterDescriptor PARAMETER_URL =
        new ConnectorParameterDescriptor(
            "url",
            ConnectorParameterType.STRING,
            "URL",
            ""
        );

    public static final ConnectorDescriptor DESCRIPTOR = new ConnectorDescriptor(
        ID,
        "EveryTrade Universal Connector",
        SupportedExchange.EVERYTRADE.getInternalId(),
        List.of(PARAMETER_URL, PARAMETER_API_KEY, PARAMETER_API_SECRET)
    );

    private final IEveryTradeApi api;
    private final String apiKey;
    private final ParamsDigest signer;

    public EveryTradeConnector(Map<String, String> parameters) {
        this(
            parameters.get(PARAMETER_URL.getId()),
            parameters.get(PARAMETER_API_KEY.getId()),
            parameters.get(PARAMETER_API_SECRET.getId())
        );
    }

    public EveryTradeConnector(String url, String apiKey, String apiSecret) {
        api = RestProxyFactory.createProxy(IEveryTradeApi.class, url, getConfig());
        Objects.requireNonNull(this.apiKey = apiKey);
        signer = new EveryTradeApiDigest(Objects.requireNonNull(apiSecret));
    }

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public DownloadResult getTransactions(String lastTransactionId) {
        final EveryTradeApiDto data =
            api.getTransactions(apiKey, signer, lastTransactionId, MAX_FETCH_SIZE);

        final List<EveryTradeApiTransactionBean> transactions = data.getTransactions();

        final List<ImportedTransactionBean> importedTransactions = new ArrayList<>();
        final List<RowError> errorRows = new ArrayList<>();
        for (EveryTradeApiTransactionBean transaction : transactions) {
            try {
                importedTransactions.add(transaction.toImportedTransactionBean());
            } catch (Exception e) {
                log.error("Error converting to ImportedTransactionBean.", e);
                errorRows.add(new RowError(transaction.toString(), e.getMessage(), RowErrorType.FAILED));
            }
        }
        final String lastDownloadedTxUid;
        if (importedTransactions.isEmpty()) {
            lastDownloadedTxUid = lastTransactionId;
        } else {
            final ImportedTransactionBean lastTransaction = importedTransactions.get(importedTransactions.size() - 1);
            lastDownloadedTxUid = lastTransaction.getUid();
        }

        return new DownloadResult(
            new ParseResult(importedTransactions, new ConversionStatistic(errorRows, 0)),
            lastDownloadedTxUid
        );
    }

    //TODO: insecure, remove!!!
    private ClientConfig getConfig() {
        try {
            final ClientConfig config = new ClientConfig();
            SSLContext sslcontext = SSLContext.getInstance("TLS");
            sslcontext.init(null, new TrustManager[]{
                new X509TrustManager() {

                    public X509Certificate[] getAcceptedIssuers() {
                        return null;
                    }

                    @SuppressWarnings("TrustAllX509TrustManager")
                    public void checkClientTrusted(X509Certificate[] certs, String authType) {
                    }

                    @SuppressWarnings("TrustAllX509TrustManager")
                    public void checkServerTrusted(X509Certificate[] certs, String authType) {
                    }
                }
            }, new SecureRandom());
            config.setSslSocketFactory(sslcontext.getSocketFactory());
            config.setHostnameVerifier((x, y) -> true);
            config.setIgnoreHttpErrorCodes(true);
            return config;
        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            throw new IllegalStateException(e);
        }
    }
}
