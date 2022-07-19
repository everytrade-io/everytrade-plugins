package io.everytrade.server.plugin.impl.everytrade.parser.exchange;

import com.univocity.parsers.common.Context;
import com.univocity.parsers.common.processor.BeanListProcessor;
import com.univocity.parsers.csv.CsvParserSettings;
import io.everytrade.server.plugin.api.parser.ParsingProblem;
import io.everytrade.server.plugin.api.parser.ParsingProblemType;
import io.everytrade.server.plugin.impl.everytrade.parser.exception.DataIgnoredException;
import io.everytrade.server.plugin.impl.everytrade.parser.exception.ParsingProcessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import static io.everytrade.server.plugin.api.parser.ParsingProblemType.PARSED_ROW_IGNORED;
import static io.everytrade.server.plugin.api.parser.ParsingProblemType.ROW_PARSING_FAILED;
import static java.util.stream.Collectors.toList;

public class DefaultUnivocityExchangeSpecificParser implements IExchangeSpecificParser {
    private static final Logger LOG = LoggerFactory.getLogger(DefaultUnivocityExchangeSpecificParser.class);
    private static final String DEFAULT_DELIMITER = ",";
    private static final List<String> LINE_SEPARATORS = List.of("\n", "\r", "\r\n");

    private final Class<? extends ExchangeBean> exchangeBean;
    private final String delimiter;
    private final String lineSeparator;
    private List<ParsingProblem> parsingProblems = List.of();
    private int rowId = 1;

    public DefaultUnivocityExchangeSpecificParser(Class<? extends ExchangeBean> exchangeBean) {
        this(exchangeBean, DEFAULT_DELIMITER, null);
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

        if (lineSeparator != null) {
            return parse(inputFile, createParserSettings(parsingProblems, lineSeparator), exchangeBean);
        }

        var settings = LINE_SEPARATORS.stream()
            .map(separator -> createParserSettings(parsingProblems, separator))
            .collect(toList());

        Exception lastException = null;
        for (CsvParserSettings s : settings) {
            try {
                return parse(inputFile, s, exchangeBean);
            } catch (Exception e) {
                lastException = e;
                LOG.error("Failed to parse file. Trying another config...");
            }
        }
        throw new RuntimeException(lastException);
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
                    rowId++;
                    T bean = super.createBean(row, context);
                    if (bean == null) {
                        return null;
                    }
                    bean.setRowValues(row);
                    int rowNumber = context.currentColumn();
                    bean.setRowNumber(rowNumber);
                    bean.setRowId(rowId);
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

    private CsvParserSettings createParserSettings(List<ParsingProblem> parsingProblems, String lineSeparator) {
        var parserSettings = new CsvParserSettings();
        parserSettings.setHeaderExtractionEnabled(true);
        parserSettings.setProcessorErrorHandler((error, inputRow, context) -> {
            ParsingProblemType parsingProblemType = error instanceof DataIgnoredException ? PARSED_ROW_IGNORED : ROW_PARSING_FAILED;
            parsingProblems.add(new ParsingProblem(Arrays.toString(inputRow), error.getMessage(), parsingProblemType));
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
