package io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.binanceExceptions;

import com.univocity.parsers.common.DataValidationException;

public class BinanceValidateException extends DataValidationException {

        public BinanceValidateException(String message) {
            super(message);
        }
}
