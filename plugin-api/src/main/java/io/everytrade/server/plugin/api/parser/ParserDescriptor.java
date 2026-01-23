package io.everytrade.server.plugin.api.parser;

import io.everytrade.server.model.SupportedExchange;
import io.everytrade.server.plugin.csv.CsvHeader;

import java.util.Map;
import java.util.Objects;

public class ParserDescriptor {
    private final String id;
    private final Map<CsvHeader, SupportedExchange> exchangeHeaderTemplates;

    public ParserDescriptor(String id, Map<CsvHeader, SupportedExchange> exchangeHeaderTemplates) {
        Objects.requireNonNull(this.id = id);
        Objects.requireNonNull(exchangeHeaderTemplates);
        this.exchangeHeaderTemplates = Map.copyOf(exchangeHeaderTemplates);
    }

    public String getId() {
        return id;
    }

    public boolean isHeaderSupported(String header) {
        final CsvHeader headerTemplate = findHeaderTemplate(header);
        return headerTemplate != null;
    }

    public SupportedExchange getSupportedExchange(String header) {
        final CsvHeader headerTemplate = findHeaderTemplate(header);
        if (headerTemplate == null) {
            throw new IllegalStateException(String.format("Header '%s' is not supported.", header));
        }
        return exchangeHeaderTemplates.get(headerTemplate);
    }

    public CsvHeader findHeaderTemplate(String header) {

        CsvHeader bestMatch = null;
        int bestColumnCount = -1;

        for (CsvHeader template : exchangeHeaderTemplates.keySet()) {

            if (!template.matching(header)) {
                continue;
            }

            int columnCount = template.getHeaderValues().size();

            if (columnCount > bestColumnCount) {
                bestMatch = template;
                bestColumnCount = columnCount;
            }
        }

        return bestMatch;
    }

    @Override
    public String toString() {
        return "ParserDescriptor{" +
            "id='" + id + '\'' +
            ", exchangeHeaders=" + exchangeHeaderTemplates +
            '}';
    }
}
