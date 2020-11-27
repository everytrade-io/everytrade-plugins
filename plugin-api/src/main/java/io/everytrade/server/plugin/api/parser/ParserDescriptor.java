package io.everytrade.server.plugin.api.parser;

import java.util.List;
import java.util.Objects;

public class ParserDescriptor {
    private final String id;
    private final List<String> exchangeSignatures;

    public ParserDescriptor(String id, List<String> exchangeSignatures) {
        Objects.requireNonNull(this.id = id);
        Objects.requireNonNull(exchangeSignatures);
        this.exchangeSignatures = List.copyOf(exchangeSignatures);
    }

    public String getId() {
        return id;
    }

    public List<String> getExchangeSignatures() {
        return exchangeSignatures;
    }

    @Override
    public String toString() {
        return "ParserDescriptor{" +
            "id='" + id + '\'' +
            ", exchangeSignatures=" + exchangeSignatures +
            '}';
    }
}
