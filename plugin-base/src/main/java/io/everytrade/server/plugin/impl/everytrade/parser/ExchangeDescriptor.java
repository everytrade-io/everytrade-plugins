package io.everytrade.server.plugin.impl.everytrade.parser;

import io.everytrade.server.model.SupportedExchange;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.ExchangeBean;

public class ExchangeDescriptor {
    private final Class<? extends ExchangeBean> exchangeBean;
    private final SupportedExchange supportedExchange;
    private final String delimiter;

    public ExchangeDescriptor(
        Class<? extends ExchangeBean> exchangeBean,
        SupportedExchange supportedExchange,
        String delimiter
    ) {
        this.exchangeBean = exchangeBean;
        this.supportedExchange = supportedExchange;
        this.delimiter = delimiter;
    }

    public Class<? extends ExchangeBean> getExchangeBean() {
        return exchangeBean;
    }

    public SupportedExchange getSupportedExchange() {
        return supportedExchange;
    }

    public String getDelimiter() {
        return delimiter;
    }
}
