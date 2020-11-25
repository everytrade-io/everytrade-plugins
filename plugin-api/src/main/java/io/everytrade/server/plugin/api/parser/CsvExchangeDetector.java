package io.everytrade.server.plugin.api.parser;

import io.everytrade.server.model.SupportedExchange;
import io.everytrade.server.plugin.api.parser.exception.ParsingProcessException;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Map;

public class CsvExchangeDetector {
    private CsvExchangeDetector() {
    }
    public static SupportedExchange getExchange(File file, Map<String, Class<? extends ExchangeBean>> beanSignatures)  {
        CsvParser csvParser = new CsvParser(beanSignatures, Map.of(), Map.of());
        CsvParser.CsvStreamInfo streamInfo = csvParser.analyze(file);

        try {
            Constructor<?> cons = streamInfo.getExchangeBean().getConstructor();
            ExchangeBean exchangeBean = (ExchangeBean) cons.newInstance();
            return exchangeBean.getExchange();

        } catch (
            NoSuchMethodException | IllegalAccessException | InstantiationException | InvocationTargetException e
        ) {
            throw new ParsingProcessException(e);
        }
    }
}
