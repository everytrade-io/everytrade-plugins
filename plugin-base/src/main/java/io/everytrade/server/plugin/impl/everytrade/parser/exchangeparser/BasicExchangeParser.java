package io.everytrade.server.plugin.impl.everytrade.parser.exchangeparser;

import com.univocity.parsers.common.Context;
import com.univocity.parsers.common.processor.BeanListProcessor;
import com.univocity.parsers.csv.CsvParserSettings;
import io.everytrade.server.plugin.api.parser.RowError;
import io.everytrade.server.plugin.api.parser.RowErrorType;
import io.everytrade.server.plugin.impl.everytrade.parser.MarkableFileInputStream;
import io.everytrade.server.plugin.impl.everytrade.parser.ParserUtils;
import io.everytrade.server.plugin.impl.everytrade.parser.exception.DataIgnoredException;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.ExchangeBean;

import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Arrays;
import java.util.List;

public class BasicExchangeParser implements IExchangeParser {
    private final Class<? extends ExchangeBean> exchangeBean;

    public BasicExchangeParser(Class<? extends ExchangeBean> exchangeBean) {
        this.exchangeBean = exchangeBean;
    }

    @Override
    public List<? extends ExchangeBean> parse(MarkableFileInputStream fileInputStream, List<RowError> rowErrors) {
        final CsvParserSettings parserSettings = createParserSettings(rowErrors, exchangeBean);
        return parse(fileInputStream, parserSettings, exchangeBean);
    }

    private <T extends ExchangeBean> List<T> parse(
        MarkableFileInputStream fileInputStream,
        CsvParserSettings parserSettings,
        Class<T> exchangeBean
    ) {
        final Reader reader = new InputStreamReader(fileInputStream);
        final BeanListProcessor<T> rowProcessor = new BeanListProcessor<>(exchangeBean) {
            @Override
            public T createBean(String[] row, Context context) {
                T bean = super.createBean(row, context);
                if (bean == null) {
                    return null;
                }
                bean.setRowValues(row);
                bean.setRowNumber(context.currentColumn());
                return bean;
            }
        };
        parserSettings.setProcessor(rowProcessor);
        com.univocity.parsers.csv.CsvParser parser = new com.univocity.parsers.csv.CsvParser(parserSettings);
        parser.parse(reader);
        return rowProcessor.getBeans();
    }

    private CsvParserSettings createParserSettings(
        List<RowError> rowErrors,
        Class<? extends ExchangeBean> exchangeBean
    ) {
        CsvParserSettings parserSettings = new CsvParserSettings();
        parserSettings.setHeaderExtractionEnabled(true);
        parserSettings.setProcessorErrorHandler((error, inputRow, context) -> {
            RowErrorType rowErrorType = error instanceof DataIgnoredException
                ? RowErrorType.IGNORED : RowErrorType.FAILED;
            RowError rowError = new RowError(Arrays.toString(inputRow), error.getMessage(), rowErrorType);
            rowErrors.add(rowError);
        });
        final String delimiter = ParserUtils.getDelimiter(exchangeBean);
        parserSettings.getFormat().setDelimiter(delimiter);

        return parserSettings;
    }
}
