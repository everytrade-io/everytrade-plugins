package io.everytrade.server.plugin.api.rateprovider;

import io.everytrade.server.model.Currency;

import java.util.List;
import java.util.Objects;

public class RateProviderDescriptor {
    private final String id;
    private final List<Currency> currencies;
    private final int priority; // unix process-style priority (i.e. lower numerical value means higher priority)

    public static final int HIGH_PRIORITY = 0;
    public static final int NORMAL_PRIORITY = 5_000;
    public static final int LOW_PRIORITY = 10_000;

    public RateProviderDescriptor(String id, List<Currency> currencies, int priority) {
        Objects.requireNonNull(this.id = id);
        this.currencies = List.copyOf(currencies);
        this.priority = priority;
    }

    public String getId() {
        return id;
    }

    public List<Currency> getCurrencies() {
        return currencies;
    }

    public int getPriority() {
        return priority;
    }

    @Override
    public String toString() {
        return "RateProviderDescriptor{" +
            "id='" + id + '\'' +
            ", currencies=" + currencies +
            '}';
    }
}
