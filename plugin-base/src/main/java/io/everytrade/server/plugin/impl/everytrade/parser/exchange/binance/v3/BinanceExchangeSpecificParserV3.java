package io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v3;

import com.univocity.parsers.common.record.Record;
import com.univocity.parsers.csv.CsvParser;
import com.univocity.parsers.csv.CsvParserSettings;
import io.everytrade.server.plugin.api.parser.ParsingProblem;
import io.everytrade.server.plugin.api.parser.ParsingProblemType;
import io.everytrade.server.plugin.impl.everytrade.parser.exception.DataIgnoredException;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.ExchangeBean;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.IExchangeSpecificParser;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class BinanceExchangeSpecificParserV3 implements IExchangeSpecificParser {
    private static final String DEFAULT_DELIMITER = ",";
    private final String delimiter;
    private List<ParsingProblem> parsingProblems = List.of();

    public BinanceExchangeSpecificParserV3() {
        delimiter = DEFAULT_DELIMITER;
    }

    public BinanceExchangeSpecificParserV3(String delimiter) {
        this.delimiter = delimiter;
    }

    @Override
    public List<? extends ExchangeBean> parse(File inputFile) {
        parsingProblems = new ArrayList<>();
        final List<BinanceBeanV3> binanceBeans = new ArrayList<>();

        try (Reader reader = new FileReader(inputFile, StandardCharsets.UTF_8)) {
            final CsvParserSettings csvParserSettings = new CsvParserSettings();
            csvParserSettings.getFormat().setDelimiter(delimiter);
            csvParserSettings.setHeaderExtractionEnabled(false);
            CsvParser parser = new CsvParser(csvParserSettings);
            List<Record> allRecords = parser.parseAllRecords(reader);

            for (final Record record : allRecords) {
                BinanceBeanV3 binanceBean = parseExchangeBean(record.getValues());
                if (binanceBean != null) {
                    binanceBeans.add(binanceBean);
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return binanceBeans;
    }

    @Override
    public List<ParsingProblem> getParsingProblems() {
        return parsingProblems;
    }

    private BinanceBeanV3 parseExchangeBean(String[] vals) {
        final String row = String.format(
            "%s,%s,%s,%s,%s,%s,%s", vals[0], vals[1], vals[2], vals[3], vals[4], vals[5], vals[6]
        );
        try {
            return new BinanceBeanV3(vals[0], vals[1], vals[2], vals[4], vals[5], vals[6]);
        } catch (DataIgnoredException e) {
            parsingProblems.add(
                new ParsingProblem(
                    row,
                    e.getMessage(),
                    ParsingProblemType.PARSED_ROW_IGNORED
                )
            );
        } catch (Exception e) {
            parsingProblems.add(
                new ParsingProblem(
                    row,
                    e.getMessage(),
                    ParsingProblemType.ROW_PARSING_FAILED
                )
            );
        }
        return null;
    }
}

