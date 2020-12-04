package io.everytrade.server.plugin.impl.everytrade.parser.exchange;

import io.everytrade.server.plugin.api.parser.RowError;

import java.io.File;
import java.util.List;
public interface IExchangeSpecificParser {
    List<? extends ExchangeBean> parse(File inputFile);
    List<RowError> getRowErrors();
}
