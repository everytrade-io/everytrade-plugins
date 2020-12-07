package io.everytrade.server.plugin.impl.everytrade.parser;


import io.everytrade.server.plugin.api.parser.TransactionCluster;

public interface IImportableBean {
    TransactionCluster toTransactionCluster();
}
