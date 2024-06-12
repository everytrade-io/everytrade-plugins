package io.everytrade.server.plugin.impl.everytrade.parser.exception;

import com.univocity.parsers.common.DataProcessingException;

public class ParserErrorCurrencyException extends DataProcessingException {
    public ParserErrorCurrencyException(String message) {
        super(message);
    }
}
