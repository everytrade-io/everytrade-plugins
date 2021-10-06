package io.everytrade.server.plugin.api.parser;

import java.util.List;

public class TransactionCluster {
    private final ImportedTransactionBean main;
    private final List<ImportedTransactionBean> related;
    private int ignoredFeeTransactionCount = 0;
    private String ignoredFeeReason;

    public TransactionCluster(ImportedTransactionBean main, List<ImportedTransactionBean> related) {
        this.main = main;
        this.related = related;
    }

    public void setIgnoredFee(int count, String reason) {
        this.ignoredFeeTransactionCount = count;
        this.ignoredFeeReason = reason;
    }

    public ImportedTransactionBean getMain() {
        return main;
    }

    public List<ImportedTransactionBean> getRelated() {
        return related;
    }

    public int getIgnoredFeeTransactionCount() {
        return ignoredFeeTransactionCount;
    }

    public String getIgnoredFeeReason() {
        return ignoredFeeReason;
    }
}
