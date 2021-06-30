package io.everytrade.server.model;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CurrencyTest {

    @Test
    public void testCodesOfCurrency() {
        Arrays.stream(Currency.values()).forEach(currency -> {
            String code = currency.code();
            assertEquals(currency, Currency.fromCode(code));
        });
    }

    @Test
    public void test1INCH() {
        assertEquals(Currency._1INCH, Currency.fromCode("1INCH"));
        assertEquals("1INCH", Currency._1INCH.code());
    }
}
