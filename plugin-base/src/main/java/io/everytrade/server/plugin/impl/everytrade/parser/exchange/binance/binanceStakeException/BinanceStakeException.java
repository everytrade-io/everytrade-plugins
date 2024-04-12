package io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.binanceStakeException;

import com.univocity.parsers.common.DataValidationException;

public class BinanceStakeException extends DataValidationException {

        public BinanceStakeException(String message) {
            super(message);
        }
}
