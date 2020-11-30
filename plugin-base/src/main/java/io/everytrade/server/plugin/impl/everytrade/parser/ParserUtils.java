package io.everytrade.server.plugin.impl.everytrade.parser;

import io.everytrade.server.model.SupportedExchange;
import io.everytrade.server.plugin.impl.everytrade.parser.exception.ParsingProcessException;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.ExchangeBean;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public class ParserUtils {
    public static final int DECIMAL_DIGITS = 10;

    private ParserUtils() {
    }

    public static Instant parse(String dateTimePattern, String dateTime) {
        final DateTimeFormatter dateTimeFormatter =
            DateTimeFormatter.ofPattern(dateTimePattern, Locale.US).withZone(ZoneOffset.UTC);

        return dateTimeFormatter.parse(dateTime, Instant::from);
    }

    public static SupportedExchange getExchange(Class<? extends ExchangeBean> exchangeBean) {
        return createInstance(exchangeBean).getExchange();
    }

    public static String getDelimiter(Class<? extends ExchangeBean> exchangeBean) {
        return createInstance(exchangeBean).getDelimiter();
    }

    private static ExchangeBean createInstance(Class<? extends ExchangeBean> exchangeBean) {
        try {
            final Constructor<?> constructor = exchangeBean.getConstructor();
            final ExchangeBean bean = (ExchangeBean) constructor.newInstance();
            return bean;
        } catch (
            NoSuchMethodException | IllegalAccessException | InstantiationException | InvocationTargetException e
        ) {
            throw new ParsingProcessException(e);
        }
    }
}
