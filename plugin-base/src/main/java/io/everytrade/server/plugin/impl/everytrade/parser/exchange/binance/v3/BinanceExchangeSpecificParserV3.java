package io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v3;

import com.univocity.parsers.common.record.Record;
import com.univocity.parsers.csv.CsvParser;
import com.univocity.parsers.csv.CsvParserSettings;
import io.everytrade.server.model.CurrencyPair;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.everytrade.server.model.CurrencyPair.getTradeablePairs;
import static io.everytrade.server.plugin.api.parser.ParsingProblemType.PARSED_ROW_IGNORED;
import static io.everytrade.server.plugin.api.parser.ParsingProblemType.ROW_PARSING_FAILED;

public class BinanceExchangeSpecificParserV3 implements IExchangeSpecificParser {
    private final String delimiter;
    private List<ParsingProblem> parsingProblems = List.of();
    private static Map<String, CurrencyPair> fastCurrencyPair = new HashMap<>();

    public BinanceExchangeSpecificParserV3(String delimiter) {
        this.delimiter = delimiter;
    }

    static {
        getTradeablePairs().forEach(t -> fastCurrencyPair.put(
            String.format("%s%s", t.getBase().code(), t.getQuote().code()), t)
        );
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
            // remove row with header
            if(!allRecords.isEmpty()) {
                allRecords.remove(0);
            }
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
        var row = String.format("%s,%s,%s,%s,%s,%s,%s", vals[0], vals[1], vals[2], vals[3], vals[4], vals[5], vals[6]);
        try {
            return new BinanceBeanV3(vals[0], vals[1], vals[2], vals[4], vals[5], vals[6], fastCurrencyPair);
        } catch (DataIgnoredException e) {
            parsingProblems.add(
                new ParsingProblem(row, e.getMessage(), PARSED_ROW_IGNORED)
            );
        } catch (Exception e) {
            parsingProblems.add(
                new ParsingProblem(row, e.getMessage(), ROW_PARSING_FAILED)
            );
        }
        return null;
    }
}

