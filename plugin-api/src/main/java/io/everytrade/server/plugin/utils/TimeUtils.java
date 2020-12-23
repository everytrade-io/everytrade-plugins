package io.everytrade.server.plugin.utils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoField;

public class TimeUtils {
    public static Instant truncate(Instant instant, ChronoField field, long count) {
        final LocalDateTime truncatedLocalDateTime =
            LocalDateTime.ofInstant(instant, ZoneOffset.UTC).truncatedTo(field.getBaseUnit());
        final int oldFieldValue = truncatedLocalDateTime.get(field);
        long newFieldValue = (oldFieldValue / count) * count;
        return truncatedLocalDateTime.with(field, newFieldValue).toInstant(ZoneOffset.UTC);
    }
}
