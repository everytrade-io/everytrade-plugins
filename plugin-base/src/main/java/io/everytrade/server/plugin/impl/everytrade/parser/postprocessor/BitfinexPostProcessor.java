package io.everytrade.server.plugin.impl.everytrade.parser.postprocessor;

import io.everytrade.server.plugin.impl.everytrade.parser.ParserUtils;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.BitfinexBeanV1;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.ExchangeBean;

import java.time.format.DateTimeParseException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

public class BitfinexPostProcessor implements IPostProcessor {

    @Override
    public ConversionParams evalConversionParams(List<? extends ExchangeBean> exchangeBeans) {
        final String defaultPattern = "dd-MM-yy HH:mm:ss";
        final String defaultPatternMillis = "dd-MM-yy HH:mm:ss.SSS";
        final Set<String> patterns = new HashSet<>(Set.of(
            defaultPattern,
            "yy-MM-dd HH:mm:ss",
            "MM-dd-yy HH:mm:ss",
            defaultPatternMillis,
            "yy-MM-dd HH:mm:ss.SSS",
            "MM-dd-yy HH:mm:ss.SSS"
        ));
        for (ExchangeBean exchangeBean : exchangeBeans) {
            if (!(exchangeBean instanceof BitfinexBeanV1)) {
                throw new IllegalStateException(String.format(
                    "Unexpected type of bean found %s.", exchangeBean.getClass().getName()
                ));
            }
            BitfinexBeanV1 bitfinexBeanV1 = (BitfinexBeanV1) exchangeBean;
            final String date = bitfinexBeanV1.getDate();
            Iterator<String> iterator = patterns.iterator();
            while (iterator.hasNext()) {
                try {
                    ParserUtils.parse(iterator.next(), date);
                } catch (DateTimeParseException e) {
                    iterator.remove();
                }
            }
            if (patterns.size() == 1) {
                return new ConversionParams().setDateTimePattern(patterns.iterator().next());
            }
            if (patterns.isEmpty()) {
                return new ConversionParams().setDateTimePattern(defaultPattern);
            }
        }
        final long count = patterns.stream().filter(p -> p.endsWith(".SSS")).count();
        if (patterns.size() == count) {
            return new ConversionParams().setDateTimePattern(defaultPatternMillis);
        }
        return new ConversionParams().setDateTimePattern(defaultPattern);
    }
}
