package io.everytrade.server.plugin.impl.everytrade.parser.postprocessor;

import io.everytrade.server.plugin.impl.everytrade.parser.exchange.BitfinexBeanV1;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.ExchangeBean;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;

public class PostProcessorFinder {
    private final Map<Class<? extends ExchangeBean>, Class<? extends IPostProcessor>> postProcessors = new HashMap<>();

    public PostProcessorFinder() {
        postProcessors.put(BitfinexBeanV1.class, BitfinexPostProcessor.class);
    }

    public IPostProcessor find(Class<? extends ExchangeBean> exchangeBean) {
        if (!postProcessors.containsKey(exchangeBean)) {
            return new DefaultPostProcessor();
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
