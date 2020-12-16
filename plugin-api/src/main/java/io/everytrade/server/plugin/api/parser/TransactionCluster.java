package io.everytrade.server.plugin.api.parser;

import java.util.List;

public class TransactionCluster {
    private final ImportedTransactionBean main;
    private final List<ImportedTransactionBean> related;
    private final int ignoredFeeTransactions;


    public TransactionCluster(ImportedTransactionBean main, List<ImportedTransactionBean> related) {
        this(main, related, 0);
    }

    public TransactionCluster(
        ImportedTransactionBean main,
        List<ImportedTransactionBean> related,
        int ignoredFeeTransactions
    ) {
        this.main = main;
        this.related = related;
        this.ignoredFeeTransactions = ignoredFeeTransactions;
    }

    public ImportedTransactionBean getMain() {
        return main;
    }

    public List<ImportedTransactionBean> getRelated() {
        return related;
    }

    public int getIgnoredFeeTransactions() {
        return ignoredFeeTransactions;
    }
}
