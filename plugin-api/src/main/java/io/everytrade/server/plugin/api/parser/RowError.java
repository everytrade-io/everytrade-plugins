package io.everytrade.server.plugin.api.parser;

public class RowError {
    private final String row;
    private final String message;
    private final RowErrorType type;

    public RowError(String row, String message, RowErrorType type) {
        this.row = row;
        this.message = message;
        this.type = type;
    }

    public String getRow() {
        return row;
    }

    public String getMessage() {
        return message;
    }

    public RowErrorType getType() {
        return type;
    }

    @Override
    public String toString() {
        return "RowError{" +
            "row='" + row + '\'' +
            ", message='" + message + '\'' +
            ", type=" + type +
            '}';
    }
}
