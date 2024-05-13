package io.everytrade.server.plugin.impl.everytrade.parser.utils;

import io.everytrade.server.plugin.api.parser.ImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.TransactionCluster;
import io.everytrade.server.plugin.impl.everytrade.parser.exception.DataIgnoredException;

import java.math.BigDecimal;
import java.util.List;

import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v4.BinanceBeanV4.BINANCE_CARD_SPENDING;

public class ClusterValidator {

    static final BigDecimal MAX_VOLUME_VALUE = new BigDecimal("99999999999999999999.999");

    public static void validateNumberSize(BigDecimal number) {
        if (number.compareTo(MAX_VOLUME_VALUE) > 0) {
            String message = "Number exceeds 20 digits";
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
        validateNumberSize(tx.getVolume());
    }

}