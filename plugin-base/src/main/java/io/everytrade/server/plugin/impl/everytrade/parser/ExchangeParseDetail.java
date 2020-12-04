package io.everytrade.server.plugin.impl.everytrade.parser;

import io.everytrade.server.model.SupportedExchange;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.IExchangeSpecificParser;

public class ExchangeParseDetail {
    private final IExchangeSpecificParser exchangeSpecificParser;
    private final SupportedExchange supportedExchange;

    public ExchangeParseDetail(
        IExchangeSpecificParser exchangeSpecificParser,
        SupportedExchange supportedExchange
    ) {
        this.exchangeSpecificParser = exchangeSpecificParser;
        this.supportedExchange = supportedExchange;
    }

    public IExchangeSpecificParser getExchangeSpecificParser() {
        return exchangeSpecificParser;
    }

    public SupportedExchange getSupportedExchange() {
        return supportedExchange;
    }
}
