package io.everytrade.server.plugin.impl.everytrade.parser;

import com.univocity.parsers.common.DataValidationException;
import com.univocity.parsers.common.record.Record;
import com.univocity.parsers.csv.CsvFormat;
import com.univocity.parsers.csv.CsvParserSettings;
import io.everytrade.server.plugin.api.IPlugin;
import io.everytrade.server.plugin.api.parser.ConversionStatistic;
import io.everytrade.server.plugin.api.parser.ICsvParser;
import io.everytrade.server.plugin.api.parser.ImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.ParseResult;
import io.everytrade.server.plugin.api.parser.ParserDescriptor;
import io.everytrade.server.plugin.api.parser.RowError;
import io.everytrade.server.plugin.api.parser.RowErrorType;
import io.everytrade.server.plugin.impl.everytrade.EveryTradePlugin;
import io.everytrade.server.plugin.impl.everytrade.parser.exception.ParsingProcessException;
import io.everytrade.server.plugin.impl.everytrade.parser.exception.UnknownHeaderException;
import io.everytrade.server.plugin.impl.everytrade.parser.exchangeparser.IExchangeParser;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.BittrexBeanV1;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.ExchangeBean;
import io.everytrade.server.plugin.impl.everytrade.parser.exchangeparser.ExchangeParserFinder;
import io.everytrade.server.plugin.impl.everytrade.parser.postprocessor.ConversionParams;
import io.everytrade.server.plugin.impl.everytrade.parser.postprocessor.IPostProcessor;
import io.everytrade.server.plugin.impl.everytrade.parser.postprocessor.PostProcessorFinder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EverytradeCsvParser implements ICsvParser {
    private static final String ID = EveryTradePlugin.ID + IPlugin.PLUGIN_PATH_SEPARATOR + "everytradeParser";

    private static final Map<String, Class<? extends ExchangeBean>> exchangeSignatures = new HashMap<>();

    static {
        exchangeSignatures.put(
            "BTX-001:|OrderUuid|Exchange|Type|Quantity|CommissionPaid|Price|Closed|",
            BittrexBeanV1.class
        );
    }

    public static final ParserDescriptor DESCRIPTOR = new ParserDescriptor(
        ID,
        new ArrayList<>(exchangeSignatures.keySet())
    );

    private static final String[] EXPLICIT_DELIMITERS = new String[]{";", ","};
    private final Logger log = LoggerFactory.getLogger(this.getClass());


    @Override
    public String getId() {
        return ID;
    }

    @Override
    public ParseResult parse(File file, String signature) {
        final Class<? extends ExchangeBean> exchangeBean = exchangeSignatures.get(signature);
        List<RowError> rowErrors = new ArrayList<>();
        final CsvFormat csvFormat = analyze(file, signature);
        final IExchangeParser exchangeParser = new ExchangeParserFinder().find(exchangeBean);

        List<? extends ExchangeBean> listBeans = exchangeParser.parse(file, csvFormat, rowErrors);

        final IPostProcessor postProcessor = new PostProcessorFinder().find(exchangeBean);
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

    public CsvFormat analyze(File file, String signature) {
        for (int i = 0; i < EXPLICIT_DELIMITERS.length + 1; i++) {
            final CsvFormat csvFormat;
            if (i == 0) {
                csvFormat = null;
            } else {
                csvFormat = new CsvFormat();
                csvFormat.setDelimiter(EXPLICIT_DELIMITERS[i - 1]);
            }
            final CsvFormat detectedFormat = analyzeFile(file, csvFormat, signature);
            if (detectedFormat != null) {
                return detectedFormat;
            }
        }
        throw new UnknownHeaderException();
    }

    private CsvFormat analyzeFile(File file, CsvFormat format, String signature) {
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
            final SignatureValidator signatureValidator = new SignatureValidator();
            if (signatureValidator.validate(csvHeader, signature)) {
                if (format != null) {
                    return format;
                } else {
                    return parser.getDetectedFormat();
                }
            } else {
                return null;
            }
        } catch (IOException e) {
            throw new ParsingProcessException(e);
        }
    }
}
