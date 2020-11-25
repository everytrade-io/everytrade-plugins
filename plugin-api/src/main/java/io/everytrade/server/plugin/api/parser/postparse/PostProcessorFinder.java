package io.everytrade.server.plugin.api.parser.postparse;


import io.everytrade.server.plugin.api.parser.ExchangeBean;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Map;

public class PostProcessorFinder {
    private final Map<Class<? extends ExchangeBean>, Class<? extends IPostProcessor>> postProcessors;

    public PostProcessorFinder(Map<Class<? extends ExchangeBean>, Class<? extends IPostProcessor>> postProcessors) {
        this.postProcessors = Map.copyOf(postProcessors);
    }

    public IPostProcessor find(Class<? extends ExchangeBean> exchangeBean) {
        if (!postProcessors.containsKey(exchangeBean)) {
            return exchangeBeans -> null;
        }
        try {
            Constructor<?> cons = postProcessors.get(exchangeBean).getConstructor();
            return (IPostProcessor) cons.newInstance();
        } catch (
            InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e
        ) {
            throw new IllegalStateException(e);
        }
    }
}
