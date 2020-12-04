package io.everytrade.server.plugin.impl.everytrade.parser.exception;

public class ParsingProcessException extends RuntimeException {
    public ParsingProcessException() {
        super();
    }

    public ParsingProcessException(Throwable cause) {
        super(cause);
    }

    public ParsingProcessException(String message) {
        super(message);
    }
}
