package io.everytrade.server.plugin.api.rateprovider;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoField;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

public enum RateValidity {
    //keep ordered so that Enum#compareTo is consistent with expected ordering (Duration#compareTo).
    QUARTER_HOUR(ChronoField.MINUTE_OF_HOUR, 15),
    DAY(ChronoField.DAY_OF_MONTH, 1),
    FOREVER(ChronoField.INSTANT_SECONDS, Instant.MIN.until(Instant.MAX, ChronoUnit.SECONDS));

    private final ChronoField field;
    private final long count;
    private final Duration duration;

    RateValidity(ChronoField field, long count) {
        Objects.requireNonNull(this.field = field);
        this.count = count;
        if (!field.range().isValidValue(this.count)) {
            throw new IllegalArgumentException(
                String.format("Count %d is not in range for field '%s'.", this.count, field)
            );
        }
        this.duration = Duration.of(this.count, this.field.getBaseUnit());
    }

    public ChronoField getField() {
        return field;
    }

    public long getCount() {
        return count;
    }

    public Duration getDuration() {
        return duration;
    }

    public static RateValidity min(RateValidity r1, RateValidity r2) {
        return r1.getDuration().compareTo(r2.getDuration()) < 0 ? r1 : r2;
    }
}
