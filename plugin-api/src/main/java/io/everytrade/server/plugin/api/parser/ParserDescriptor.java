package io.everytrade.server.plugin.api.parser;

import io.everytrade.server.model.SupportedExchange;

import java.util.Map;
import java.util.Objects;

import static io.everytrade.server.plugin.utils.HeaderTemplateFinder.findHeaderTemplate;

public class ParserDescriptor {
    private final String id;
    private final Map<String, SupportedExchange> exchangeHeaderTemplates;

    public ParserDescriptor(String id, Map<String, SupportedExchange> exchangeHeaderTemplates) {
        Objects.requireNonNull(this.id = id);
        Objects.requireNonNull(exchangeHeaderTemplates);
        this.exchangeHeaderTemplates = Map.copyOf(exchangeHeaderTemplates);
    }

    public String getId() {
        return id;
    }

    public boolean isHeaderSupported(String header) {
        final String headerTemplate = findHeaderTemplate(header, exchangeHeaderTemplates.keySet());
        return  headerTemplate != null;
    }

    public SupportedExchange getSupportedExchange(String header) {
        final String headerTemplate = findHeaderTemplate(header, exchangeHeaderTemplates.keySet());
        if (headerTemplate == null) {
            throw new IllegalStateException(String.format("Header '%s' is not supported.", header));
        }
        return exchangeHeaderTemplates.get(headerTemplate);
    }

    @Override
    public String toString() {
        return "ParserDescriptor{" +
            "id='" + id + '\'' +
            ", exchangeHeaders=" + exchangeHeaderTemplates +
            '}';
    }
}
