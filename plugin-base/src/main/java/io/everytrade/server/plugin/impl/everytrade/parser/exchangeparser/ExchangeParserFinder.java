package io.everytrade.server.plugin.impl.everytrade.parser.exchangeparser;


import io.everytrade.server.plugin.impl.everytrade.parser.exchange.BinanceBeanV2;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.ExchangeBean;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;

public class ExchangeParserFinder {
    private final Map<Class<? extends ExchangeBean>, Class<? extends IExchangeParser>> parsers = new HashMap<>();

    public ExchangeParserFinder() {
        parsers.put(BinanceBeanV2.class, BinanceExchangeParser.class);
    }

    public IExchangeParser find(Class<? extends ExchangeBean> exchangeBean) {
        final Class<? extends IExchangeParser> exchange = parsers.get(exchangeBean);
        if (exchange == null) {
            return new DefaultExchangeParser(exchangeBean);
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
