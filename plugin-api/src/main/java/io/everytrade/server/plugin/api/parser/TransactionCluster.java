package io.everytrade.server.plugin.api.parser;

import java.util.List;

public class TransactionCluster {
    private final ImportedTransactionBean main;
    private final List<ImportedTransactionBean> related;
    private final int ignoredFeeTransactionCount;

    public TransactionCluster(ImportedTransactionBean main, List<ImportedTransactionBean> related) {
        this(main, related, 0);
    }

    public TransactionCluster(
        ImportedTransactionBean main,
        List<ImportedTransactionBean> related,
        int ignoredFeeTransactionCount
    ) {
        this.main = main;
        this.related = related;
        this.ignoredFeeTransactionCount = ignoredFeeTransactionCount;
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
}
