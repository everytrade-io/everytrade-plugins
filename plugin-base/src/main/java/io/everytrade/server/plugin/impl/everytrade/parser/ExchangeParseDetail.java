package io.everytrade.server.plugin.impl.everytrade.parser;

import io.everytrade.server.model.SupportedExchange;
import io.everytrade.server.plugin.csv.CsvHeader;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.IExchangeSpecificParser;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NonNull;
import lombok.Value;

import java.util.List;
import java.util.function.Supplier;

@Value
@AllArgsConstructor
@Builder
public class ExchangeParseDetail {

    @NonNull
    List<CsvHeader> headers;

    @NonNull
    Supplier<IExchangeSpecificParser> parserFactory;

    @NonNull
    SupportedExchange supportedExchange;
}

