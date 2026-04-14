package io.everytrade.server.plugin.impl.everytrade.parser.exchange.coinbank;

import io.everytrade.server.model.Currency;

import java.util.HashMap;
import java.util.Map;

import static io.everytrade.server.model.Currency.DASH;
import static io.everytrade.server.model.Currency.IOTA;

public class CoinbankCurrencySwitcher {
    public static final Map<String, Currency> SWITCHER = new HashMap<>();

    static {
        SWITCHER.put("DSH", DASH);
        SWITCHER.put("IOTOLD", IOTA);
    }
}
