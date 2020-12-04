package io.everytrade.server.plugin.impl.everytrade.parser.exchange;

import com.univocity.parsers.csv.CsvParserSettings;
import io.everytrade.server.plugin.api.parser.RowError;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean.OkexBeanV1;

import java.io.File;
import java.util.List;

public class OkexExchangeSpecificParser implements IExchangeSpecificParser{
    @Override
    public List<? extends ExchangeBean> parse(File inputFile, String delimiter, List<RowError> rowErrors) {
        final DefaultUnivocityExchangeSpecificParser defaultParser
            = new DefaultUnivocityExchangeSpecificParser(OkexBeanV1.class);
        final CsvParserSettings parserSettings = defaultParser.createParserSettings(rowErrors, delimiter);
        parserSettings.getFormat().setLineSeparator("\n");
        return defaultParser.parse(inputFile,  parserSettings);
    }
}
