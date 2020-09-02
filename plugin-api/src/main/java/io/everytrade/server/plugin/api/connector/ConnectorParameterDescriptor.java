package io.everytrade.server.plugin.api.connector;

import java.util.Objects;

public class ConnectorParameterDescriptor {
    private final String id;
    private final ConnectorParameterType type;
    private final String label;
    private final String description;

    public ConnectorParameterDescriptor(String id, ConnectorParameterType type, String label, String description) {
        Objects.requireNonNull(this.id = id);
        Objects.requireNonNull(this.type = type);
        Objects.requireNonNull(this.label = label);
        Objects.requireNonNull(this.description = description);
    }

    public String getId() {
        return id;
    }

    public ConnectorParameterType getType() {
        return type;
    }

    public String getLabel() {
        return label;
    }

    public String getDescription() {
        return description;
    }

    @Override
    public String toString() {
        return "ConnectorParameterDescriptor{" +
            "id='" + id + '\'' +
            ", type=" + type +
            ", label='" + label + '\'' +
            ", description='" + description + '\'' +
            '}';
    }
}
