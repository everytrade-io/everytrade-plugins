package io.everytrade.server.plugin.impl.everytrade.parser;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public class ParserUtils {
    public static final int DECIMAL_DIGITS = 10;

    private ParserUtils() {
    }

    public static Instant parse(String dateTimePattern, String dateTime) {
        final DateTimeFormatter dateTimeFormatter =
            DateTimeFormatter.ofPattern(dateTimePattern, Locale.US).withZone(ZoneOffset.UTC);

        return dateTimeFormatter.parse(dateTime, Instant::from);
    }
}
