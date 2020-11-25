package io.everytrade.server.plugin.api.parser.preparse;


import io.everytrade.server.plugin.api.parser.ExchangeBean;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Map;

public class ParserFinder {
    private final Map<Class<? extends ExchangeBean>, Class<? extends IExchangeParser>> parsers;

    public ParserFinder(Map<Class<? extends ExchangeBean>, Class<? extends IExchangeParser>> parsers) {
        this.parsers = Map.copyOf(parsers);
    }

    public IExchangeParser find(Class<? extends ExchangeBean> exchangeBean) {
        final Class<? extends IExchangeParser> exchange = parsers.get(exchangeBean);
        if (exchange == null) {
            return null;
        }
        try {
            Constructor<? extends IExchangeParser> cons = exchange.getConstructor();
            return cons.newInstance();
        } catch (
            InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e
        ) {
            throw new IllegalStateException(e);
        }
    }
}
