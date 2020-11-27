package io.everytrade.server.plugin.impl.everytrade.parser;


import io.everytrade.server.plugin.api.parser.ImportedTransactionBean;
import io.everytrade.server.plugin.impl.everytrade.parser.postprocessor.ConversionParams;

public interface IImportedTransactionBeanable {
    ImportedTransactionBean toImportedTransactionBean(ConversionParams conversionParams);
}
