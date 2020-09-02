package io.everytrade.server.plugin.api.parser;

import java.util.List;

public class ParseResult {
    private final List<ImportedTransactionBean> importedTransactionBeans;

    //TODO: Inline the statistics? Why separate class?
    private final ConversionStatistic conversionStatistic;

    public ParseResult(
        List<ImportedTransactionBean> importedTransactionBeans,
        ConversionStatistic conversionStatistic
    ) {
        this.importedTransactionBeans = importedTransactionBeans;
        this.conversionStatistic = conversionStatistic;
    }

    public ConversionStatistic getConversionStatistic() {
        return conversionStatistic;
    }

    public List<ImportedTransactionBean> getImportedTransactionBeans() {
        return importedTransactionBeans;
    }
}
