package io.everytrade.server.plugin.impl.everytrade;

import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.currency.CurrencyPair;

public class ConnectorUtils {

    public static CurrencyPair createPair(String pair) {
        final String[] split = pair.split("/");
        if (split.length != 2) {
            throw new IllegalArgumentException(String.format("Illegal pair value '%s'.", pair));
        }
        final Currency base = Currency.getInstanceNoCreate(split[0]);
        final Currency quote = Currency.getInstanceNoCreate(split[1]);
        if (base == null) {
            throw new IllegalArgumentException(String.format("Illegal base value '%s'.", split[0]));
        }
        if (quote == null) {
            throw new IllegalArgumentException(String.format("Illegal quote value '%s'.", split[1]));
        }
        return new CurrencyPair(base, quote);
    }
}
