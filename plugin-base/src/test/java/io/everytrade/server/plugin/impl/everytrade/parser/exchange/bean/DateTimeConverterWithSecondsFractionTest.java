package io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DateTimeConverterWithSecondsFractionTest {

    @Test
    void testCorrectConversionNoFraction() {
        testConversion("2020-01-03, 15:20:30");

    }

    @Test
    void testCorrectConversionFraction() {
        testConversion("2020-01-03, 15:20:30.4968");

    }

    private void testConversion(String input) {
        DateTimeConverterWithSecondsFraction converter
            = new DateTimeConverterWithSecondsFraction("yyyy-MM-dd, HH:mm:ss");

        assertEquals(input, converter.revert(converter.execute(input)));
    }
}