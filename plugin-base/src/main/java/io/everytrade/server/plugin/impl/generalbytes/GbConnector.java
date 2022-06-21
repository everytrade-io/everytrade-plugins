package io.everytrade.server.plugin.impl.generalbytes;

import io.everytrade.server.model.SupportedExchange;
import io.everytrade.server.parser.exchange.GbApiTransactionBean;
import io.everytrade.server.plugin.api.IPlugin;
import io.everytrade.server.plugin.api.connector.ConnectorDescriptor;
import io.everytrade.server.plugin.api.connector.ConnectorParameterDescriptor;
import io.everytrade.server.plugin.api.connector.ConnectorParameterType;
import io.everytrade.server.plugin.api.connector.DownloadResult;
import io.everytrade.server.plugin.api.connector.IConnector;
import io.everytrade.server.plugin.api.parser.ParseResult;
import io.everytrade.server.plugin.api.parser.ParsingProblem;
import io.everytrade.server.plugin.api.parser.ParsingProblemType;
import io.everytrade.server.plugin.api.parser.TransactionCluster;
import io.everytrade.server.plugin.impl.everytrade.EveryTradeApiDigest;
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


public class GbConnector implements IConnector {
    private final IGbApi api;
    private final String apiKey;
    private final ParamsDigest signer;
    private final Logger log = LoggerFactory.getLogger(this.getClass());

    private static final int MAX_FETCH_SIZE = 5_000;
    private static final String ID = GbPlugin.ID + IPlugin.PLUGIN_PATH_SEPARATOR + "GBConnector";

    private static final ConnectorParameterDescriptor PARAMETER_API_SECRET =
        new ConnectorParameterDescriptor(
            "apiSecret",
            ConnectorParameterType.SECRET,
            "API Secret",
            "",
            false
        );

    private static final ConnectorParameterDescriptor PARAMETER_API_KEY =
        new ConnectorParameterDescriptor(
            "apiKey",
            ConnectorParameterType.STRING,
            "API Key",
            "",
            false
        );

    private static final ConnectorParameterDescriptor PARAMETER_URL =
        new ConnectorParameterDescriptor(
            "url",
            ConnectorParameterType.STRING,
            "CAS URL",
            "URL to GENERAL BYTES Crypto Application Server For example https://gb.example.com:7777/",
            false
        );

    public static final ConnectorDescriptor DESCRIPTOR = new ConnectorDescriptor(
        ID,
        "GENERAL BYTES CAS Connector",
        "",
        SupportedExchange.GENERAL_BYTES.getInternalId(),
        List.of(PARAMETER_URL, PARAMETER_API_KEY, PARAMETER_API_SECRET)
    );

    public GbConnector(Map<String, String> parameters) {
        this(
            parameters.get(PARAMETER_URL.getId()),
            parameters.get(PARAMETER_API_KEY.getId()),
            parameters.get(PARAMETER_API_SECRET.getId())
        );
    }

    public GbConnector(String url, String apiKey, String apiSecret) {
        if (!url.contains("/everytrade")) {
            if (!url.endsWith("/")) {
                url+="/";
            }
            url+="everytrade";
        }
        api = RestProxyFactory.createProxy(IGbApi.class, url, getConfig());
        Objects.requireNonNull(this.apiKey = apiKey);
        signer = new EveryTradeApiDigest(Objects.requireNonNull(apiSecret));
    }

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public DownloadResult getTransactions(String lastTransactionId) {
        final List<TransactionCluster> importedClusters = new ArrayList<>();
        final List<ParsingProblem> parsingProblems = new ArrayList<>();

        final GbApiDto data = api.getTransactions(apiKey, signer, lastTransactionId, MAX_FETCH_SIZE);
        final List<GbApiTransactionBean> transactions = Objects.requireNonNullElse(data.getTransactions(), new ArrayList<>());

        long transactionCount = 0;
        String lastDownloadedTxUid = lastTransactionId;
        for (GbApiTransactionBean transaction : transactions) {
            try {
                if (transaction.isImportable()) {
                    final TransactionCluster cluster = transaction.toTransactionCluster();
                    importedClusters.add(cluster);
                    transactionCount += 1 + cluster.getRelated().size();
                    lastDownloadedTxUid = transaction.getUid();
                } else if (transaction.isIgnored()) {
                    parsingProblems.add(
                        new ParsingProblem(transaction.toString(), transaction.getIgnoreReason(), ParsingProblemType.PARSED_ROW_IGNORED)
                    );
                }
            } catch (Exception e) {
                log.error("Error converting to ImportedTransactionBean: {}", e.getMessage());
                log.debug("Exception by converting to ImportedTransactionBean.", e);
                parsingProblems.add(
                    new ParsingProblem(transaction.toString(), e.getMessage(), ParsingProblemType.ROW_PARSING_FAILED)
                );
            }
        }
        log.info(
            "{} transaction cluster(s) with {} transactions parsed successfully.",
            importedClusters.size(),
            transactionCount
        );
        if (!parsingProblems.isEmpty()) {
            log.warn("{} row(s) not parsed.", parsingProblems.size());
        }

        return new DownloadResult(
            new ParseResult(importedClusters,parsingProblems),
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
