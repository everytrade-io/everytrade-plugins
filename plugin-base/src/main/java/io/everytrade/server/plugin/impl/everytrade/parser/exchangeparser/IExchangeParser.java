package io.everytrade.server.plugin.impl.everytrade.parser.exchangeparser;

import io.everytrade.server.plugin.api.parser.RowError;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.ExchangeBean;

import java.io.File;
import java.util.List;
public interface IExchangeParser {
    List<? extends ExchangeBean> parse(File inputFile, String delimiter, List<RowError> rowErrors);
}
