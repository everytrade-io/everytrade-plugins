package io.everytrade.server.plugin.api.parser.exception;

public class UnknownHeaderException extends ParsingProcessException {
    public UnknownHeaderException() {
        super();
    }
    public UnknownHeaderException(String message) {
        super(message);
    }
}
