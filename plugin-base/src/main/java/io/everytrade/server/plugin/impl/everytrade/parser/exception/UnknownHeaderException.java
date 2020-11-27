package io.everytrade.server.plugin.impl.everytrade.parser.exception;

public class UnknownHeaderException extends ParsingProcessException {
    public UnknownHeaderException() {
        super();
    }
    public UnknownHeaderException(String message) {
        super(message);
    }
}
