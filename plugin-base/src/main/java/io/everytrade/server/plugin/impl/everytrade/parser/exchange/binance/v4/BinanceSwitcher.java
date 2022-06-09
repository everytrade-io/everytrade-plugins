package io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v4;

import io.everytrade.server.model.TransactionType;

public class BinanceSwitcher {

    public static TransactionType operationTypeSwitcher(String operationType) {
        if (BinanceConstantsV4.OPERATION_TYPE_BUY.code.equals(operationType)) {
            return TransactionType.BUY;
        }
        if (BinanceConstantsV4.OPERATION_TYPE_SELL.code.equals(operationType)) {
            return TransactionType.SELL;
        }
        if (BinanceConstantsV4.OPERATION_TYPE_DEPOSIT.code.equals(operationType)) {
            return TransactionType.DEPOSIT;
        }
        if (BinanceConstantsV4.OPERATION_TYPE_WITHDRAWAL.code.equals(operationType)) {
            return TransactionType.WITHDRAWAL;
        }
        if (BinanceConstantsV4.OPERATION_TYPE_FEE.code.equals(operationType)) {
            return TransactionType.FEE;
        }

        if (BinanceConstantsV4.OPERATION_TYPE_TRANSACTION_RELATED.code.equals(operationType)) {
            return TransactionType.BUY;
        }
        return TransactionType.UNKNOWN;
    }

}
