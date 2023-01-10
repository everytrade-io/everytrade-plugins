package io.everytrade.server.plugin.impl.everytrade.parser.exception;

import com.univocity.parsers.common.DataValidationException;

public class DataStatusException extends DataValidationException {

    public DataStatusException(String message) {
        super(message);
    }
}
