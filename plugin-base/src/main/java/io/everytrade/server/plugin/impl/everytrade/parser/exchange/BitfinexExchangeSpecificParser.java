package io.everytrade.server.plugin.impl.everytrade.parser.exchange;

import io.everytrade.server.plugin.api.parser.ParsingProblem;
import io.everytrade.server.plugin.impl.everytrade.parser.ParserUtils;
import io.everytrade.server.plugin.impl.everytrade.parser.exception.ParsingProcessException;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean.BitfinexBeanV1;

import java.io.File;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

public class BitfinexExchangeSpecificParser implements IExchangeSpecificParser {
    private static String delimiter;
    private List<ParsingProblem> parsingProblems = List.of();

    public BitfinexExchangeSpecificParser(String delimiter) {
        this.delimiter = delimiter;
    }
    @Override
    public List<? extends ExchangeBean> parse(File inputFile) {
        final DefaultUnivocityExchangeSpecificParser parser
            = new DefaultUnivocityExchangeSpecificParser(BitfinexBeanV1.class, delimiter);
        final List<? extends ExchangeBean> exchangeBeans = parser.parse(inputFile);
        final List<BitfinexBeanV1> bitfinexBeans = new ArrayList<>();
        for (ExchangeBean exchangeBean : exchangeBeans) {
            if (!(exchangeBean instanceof BitfinexBeanV1)){
                throw new ParsingProcessException(String.format(
                    "Unexpected parsed bean class: %s", exchangeBean.getClass()
                ));
            }
            bitfinexBeans.add((BitfinexBeanV1) exchangeBean);
        }
        parsingProblems = parser.getParsingProblems();
        final String datePattern = evalDatePattern(bitfinexBeans);
        return updateDate(bitfinexBeans, datePattern);
    }

    @Override
    public List<ParsingProblem> getParsingProblems() {
        return parsingProblems;
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
