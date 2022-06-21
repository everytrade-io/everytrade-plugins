package io.everytrade.server.plugin.impl.everytrade;

import io.everytrade.server.model.SupportedExchange;
import io.everytrade.server.parser.exchange.EveryTradeApiTransactionBean;
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
import lombok.NonNull;
import lombok.experimental.FieldDefaults;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import si.mazi.rescu.ParamsDigest;
import si.mazi.rescu.RestProxyFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static lombok.AccessLevel.PRIVATE;

@FieldDefaults(makeFinal = true, level = PRIVATE)
public class EveryTradeConnector implements IConnector {

    private static final Logger LOG = LoggerFactory.getLogger(EveryTradeConnector.class);
    private static final int MAX_FETCH_SIZE = 1_000;
    private static final String ID = EveryTradePlugin.ID + IPlugin.PLUGIN_PATH_SEPARATOR + "etApiConnector";

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
            "URL",
            "",
            false
        );

    public static final ConnectorDescriptor DESCRIPTOR = new ConnectorDescriptor(
        ID,
        "EveryTrade Universal Connector",
        "",
        SupportedExchange.EVERYTRADE.getInternalId(),
        List.of(PARAMETER_URL, PARAMETER_API_KEY, PARAMETER_API_SECRET)
    );


    public EveryTradeConnector(Map<String, String> parameters) {
        this(
            parameters.get(PARAMETER_URL.getId()),
            parameters.get(PARAMETER_API_KEY.getId()),
            parameters.get(PARAMETER_API_SECRET.getId())
        );
    }

    IEveryTradeApi api;
    String apiKey;
    ParamsDigest signer;

    public EveryTradeConnector(@NonNull String url, @NonNull String apiKey, @NonNull String apiSecret) {
        this.api = RestProxyFactory.createProxy(IEveryTradeApi.class, url);
        this.apiKey = apiKey;
        this.signer = new EveryTradeApiDigest(apiSecret);
    }

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public DownloadResult getTransactions(String lastTransactionId) {
        var data = api.getTransactions(apiKey, signer, lastTransactionId, MAX_FETCH_SIZE);
        var txs = Objects.requireNonNullElse(data.getTransactions(), new ArrayList<EveryTradeApiTransactionBean>());

        final List<TransactionCluster> importedClusters = new ArrayList<>();
        final List<ParsingProblem> parsingProblems = new ArrayList<>();
        long transactionCount = 0;
        String lastDownloadedTxUid = lastTransactionId;
        for (EveryTradeApiTransactionBean tx : txs) {
            try {
                var cluster = tx.toTransactionCluster();
                importedClusters.add(cluster);
                transactionCount += 1 + cluster.getRelated().size();
                lastDownloadedTxUid = tx.getUid();
            } catch (Exception e) {
                LOG.error("Error converting to ImportedTransactionBean: {}: {}", e.getClass().getName(), e.getMessage());
                LOG.debug("Exception by converting to ImportedTransactionBean.", e);
                parsingProblems.add(new ParsingProblem(tx.toString(), e.getMessage(), ParsingProblemType.ROW_PARSING_FAILED));
            }
        }
        LOG.info(
            "{} transaction cluster(s) with {} transactions parsed successfully.",
            importedClusters.size(),
            transactionCount
        );
        if (!parsingProblems.isEmpty()) {
            LOG.warn("{} row(s) not parsed.", parsingProblems.size());
        }

        return new DownloadResult(new ParseResult(importedClusters, parsingProblems), lastDownloadedTxUid);
    }
}
