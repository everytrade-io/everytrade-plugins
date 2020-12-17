package io.everytrade.server.plugin.api.rateprovider;

import java.util.Objects;

public enum RateSourceType {
    OFFICIAL, MARKET, USER, MISSING, MIXED, FACT;

    public RateSourceType combine(RateSourceType other) {
        Objects.requireNonNull(other);
        if (other == FACT || this == other) {
            return this;
        }
        return this == FACT ? other : MIXED;
    }
}
