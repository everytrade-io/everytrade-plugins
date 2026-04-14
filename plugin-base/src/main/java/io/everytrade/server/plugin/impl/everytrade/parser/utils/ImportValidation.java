package io.everytrade.server.plugin.impl.everytrade.parser.utils;

import io.everytrade.server.plugin.api.parser.ImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.TransactionCluster;
import io.everytrade.server.plugin.impl.everytrade.parser.exception.DataIgnoredException;

import java.math.BigDecimal;

public final class ImportValidation {

    private ImportValidation() {
    }

    public static void validateClusterOrThrow(TransactionCluster cluster) {
        if (cluster == null) {
            return;
        }

        validateBeanOrThrow(cluster.getMain(), "main");

        if (cluster.getRelated() != null) {
            for (int i = 0; i < cluster.getRelated().size(); i++) {
                validateBeanOrThrow(cluster.getRelated().get(i), "related[" + i + "]");
            }
        }
    }

    private static void validateBeanOrThrow(ImportedTransactionBean tx, String where) {
        if (tx == null) {
            return;
        }

        BigDecimal unitPrice = tx.getUnitPrice();
        if (unitPrice != null && unitPrice.signum() < 0) {
            throw new DataIgnoredException("Negative unit price (" + unitPrice + ") in " + where);
        }
    }
}