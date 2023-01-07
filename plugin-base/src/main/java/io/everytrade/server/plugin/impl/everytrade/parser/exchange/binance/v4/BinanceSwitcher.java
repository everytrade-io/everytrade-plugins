package io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v4;

import io.everytrade.server.model.TransactionType;

import java.util.List;

import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v4.BinanceConstantsV4.OPERATION_TYPE_BUY;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v4.BinanceConstantsV4.OPERATION_TYPE_DEPOSIT;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v4.BinanceConstantsV4.OPERATION_TYPE_DISTRIBUTION;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v4.BinanceConstantsV4.OPERATION_TYPE_FEE;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v4.BinanceConstantsV4.OPERATION_TYPE_FIAT_DEPOSIT;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v4.BinanceConstantsV4.OPERATION_TYPE_FIAT_WITHDRAWAL;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v4.BinanceConstantsV4.OPERATION_TYPE_SELL;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v4.BinanceConstantsV4.OPERATION_TYPE_TRANSACTION_RELATED;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v4.BinanceConstantsV4.OPERATION_TYPE_WITHDRAWAL;

public class BinanceSwitcher {

    public static TransactionType operationTypeSwitcher(String operationType) {
        if (OPERATION_TYPE_BUY.code.equals(operationType)) {
            return TransactionType.BUY;
        }
        if (OPERATION_TYPE_SELL.code.equals(operationType)) {
            return TransactionType.SELL;
        }
        if (List.of(OPERATION_TYPE_DEPOSIT.code, OPERATION_TYPE_FIAT_DEPOSIT.code).contains(operationType)) {
            return TransactionType.DEPOSIT;
        }
        if (List.of(OPERATION_TYPE_WITHDRAWAL.code,OPERATION_TYPE_FIAT_WITHDRAWAL.code).contains(operationType)) {
            return TransactionType.WITHDRAWAL;
        }
        if (OPERATION_TYPE_FEE.code.equals(operationType)) {
            return TransactionType.FEE;
        }
        if (OPERATION_TYPE_TRANSACTION_RELATED.code.equals(operationType)) {
            return TransactionType.BUY;
        }
        if (OPERATION_TYPE_DISTRIBUTION.code.equals(operationType)) {
            return TransactionType.REWARD;
        }
        return TransactionType.UNKNOWN;
    }

}
