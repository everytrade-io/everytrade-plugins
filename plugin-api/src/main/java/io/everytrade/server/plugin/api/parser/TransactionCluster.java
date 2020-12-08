package io.everytrade.server.plugin.api.parser;

import java.util.List;

public class TransactionCluster {
    private final ImportedTransactionBean main;
    private final List<ImportedTransactionBean> related;

    public TransactionCluster(ImportedTransactionBean main, List<ImportedTransactionBean> related) {
        this.main = main;
        this.related = related;
    }

    public ImportedTransactionBean getMain() {
        return main;
    }

    public List<ImportedTransactionBean> getRelated() {
        return related;
    }
}
