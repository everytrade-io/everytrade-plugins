package io.everytrade.server.model;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static java.util.stream.Collectors.toSet;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CurrencyPairTest {

    @Test
    public void testAllFiatsArePresentInTradeablePairs() {
        var allFiats = Arrays.stream(Currency.values()).filter(Currency::isFiat).collect(toSet());
        var allSupported = CurrencyPair.getTradeablePairs().stream().map(CurrencyPair::getBase).collect(toSet());

        allFiats.forEach(f -> {
            assertTrue(allSupported.contains(f), f.code() + " must be present in supported fiats.");
        });
    }

    @Test
    public void testAllCryptosArePresentInTradeablePairs() {
        var allFiats = Arrays.stream(Currency.values()).filter(it -> !it.isFiat()).collect(toSet());
        var allSupported = CurrencyPair.getTradeablePairs().stream().map(CurrencyPair::getBase).collect(toSet());

        allFiats.forEach(f -> {
            assertTrue(allSupported.contains(f), f.code() + " must be present in supported fiats.");
        });
    }
}
