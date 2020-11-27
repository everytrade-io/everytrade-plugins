package io.everytrade.server.plugin.impl.everytrade.parser.exchangeparser;

import com.univocity.parsers.csv.CsvFormat;
import io.everytrade.server.plugin.api.parser.RowError;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.ExchangeBean;

import java.io.File;
import java.util.List;
public interface IExchangeParser {
    List<? extends ExchangeBean> parse(
        File inputFile, CsvFormat csvFormat, List<RowError> rowErrors
    );
}
