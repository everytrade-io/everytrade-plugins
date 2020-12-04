package io.everytrade.server.plugin.impl.everytrade.parser.exchange;

import com.univocity.parsers.common.Context;
import com.univocity.parsers.common.processor.BeanListProcessor;
import com.univocity.parsers.csv.CsvParserSettings;
import io.everytrade.server.plugin.api.parser.RowError;
import io.everytrade.server.plugin.api.parser.RowErrorType;
import io.everytrade.server.plugin.impl.everytrade.parser.exception.DataIgnoredException;
import io.everytrade.server.plugin.impl.everytrade.parser.exception.ParsingProcessException;

import java.io.File;
import java.io.FileReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
public class DefaultUnivocityExchangeSpecificParser implements IExchangeSpecificParser {
    private final Class<? extends ExchangeBean> exchangeBean;

    public DefaultUnivocityExchangeSpecificParser(Class<? extends ExchangeBean> exchangeBean) {
        this.exchangeBean = exchangeBean;
    }

    @Override
    public List<? extends ExchangeBean> parse(File inputFile, String delimiter, List<RowError> rowErrors) {
        final CsvParserSettings parserSettings = createParserSettings(rowErrors, delimiter);
        return parse(inputFile, parserSettings, exchangeBean);
    }

    private <T extends ExchangeBean> List<T> parse(File file, CsvParserSettings parserSettings, Class<T> exchangeBean) {
        try (Reader reader = new FileReader(file, StandardCharsets.UTF_8)) {
            BeanListProcessor<T> rowProcessor = new BeanListProcessor<>(exchangeBean) {
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
        } catch (Exception e) {
            throw new ParsingProcessException(String.format("Parsing error. %s", e.getMessage()));
        }
    }

    private CsvParserSettings createParserSettings(
        List<RowError> rowErrors,
        String delimiter
    ) {
        CsvParserSettings parserSettings = new CsvParserSettings();
        parserSettings.setHeaderExtractionEnabled(true);
        parserSettings.setProcessorErrorHandler((error, inputRow, context) -> {
            RowErrorType rowErrorType = error instanceof DataIgnoredException
                ? RowErrorType.IGNORED : RowErrorType.FAILED;
            RowError rowError = new RowError(Arrays.toString(inputRow), error.getMessage(), rowErrorType);
            rowErrors.add(rowError);
        });
        parserSettings.getFormat().setDelimiter(delimiter);
        parserSettings.getFormat().setComment('\0'); // No symbol for comments

        return parserSettings;
    }
}
