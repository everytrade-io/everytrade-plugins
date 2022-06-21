package io.everytrade.server.plugin.api.connector;

import lombok.NonNull;
import lombok.Value;

@Value
public class ConnectorParameterDescriptor {

    @NonNull
    String id;

    @NonNull
    ConnectorParameterType type;

    @NonNull
    String label;

    @NonNull
    String description;

    boolean isSelectedByDefault;
}
