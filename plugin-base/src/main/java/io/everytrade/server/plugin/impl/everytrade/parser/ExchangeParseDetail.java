package io.everytrade.server.plugin.impl.everytrade.parser;

import io.everytrade.server.model.SupportedExchange;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.IExchangeSpecificParser;

import java.util.function.Supplier;

public class ExchangeParseDetail {

    private final Supplier<IExchangeSpecificParser> parserFactory;
    private final SupportedExchange supportedExchange;

    public ExchangeParseDetail(
        Supplier<IExchangeSpecificParser> exchangeSpecificParser,
        SupportedExchange supportedExchange
    ) {
        this.parserFactory = exchangeSpecificParser;
        this.supportedExchange = supportedExchange;
    }

    public Supplier<IExchangeSpecificParser> getParserFactory() {
        return parserFactory;
    }

    public SupportedExchange getSupportedExchange() {
        return supportedExchange;
    }
}
