package io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v2;

import com.univocity.parsers.common.record.Record;
import com.univocity.parsers.csv.CsvParser;
import com.univocity.parsers.csv.CsvParserSettings;
import io.everytrade.server.plugin.api.parser.ParsingProblem;
import io.everytrade.server.plugin.api.parser.ParsingProblemType;
import io.everytrade.server.plugin.impl.everytrade.parser.exception.DataIgnoredException;
import io.everytrade.server.plugin.impl.everytrade.parser.exception.ParsingProcessException;
import io.everytrade.server.plugin.impl.everytrade.parser.exception.UnknownHeaderException;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.IExchangeSpecificParser;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.ExchangeBean;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class BinanceExchangeSpecificParser implements IExchangeSpecificParser {
    public static final Pattern DATE_PATTERN = Pattern.compile("^Date\\(.*\\)$");
    private static final String DEFAULT_DELIMITER = ",";

    private final String delimiter;
    private List<ParsingProblem> parsingProblems = List.of();

    enum RowType {
        HEADER, GROUP, GROUP_HEADER, GROUP_ROW
    }

    public BinanceExchangeSpecificParser() {
        delimiter = DEFAULT_DELIMITER;
    }

    public BinanceExchangeSpecificParser(String delimiter) {
        this.delimiter = delimiter;
    }

    @Override
    public List<? extends ExchangeBean> parse(
        File inputFile
    ) {
        parsingProblems = new ArrayList<>();
        final List<BinanceBeanV2> binanceBeans = new ArrayList<>();
        try (Reader reader = new FileReader(inputFile, StandardCharsets.UTF_8)) {
            final CsvParserSettings csvParserSettings = new CsvParserSettings();
            csvParserSettings.getFormat().setDelimiter(delimiter);
            csvParserSettings.setHeaderExtractionEnabled(false);
            CsvParser parser = new CsvParser(csvParserSettings);
            List<Record> allRecords = parser.parseAllRecords(reader);
            Map<Level2Header, Integer> level2HeaderIndexes = null;
            Map<Level1Header, Integer> level1HeaderIndexes = null;
            String columnValuePair = null;
            String columnValueType = null;
            String columnValueStatus = null;
            RowType rowType = RowType.HEADER;

            for (int i = 0; i < allRecords.size(); i++) {
                final Record record = allRecords.get(i);
                final String[] columnValues = record.getValues();
                if (i == 0) {
                    level1HeaderIndexes = createIndexesLevel1(columnValues);
                    continue;
                }

                final String columnValueDate = columnValues[level1HeaderIndexes.get(Level1Header.DATE)];
                rowType = evalRowType(rowType, columnValueDate);

                switch (rowType) {
                    case GROUP:
                        columnValuePair = columnValues[level1HeaderIndexes.get(Level1Header.PAIR)];
                        columnValueType = columnValues[level1HeaderIndexes.get(Level1Header.TYPE)];
                        columnValueStatus = columnValues[level1HeaderIndexes.get(Level1Header.STATUS)];
                        break;

                    case GROUP_HEADER:
                        checkLevel2Header(columnValues);
                        level2HeaderIndexes = createIndexesLevel2(columnValues);
                        break;

                    case GROUP_ROW:
                        if (level2HeaderIndexes == null) {
                            throw new ParsingProcessException("Unknown file structure. Unexpected row type " + rowType);
                        }
                        final String columnValueDateL2 = columnValues[level2HeaderIndexes.get(Level2Header.DATE)];
                        final String columnValueFilled = columnValues[level2HeaderIndexes.get(Level2Header.FILLED)];
                        final String columnValueTotal = columnValues[level2HeaderIndexes.get(Level2Header.TOTAL)];
                        final String columnValueFee = columnValues[level2HeaderIndexes.get(Level2Header.FEE)];
                        createExchangeBean(
                            parsingProblems,
                            binanceBeans,
                            columnValueDateL2,
                            columnValuePair,
                            columnValueType,
                            columnValueFilled,
                            columnValueTotal,
                            columnValueFee,
                            columnValueStatus
                        );
                        break;

                    default:
                        throw new ParsingProcessException("Unknown file structure - unexpected row type " + rowType);
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

    private void createExchangeBean(
        List<ParsingProblem> parsingProblems,
        List<BinanceBeanV2> binanceBeans,
        String columnValueDateL2,
        String columnValuePair,
        String columnValueType,
        String columnValueFilled,
        String columnValueTotal,
        String columnValueFee,
        String columnValueStatus
    ) {
        final String row = String.format(
            "%s,%s,%s,%s,%s,%s,%s",
            columnValueDateL2,
            columnValuePair,
            columnValueType,
            columnValueFilled,
            columnValueTotal,
            columnValueFee,
            columnValueStatus
        );
        try {
            binanceBeans.add(
                new BinanceBeanV2(
                    columnValueDateL2,
                    columnValuePair,
                    columnValueType,
                    columnValueFilled,
                    columnValueTotal,
                    columnValueFee,
                    columnValueStatus
                ));
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
    }

    private RowType evalRowType(RowType rowType, String columnValueDate) {
        if (columnValueDate == null || columnValueDate.isBlank() || columnValueDate.isEmpty()) {
            if (RowType.GROUP.equals(rowType)) {
                return RowType.GROUP_HEADER;
            } else {
                return RowType.GROUP_ROW;
            }
        } else {
            return RowType.GROUP;
        }
    }


    private void checkLevel2Header(String[] columnValues) {
        final Map<String, Integer> level2Headers = Arrays
            .stream(Level2Header.values())
            .collect(Collectors.toMap(Level2Header::getValue, v -> 0));

        for (String columnValue : columnValues) {
            if (columnValue == null) {
                continue;
            }
            final String correctedColumnValue = DATE_PATTERN.matcher(columnValue).find()
                ? Level2Header.DATE.getValue()
                : columnValue;
            if (level2Headers.containsKey(correctedColumnValue)) {
                Integer count = level2Headers.get(correctedColumnValue);
                level2Headers.put(correctedColumnValue, ++count);
            }
        }

        final Integer count = level2Headers.values().stream().reduce(0, Integer::sum);
        if (!count.equals(Level2Header.values().length)) {
            throw new UnknownHeaderException(String.format(
                "Level 2 header(%s) does not contains each element of (%s) exactly once.",
                Arrays.toString(columnValues),
                Arrays.toString(Arrays.stream(Level2Header.values()).map(Level2Header::getValue).toArray())
            ));
        }
    }

    private Map<Level1Header, Integer> createIndexesLevel1(String[] columnValues) {
        final Map<Level1Header, Integer> indexes = new TreeMap<>();
        for (int i = 0; i < columnValues.length; i++) {
            final Level1Header header = Level1Header.get(columnValues[i]);
            if (header != null) {
                indexes.put(header, i);
            }
        }
        return indexes;
    }

    private Map<Level2Header, Integer> createIndexesLevel2(String[] columnValues) {
        final Map<Level2Header, Integer> indexes = new TreeMap<>();
        for (int i = 0; i < columnValues.length; i++) {
            final Level2Header header = Level2Header.get(columnValues[i]);
            if (header != null) {
                indexes.put(header, i);
            }
        }
        return indexes;
    }
}

