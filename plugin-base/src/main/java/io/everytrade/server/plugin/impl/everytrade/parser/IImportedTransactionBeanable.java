package io.everytrade.server.plugin.impl.everytrade.parser;


import io.everytrade.server.plugin.api.parser.ImportedTransactionBean;

public interface IImportedTransactionBeanable {
    ImportedTransactionBean toImportedTransactionBean();
}
