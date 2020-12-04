package io.everytrade.server.plugin.api.parser;

import java.util.List;
import java.util.Objects;

public class ParserDescriptor {
    private final String id;
    private final List<String> exchangeHeaders;

    public ParserDescriptor(String id, List<String> exchangeHeaders) {
        Objects.requireNonNull(this.id = id);
        Objects.requireNonNull(exchangeHeaders);
        this.exchangeHeaders = List.copyOf(exchangeHeaders);
    }

    public String getId() {
        return id;
    }

    public List<String> getExchangeHeaders() {
        return exchangeHeaders;
    }

    @Override
    public String toString() {
        return "ParserDescriptor{" +
            "id='" + id + '\'' +
            ", exchangeHeaders=" + exchangeHeaders +
            '}';
    }
}
