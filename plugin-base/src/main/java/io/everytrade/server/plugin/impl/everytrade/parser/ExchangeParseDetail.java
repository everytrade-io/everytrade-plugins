package io.everytrade.server.plugin.impl.everytrade.parser;

import io.everytrade.server.model.SupportedExchange;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.IExchangeSpecificParser;

public class ExchangeParseDetail {
    private final IExchangeSpecificParser exchangeSpecificParser;
    private final SupportedExchange supportedExchange;
    private final String delimiter;

    public ExchangeParseDetail(
        IExchangeSpecificParser exchangeSpecificParser,
        SupportedExchange supportedExchange,
        String delimiter
    ) {
        this.exchangeSpecificParser = exchangeSpecificParser;
        this.supportedExchange = supportedExchange;
        this.delimiter = delimiter;
    }

    public IExchangeSpecificParser getExchangeSpecificParser() {
        return exchangeSpecificParser;
    }

    public SupportedExchange getSupportedExchange() {
        return supportedExchange;
    }

    public String getDelimiter() {
        return delimiter;
    }
}
