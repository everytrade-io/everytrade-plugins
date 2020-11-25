package io.everytrade.server.plugin.api.parser;

import com.univocity.parsers.common.Context;
import com.univocity.parsers.common.DataValidationException;
import com.univocity.parsers.common.processor.BeanListProcessor;
import com.univocity.parsers.common.record.Record;
import com.univocity.parsers.csv.CsvFormat;
import com.univocity.parsers.csv.CsvParserSettings;

import io.everytrade.server.plugin.api.parser.exception.DataIgnoredException;
import io.everytrade.server.plugin.api.parser.exception.ParsingProcessException;
import io.everytrade.server.plugin.api.parser.exception.UnknownHeaderException;
import io.everytrade.server.plugin.api.parser.postparse.ConversionParams;
import io.everytrade.server.plugin.api.parser.postparse.IPostProcessor;
import io.everytrade.server.plugin.api.parser.postparse.PostProcessorFinder;
import io.everytrade.server.plugin.api.parser.preparse.IExchangeParser;
import io.everytrade.server.plugin.api.parser.preparse.ParserFinder;
import io.everytrade.server.plugin.api.parser.signature.ExchangeFormatFinder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class CsvParser {
    private static final String[] EXPLICIT_DELIMITERS = new String[]{";", ","};
    private final Logger log = LoggerFactory.getLogger(getClass());
    private final Map<String, Class<? extends ExchangeBean>> beanSignatures;
    private final Map<Class<? extends ExchangeBean>, Class<? extends IPostProcessor>> postProcessors;
    private final Map<Class<? extends ExchangeBean>, Class<? extends IExchangeParser>> parsers;

    public CsvParser(
        Map<String, Class<? extends ExchangeBean>> beanSignatures,
        Map<Class<? extends ExchangeBean>, Class<? extends IPostProcessor>> postProcessors,
        Map<Class<? extends ExchangeBean>, Class<? extends IExchangeParser>> parsers
    ) {
        Objects.requireNonNull(beanSignatures);
        Objects.requireNonNull(postProcessors);
        Objects.requireNonNull(parsers);
        this.beanSignatures = Map.copyOf(beanSignatures);
        this.postProcessors = Map.copyOf(postProcessors);
        this.parsers = Map.copyOf(parsers);
    }

    public ParseResult parseCsvFile(File file) {
        List<RowError> rowErrors = new ArrayList<>();
        List<? extends ExchangeBean> listBeans;

        CsvStreamInfo streamInfo = analyze(file);

        CsvParserSettings csvParserSettings
            = createParserSettings(streamInfo.csvFormat, rowErrors, streamInfo.exchangeBean);

        final IExchangeParser exchangeParser = new ParserFinder(parsers).find(streamInfo.exchangeBean);
        if (exchangeParser != null) {
            listBeans = exchangeParser.parse(file, csvParserSettings, rowErrors);
        } else {
            listBeans = parse(file, csvParserSettings, streamInfo.exchangeBean);
        }

        final IPostProcessor postProcessor = new PostProcessorFinder(postProcessors).find(streamInfo.exchangeBean);
        final ConversionParams conversionParams = postProcessor.evalConversionParams(listBeans);

        int ignoredFeeCount = 0;
        List<ImportedTransactionBean> importedTransactionBeans = new ArrayList<>();
        for (ExchangeBean p : listBeans) {
            try {
                final ImportedTransactionBean importedTransactionBean = p.toImportedTransactionBean(conversionParams);
                importedTransactionBeans.add(importedTransactionBean);
                if (importedTransactionBean.getImportDetail().isIgnoredFee()) {
                    ignoredFeeCount++;
                }
            } catch (DataValidationException e) {
                rowErrors.add(new RowError(p.rowToString(), e.getMessage(), RowErrorType.FAILED));
            }
        }

        log.info("{} transaction(s) parsed successfully.", importedTransactionBeans.size());
        if (!rowErrors.isEmpty()) {
            log.warn("{} row(s) not parsed.", rowErrors.size());
        }

        return new ParseResult(importedTransactionBeans, new ConversionStatistic(rowErrors, ignoredFeeCount));
    }

    private CsvParserSettings createParserSettings(
        CsvFormat csvFormat,
        List<RowError> rowErrors,
        Class<? extends ExchangeBean> exchangeBean
    ) {
        CsvParserSettings parserSettings = new CsvParserSettings();
        parserSettings.setFormat(csvFormat);
        parserSettings.setHeaderExtractionEnabled(true);

        parserSettings.setProcessorErrorHandler((error, inputRow, context) -> {
            RowErrorType rowErrorType = error instanceof DataIgnoredException
                ? RowErrorType.IGNORED : RowErrorType.FAILED;
            RowError rowError = new RowError(Arrays.toString(inputRow), error.getMessage(), rowErrorType);
            rowErrors.add(rowError);
        });

        try {
            Constructor<?> constructor = exchangeBean.getConstructor();
            ExchangeBean bean = (ExchangeBean) constructor.newInstance();
            bean.updateParserSettings(parserSettings);
        } catch (
            NoSuchMethodException | IllegalAccessException | InstantiationException | InvocationTargetException e
        ) {
            throw new ParsingProcessException(e);
        }

        return parserSettings;
    }

    public CsvStreamInfo analyze(File file) {
        for (int i = 0; i < EXPLICIT_DELIMITERS.length + 1; i++) {
            final CsvFormat csvFormat;
            if (i == 0) {
                csvFormat = null;
            } else {
                csvFormat = new CsvFormat();
                csvFormat.setDelimiter(EXPLICIT_DELIMITERS[i - 1]);
            }
            try {
                return analyzeFile(file, csvFormat);
            } catch (UnknownHeaderException e) {
            }
        }
        throw new UnknownHeaderException();
    }

    private CsvStreamInfo analyzeFile(File file, CsvFormat format) {
        try (Reader reader = new FileReader(file, StandardCharsets.UTF_8)) {
            CsvParserSettings parserSettings = new CsvParserSettings();
            if (format != null) {
                parserSettings.setFormat(format);
            } else {
                parserSettings.detectFormatAutomatically();
            }
            parserSettings.getFormat().setComment('\0'); // No symbol for comments
            com.univocity.parsers.csv.CsvParser parser = new com.univocity.parsers.csv.CsvParser(parserSettings);
            parser.beginParsing(reader);
            Record record = parser.parseNextRecord();
            List<String> csvHeader = Arrays.asList(record.getValues());
            // throws UnknowHeaderException or ParsingProcesException in cases exactly one bean not found.
            Class<? extends ExchangeBean> exchangeBean = new ExchangeFormatFinder(beanSignatures).find(csvHeader);
            if (format != null) {
                return new CsvStreamInfo(format, exchangeBean);
            } else {
                return new CsvStreamInfo(parser.getDetectedFormat(), exchangeBean);
            }
        } catch (IOException e) {
            throw new ParsingProcessException(e);
        }
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

    static class CsvStreamInfo {
        final private CsvFormat csvFormat;
        final private Class<? extends ExchangeBean> exchangeBean;

        CsvStreamInfo(CsvFormat csvFormat, Class<? extends ExchangeBean> exchangeBean) {
            this.csvFormat = csvFormat;
            this.exchangeBean = exchangeBean;
        }

        public Class<? extends ExchangeBean> getExchangeBean() {
            return exchangeBean;
        }
    }
}