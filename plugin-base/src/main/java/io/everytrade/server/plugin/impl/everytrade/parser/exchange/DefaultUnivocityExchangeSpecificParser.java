package io.everytrade.server.plugin.impl.everytrade.parser.exchange;

import com.univocity.parsers.common.Context;
import com.univocity.parsers.common.processor.BeanListProcessor;
import com.univocity.parsers.csv.CsvParserSettings;
import io.everytrade.server.plugin.api.parser.ParsingProblem;
import io.everytrade.server.plugin.api.parser.PrarsingProblemType;
import io.everytrade.server.plugin.impl.everytrade.parser.exception.DataIgnoredException;
import io.everytrade.server.plugin.impl.everytrade.parser.exception.ParsingProcessException;

import java.io.File;
import java.io.FileReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class DefaultUnivocityExchangeSpecificParser implements IExchangeSpecificParser {
    private static final String DEFAUL_DELIMITER = ",";
    private final Class<? extends ExchangeBean> exchangeBean;
    private final String delimiter;
    private final String lineSeparator;
    private List<ParsingProblem> parsingProblems = List.of();

    public DefaultUnivocityExchangeSpecificParser(Class<? extends ExchangeBean> exchangeBean) {
        this(exchangeBean, DEFAUL_DELIMITER, null);
    }

    public DefaultUnivocityExchangeSpecificParser(
        Class<? extends ExchangeBean> exchangeBean,
        String delimiter
    ) {
        this(exchangeBean, delimiter, null);
    }

    public DefaultUnivocityExchangeSpecificParser(
        Class<? extends ExchangeBean> exchangeBean,
        String delimiter,
        String lineSeparator
    ) {
        Objects.requireNonNull(this.exchangeBean = exchangeBean);
        Objects.requireNonNull(this.delimiter = delimiter);
        this.lineSeparator = lineSeparator;
    }

    @Override
    public List<? extends ExchangeBean> parse(File inputFile) {
        parsingProblems = new ArrayList<>();
        final CsvParserSettings parserSettings = createParserSettings(parsingProblems);
        return parse(inputFile, parserSettings, exchangeBean);
    }

    @Override
    public List<ParsingProblem> getParsingProblems() {
        return parsingProblems;
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
        List<ParsingProblem> parsingProblems
    ) {
        CsvParserSettings parserSettings = new CsvParserSettings();
        parserSettings.setHeaderExtractionEnabled(true);
        parserSettings.setProcessorErrorHandler((error, inputRow, context) -> {
            PrarsingProblemType prarsingProblemType = error instanceof DataIgnoredException
                ? PrarsingProblemType.PARSED_ROW_IGNORED : PrarsingProblemType.ROW_PARSING_FAILED;
            ParsingProblem parsingProblem
                = new ParsingProblem(Arrays.toString(inputRow), error.getMessage(), prarsingProblemType);
            parsingProblems.add(parsingProblem);
        });
        parserSettings.getFormat().setDelimiter(delimiter);
        //default setting is autodetect
        if (lineSeparator != null) {
            parserSettings.getFormat().setLineSeparator(lineSeparator);
        }
        parserSettings.getFormat().setComment('\0'); // No symbol for comments

        return parserSettings;
    }
}
