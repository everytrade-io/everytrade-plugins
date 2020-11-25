package io.everytrade.server.plugin.api.parser;

import com.univocity.parsers.conversions.Conversion;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.Locale;
import java.util.Objects;

public class DateTimeConverterWithSecondsFraction implements Conversion<String, Instant> {

    private final DateTimeFormatter dateTimeFormatter;

    public DateTimeConverterWithSecondsFraction(String... patterns) {
        Objects.requireNonNull(patterns);
        if (patterns.length != 1) {
            throw new IllegalArgumentException("One pattern must be set.");
        }
        if (!patterns[0].endsWith("s")) {
            throw new IllegalArgumentException("Pattern must end with seconds (i.e. s or ss)");
        }
        dateTimeFormatter = new DateTimeFormatterBuilder()
            .append(DateTimeFormatter.ofPattern(patterns[0]))
            .appendFraction(ChronoField.NANO_OF_SECOND, 0, 4, true)
            .toFormatter(Locale.US);
    }

    @Override
    public Instant execute(String input) {
        LocalDateTime localDateTime = LocalDateTime.parse(input, dateTimeFormatter);
        return localDateTime.toInstant(ZoneOffset.UTC);
    }

    @Override
    public String revert(Instant input) {
        LocalDateTime localDateTime = LocalDateTime.ofInstant(input, ZoneOffset.UTC);
        return dateTimeFormatter.format(localDateTime);
    }
}
