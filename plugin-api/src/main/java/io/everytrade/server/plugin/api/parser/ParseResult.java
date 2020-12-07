package io.everytrade.server.plugin.api.parser;

import java.util.List;

public class ParseResult {
    private final List<TransactionCluster> transactionClusters;

    //TODO: Inline the statistics? Why separate class?
    private final ConversionStatistic conversionStatistic;

    public ParseResult(
        List<TransactionCluster> transactionClusters,
        ConversionStatistic conversionStatistic
    ) {
        this.transactionClusters = transactionClusters;
        this.conversionStatistic = conversionStatistic;
    }

    public ConversionStatistic getConversionStatistic() {
        return conversionStatistic;
    }

    public List<TransactionCluster> getTransactionClusters() {
        return transactionClusters;
    }
}
