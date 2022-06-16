package io.everytrade.server.plugin.api.parser;

import lombok.Data;
import lombok.experimental.FieldDefaults;

import java.util.List;

import static lombok.AccessLevel.PRIVATE;

@Data
@FieldDefaults(level = PRIVATE)
public class TransactionCluster {

    ImportedTransactionBean main;
    List<ImportedTransactionBean> related;
    int ignoredFeeTransactionCount = 0;
    int failedFeeTransactionCount = 0;
    String ignoredFeeReason;
    String failedFeeReason;

    public TransactionCluster(ImportedTransactionBean main, List<ImportedTransactionBean> related) {
        this.main = main;
        this.related = related;
    }

    public void setFailedFee(int count, String reason) {
        this.failedFeeTransactionCount = count;
        this.failedFeeReason = reason;
    }

    public void setIgnoredFee(int count, String reason) {
        this.ignoredFeeTransactionCount = count;
        this.ignoredFeeReason = reason;
    }

}
