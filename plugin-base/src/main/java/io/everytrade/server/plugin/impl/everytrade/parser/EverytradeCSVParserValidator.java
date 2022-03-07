package io.everytrade.server.plugin.impl.everytrade.parser;

import io.everytrade.server.plugin.api.parser.TransactionCluster;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.ExchangeBean;
import io.everytrade.server.util.CurrencyUtil;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class EverytradeCSVParserValidator {

    public static Map<String,String> parsedSymbol(String value) {
        Map<String,String> symbols = new HashMap<>();
        String[] symbolParts = symbolPairParser(value);
        String base = correctCurrency(symbolParts[0].toUpperCase());
        symbols.put("symbolBase", base);
        if (symbolParts.length > 1) {
            symbols.put("symbolQuote", correctCurrency(symbolParts[1].toUpperCase()));
        } else {
            symbols.put("symbolQuote", base);
        }
        return symbols;
    }

    public static String[] symbolPairParser(String value) {
        return value.split("/");
    }

    public static BigDecimal numberParser(String value) {
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
        return value.toUpperCase();
    }

}
