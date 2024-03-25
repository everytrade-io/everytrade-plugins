package io.everytrade.server.plugin.impl.everytrade.parser.utils;

import io.everytrade.server.plugin.api.parser.ImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.TransactionCluster;
import io.everytrade.server.plugin.impl.everytrade.parser.exception.DataIgnoredException;

import java.util.List;

public class ClusterValidator {

    static final double MAX_VOLUME_VALUE = 9999999999.999;

    public static void validateNumberSize(double number) {
        if (number > MAX_VOLUME_VALUE) {
            String message = "Number exceeds 10 digits";
            throw new DataIgnoredException(message);
        }
    }

    public static void clusterValidator(TransactionCluster cluster) {
        if (cluster.getMain() != null) {
            validateTransaction(cluster.getMain());
        }
        List<ImportedTransactionBean> relatedTransactions = cluster.getRelated().stream().toList();
        relatedTransactions.forEach(ClusterValidator::validateTransaction);
    }

    public static void validateTransaction(ImportedTransactionBean tx) {
        validateNumberSize(tx.getVolume().doubleValue());
    }

}