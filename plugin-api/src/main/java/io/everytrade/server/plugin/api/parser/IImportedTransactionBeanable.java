package io.everytrade.server.plugin.api.parser;


import io.everytrade.server.plugin.api.parser.postparse.ConversionParams;

public interface IImportedTransactionBeanable {
    ImportedTransactionBean toImportedTransactionBean(ConversionParams conversionParams);
}
