package io.everytrade.server.plugin.api.connector;

import java.util.List;
import java.util.Objects;

public class ConnectorDescriptor {
    private final String id;
    private final String name;
    private final String note;
    private final String exchangeId;
    private final List<ConnectorParameterDescriptor> parameters;

    public ConnectorDescriptor(
        String id,
        String name,
        String note,
        String exchangeId,
        List<ConnectorParameterDescriptor> parameters
    ) {
        Objects.requireNonNull(this.id = id);
        Objects.requireNonNull(this.name = name);
        Objects.requireNonNull(this.note = note);
        Objects.requireNonNull(this.exchangeId = exchangeId);
        this.parameters = List.copyOf(Objects.requireNonNull(parameters));
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getNote() {
        return note;
    }

    public String getExchangeId() {
        return exchangeId;
    }

    public List<ConnectorParameterDescriptor> getParameters() {
        return parameters;
    }

    @Override
    public String toString() {
        return "ConnectorDescriptor{" +
            "id='" + id + '\'' +
            ", name='" + name + '\'' +
            ", note='" + note + '\'' +
            ", exchangeId='" + exchangeId + '\'' +
            ", parameters=" + parameters +
            '}';
    }
}
