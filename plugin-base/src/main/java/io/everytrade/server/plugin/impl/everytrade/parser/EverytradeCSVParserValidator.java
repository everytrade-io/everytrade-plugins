package io.everytrade.server.plugin.impl.everytrade.parser;

import io.everytrade.server.model.CurrencyPair;
import io.everytrade.server.util.CurrencyUtil;

import java.math.BigDecimal;

public class EverytradeCSVParserValidator {

    public static CurrencyPair parseSymbol(String value) {
        String[] symbolParts = parsePair(value);
        String base = correctCurrency(symbolParts[0]);
        String quote;
        if (symbolParts.length > 1) {
            quote = correctCurrency(symbolParts[1]);
        } else {
            quote = base;
        }
        return new CurrencyPair(base,quote);
    }

    public static String[] parsePair(String value) {
        return value.split("/");
    }

    public static BigDecimal parserNumber(String value) {
        if(value == null) {
            return null;
        }
        // e.g. 2,100.00
        if (value.contains(",") && value.contains(".")) {
            value = value.replace(",", "");
        }
        // e.g. 2100,00
        if (value.contains(",")) {
            value = value.replace(",", ".");
        }
        if("0".equals(value)) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(value);
    }

    public static String correctCurrency(String value) {
        return CurrencyUtil.fromStringToString(value.toUpperCase());
    }
}
