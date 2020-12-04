package io.everytrade.server.plugin.impl.everytrade.parser.exchange;

import io.everytrade.server.plugin.api.parser.RowError;
import io.everytrade.server.plugin.impl.everytrade.parser.ParserUtils;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean.BitfinexBeanV1;

import java.io.File;
import java.time.format.DateTimeParseException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

public class BitfinexExchangeSpecificParser implements IExchangeSpecificParser {
    public static final String DELIMITER = ",";

    @Override
    public List<? extends ExchangeBean> parse(File inputFile, List<RowError> rowErrors) {

        final DefaultUnivocityExchangeSpecificParser parser
            = new DefaultUnivocityExchangeSpecificParser(BitfinexBeanV1.class, DELIMITER);

        final List<BitfinexBeanV1> beans = (List<BitfinexBeanV1>) parser.parse(inputFile, rowErrors);
        final String datePattern = evalDatePattern(beans);
        return updateDate(beans, datePattern);

    }

    private List<? extends ExchangeBean> updateDate(List<BitfinexBeanV1> beans, String datePattern) {
        for (BitfinexBeanV1 bean : beans) {
            final String date = bean.getDate();
            bean.setDateConverted(ParserUtils.parse(datePattern, date));
        }
        return beans;
    }

    private String evalDatePattern(List<BitfinexBeanV1> exchangeBeans) {
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
        for (BitfinexBeanV1 bitfinexBeanV1 : exchangeBeans) {
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
                return patterns.iterator().next();
            }
            if (patterns.isEmpty()) {
                return defaultPattern;
            }
        }
        final long count = patterns.stream().filter(p -> p.endsWith(".SSS")).count();
        if (patterns.size() == count) {
            return defaultPatternMillis;
        }
        return defaultPattern;
    }
}
