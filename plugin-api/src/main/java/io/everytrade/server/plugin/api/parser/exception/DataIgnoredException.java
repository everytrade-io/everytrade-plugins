package io.everytrade.server.plugin.api.parser.exception;

import com.univocity.parsers.common.DataValidationException;

public class DataIgnoredException extends DataValidationException {

    public DataIgnoredException(String message) {
        super(message);
    }
}
