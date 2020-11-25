package io.everytrade.server.plugin.api.parser.preparse;

import com.univocity.parsers.csv.CsvParserSettings;
import io.everytrade.server.plugin.api.parser.ExchangeBean;
import io.everytrade.server.plugin.api.parser.RowError;

import java.io.File;
import java.util.List;

public interface IExchangeParser {
    List<? extends ExchangeBean> parse(
        File inputFile, CsvParserSettings csvParserSettings, List<RowError> rowErrors
    );
}
