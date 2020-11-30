package io.everytrade.server.plugin.impl.everytrade.parser.exchangeparser;

import io.everytrade.server.plugin.api.parser.RowError;
import io.everytrade.server.plugin.impl.everytrade.parser.MarkableFileInputStream;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.ExchangeBean;

import java.util.List;
public interface IExchangeParser {
    List<? extends ExchangeBean> parse(MarkableFileInputStream fileInputStream, List<RowError> rowErrors);
}
