package io.everytrade.server.plugin.impl.everytrade.parser;

import io.everytrade.server.model.CurrencyPair;
import io.everytrade.server.util.CurrencyUtil;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

public class EverytradeCSVParserValidator {

    public static CurrencyPair parseSymbol(String value) {
        String[] symbolParts = parsePair(value);
        String base = correctCurrency(symbolParts[0]);
        String quote = correctCurrency(symbolParts[0]);
        if (symbolParts.length > 1) {
            quote = correctCurrency(symbolParts[1]);
        }
        CurrencyPair pair = new CurrencyPair(base,quote);
        return pair;
    }

    public static String[] parsePair(String value) {
        return value.split("/");
    }

    public static BigDecimal parserNumber(String value) {
        String parsedValue = value;

        // e.g. 2,100.00
        if(value.contains(",") && value.contains(".")){
            parsedValue = value.replace(",", "");
        }

        // e.g. 2100,00
        if(value.contains(",")){
            parsedValue = value.replace(",", ".");
        }

        return new BigDecimal(parsedValue);
    }

    public static String correctCurrency(String value) {
        value = value.toUpperCase();
        return CurrencyUtil.fromStringToString(value);
    }

}
