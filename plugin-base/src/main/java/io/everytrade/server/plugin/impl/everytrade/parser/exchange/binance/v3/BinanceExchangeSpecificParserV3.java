package io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v3;

import com.univocity.parsers.common.DataValidationException;
import com.univocity.parsers.common.record.Record;
import com.univocity.parsers.csv.CsvParser;
import com.univocity.parsers.csv.CsvParserSettings;
import io.everytrade.server.model.Currency;
import io.everytrade.server.model.CurrencyPair;
import io.everytrade.server.plugin.api.parser.ParsingProblem;
import io.everytrade.server.plugin.impl.everytrade.parser.exception.DataIgnoredException;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.ExchangeBean;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.IExchangeSpecificParser;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static io.everytrade.server.plugin.api.parser.ParsingProblemType.PARSED_ROW_IGNORED;
import static io.everytrade.server.plugin.api.parser.ParsingProblemType.ROW_PARSING_FAILED;

public class BinanceExchangeSpecificParserV3 implements IExchangeSpecificParser {
    private final String delimiter;
    private List<ParsingProblem> parsingProblems = List.of();
    private static final Set<String> CURRENCY_CODES = new HashSet<>();

    public BinanceExchangeSpecificParserV3(String delimiter) {
        this.delimiter = delimiter;
    }

    static {
        for (Currency currency : Currency.values()) {
            CURRENCY_CODES.add(currency.name());
        }
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
                String[] values = record.getValues();
                values = correctLinesWithCommaBetweenQuotes(values);
                BinanceBeanV3 binanceBean = parseExchangeBean(values);
                if (binanceBean != null) {
                    binanceBeans.add(binanceBean);
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return binanceBeans;
    }

    /**
     * e.g. "2020-05-29 11:13:27,ADABTC,BUY,0.0000067200,""3,813.0000000000ADA"",0.02562336BTC,3.8130000000ADA"
     * @param values
     * @return
     */
    private static String[] correctLinesWithCommaBetweenQuotes(String[] values) {
        try {
            if (values.length > 7) {
                String[] newValues = new String[7];
                int j = 0;
                for (int i = 0; i < values.length; i++) {
                    if (values[i].contains("\"\"")) {
                        newValues[i] = (values[i] + values[i + 1]).replace(",", "").replace("\"", "");
                        i++;
                    } else {
                        newValues[j] = values[i];
                    }
                    j++;
                }
                return newValues;
            } else if (values.length == 1) {
                String text = "";
                var val = values[0];
                var parts = val.split("\"");
                for (int i = 0; i < parts.length; ++i) {
                    if ((i + 1) % 2 == 0) {
                        parts[i] = parts[i].replace(",", "");
                    }
                    text += parts[i];
                }
                values = text.split(",");
            }
        } catch (Exception ignored) {
        }
        return values;
    }

    @Override
    public List<ParsingProblem> getParsingProblems() {
        return parsingProblems;
    }

    private BinanceBeanV3 parseExchangeBean(String[] vals) {
        String row = String.join(",", vals);
        try {
            Currency baseCurrency = extractCurrencyFromEnd(vals[4]);
            Currency quoteCurrency = extractCurrencyFromEnd(vals[5]);
            Currency feeCurrency = extractCurrencyFromEnd(vals[6]);
            if (baseCurrency == null || quoteCurrency == null) {
                throw new DataValidationException("Could not extract base or quote currency from values");
            }
            CurrencyPair currencyPair = new CurrencyPair(baseCurrency, quoteCurrency);

            return new BinanceBeanV3(
                vals[0], // date
                vals[1], // pair
                vals[2], // type
                vals[4], // filled amount with currency
                vals[5], // total amount with currency
                vals[6], // fee
                feeCurrency,
                currencyPair
            );
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

    private Currency extractCurrencyFromEnd(String value) {
        Pattern pattern = Pattern.compile("([A-Z]+)$");
        Matcher matcher = pattern.matcher(value);
        if (matcher.find()) {
            String currencyCode = matcher.group(1);
            if (CURRENCY_CODES.contains(currencyCode)) {
                return Currency.valueOf(currencyCode);
            } else {
                throw new DataValidationException("Unsupported currency code: " + currencyCode);
            }
        } else {
            throw new DataValidationException("Currency code not found in value: " + value);
        }
    }
}

