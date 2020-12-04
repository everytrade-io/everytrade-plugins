package io.everytrade.server.plugin.impl.everytrade.parser.exception;

import com.univocity.parsers.common.DataValidationException;

public class DataIgnoredException extends DataValidationException {

    public DataIgnoredException(String message) {
        super(message);
    }
}
