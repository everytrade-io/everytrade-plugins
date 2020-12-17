package io.everytrade.server.plugin.api.rateprovider;

import io.everytrade.server.model.Currency;

import java.util.List;
import java.util.Objects;

public class RateProviderDescriptor {
    private final String id;
    private final List<Currency> currencies;

    public RateProviderDescriptor(String id, List<Currency> currencies) {
        Objects.requireNonNull(this.id = id);
        this.currencies = List.copyOf(currencies);
    }

    public String getId() {
        return id;
    }

    public List<Currency> getCurrencies() {
        return currencies;
    }

    @Override
    public String toString() {
        return "RateProviderDescriptor{" +
            "id='" + id + '\'' +
            ", currencies=" + currencies +
            '}';
    }
}
