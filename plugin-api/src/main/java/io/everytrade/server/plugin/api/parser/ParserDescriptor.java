package io.everytrade.server.plugin.api.parser;

import io.everytrade.server.model.SupportedExchange;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class ParserDescriptor {
    private final String id;
    private final Map<String, SupportedExchange> exchangeHeaders;

    public ParserDescriptor(String id, Map<String, SupportedExchange> exchangeHeaders) {
        Objects.requireNonNull(this.id = id);
        Objects.requireNonNull(exchangeHeaders);
        this.exchangeHeaders = Map.copyOf(exchangeHeaders);
    }

    public String getId() {
        return id;
    }

    public List<String> getExchangeHeaders() {
        return new ArrayList<>(exchangeHeaders.keySet());
    }

    public SupportedExchange getSupportedExchange(String header) {
        return exchangeHeaders.get(header);
    }

    @Override
    public String toString() {
        return "ParserDescriptor{" +
            "id='" + id + '\'' +
            ", exchangeHeaders=" + exchangeHeaders +
            '}';
    }
}
