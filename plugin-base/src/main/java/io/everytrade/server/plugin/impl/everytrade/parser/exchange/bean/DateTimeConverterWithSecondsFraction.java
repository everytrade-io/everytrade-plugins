package io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean;

import com.univocity.parsers.common.DataValidationException;
import com.univocity.parsers.conversions.Conversion;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class DateTimeConverterWithSecondsFraction implements Conversion<String, Instant> {

    private final List<DateTimeFormatter> dateTimeFormatters = new ArrayList<>();

    public DateTimeConverterWithSecondsFraction(String... patterns) {
        Objects.requireNonNull(patterns);
        for (String pattern : patterns) {
            if (pattern.endsWith("s")) {
                dateTimeFormatters.add(
                    new DateTimeFormatterBuilder()
                        .append(DateTimeFormatter.ofPattern(pattern))
                        .appendFraction(ChronoField.NANO_OF_SECOND, 0, 4, true)
                        .toFormatter(Locale.US)
                );
            } else {
                dateTimeFormatters.add(
                    new DateTimeFormatterBuilder()
                        .append(DateTimeFormatter.ofPattern(pattern))
                        .toFormatter(Locale.US)
                );
            }
        }

    }

    @Override
    public Instant execute(String input) {
        Instant result = null;
        int errorCounter = 0;
        for (DateTimeFormatter dateTimeFormatter : dateTimeFormatters) {
            try {
                LocalDateTime localDateTime = LocalDateTime.parse(input, dateTimeFormatter);
                result = localDateTime.toInstant(ZoneOffset.UTC);
            } catch (Exception e) {
                errorCounter++;
            }
        }
        if (errorCounter + 1 != dateTimeFormatters.size()) {
            throw new DataValidationException(String.format(
                "Exactly one datetime pattern has to match for value '%s'. Found %d patterns.",
                input,
                dateTimeFormatters.size() - errorCounter
            ));
        }
        return result;
    }

    @Override
    public String revert(Instant input) {
        LocalDateTime localDateTime = LocalDateTime.ofInstant(input, ZoneOffset.UTC);

        String result = null;
        int errorCounter = 0;
        for (DateTimeFormatter dateTimeFormatter : dateTimeFormatters) {
            try {
                result = dateTimeFormatter.format(localDateTime);
            } catch (Exception e) {
                errorCounter++;
            }
        }
        if (errorCounter + 1 != dateTimeFormatters.size()) {
            throw new DataValidationException(String.format(
                "Exactly one datetime pattern has to match for value '%s'. Found %d patterns.",
                input,
                dateTimeFormatters.size() - errorCounter
            ));
        }
        return result;
    }
}
