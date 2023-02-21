package io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v4;

import io.everytrade.server.model.TransactionType;

import java.util.List;

import static io.everytrade.server.model.TransactionType.BUY;
import static io.everytrade.server.model.TransactionType.DEPOSIT;
import static io.everytrade.server.model.TransactionType.EARNING;
import static io.everytrade.server.model.TransactionType.FEE;
import static io.everytrade.server.model.TransactionType.REBATE;
import static io.everytrade.server.model.TransactionType.REWARD;
import static io.everytrade.server.model.TransactionType.SELL;
import static io.everytrade.server.model.TransactionType.UNKNOWN;
import static io.everytrade.server.model.TransactionType.WITHDRAWAL;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v4.BinanceOperationTypeV4.OPERATION_TYPE_BUY;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v4.BinanceOperationTypeV4.OPERATION_TYPE_CARD_CASHBACK;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v4.BinanceOperationTypeV4.OPERATION_TYPE_COMMISSION_REBATE;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v4.BinanceOperationTypeV4.OPERATION_TYPE_DEPOSIT;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v4.BinanceOperationTypeV4.OPERATION_TYPE_DISTRIBUTION;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v4.BinanceOperationTypeV4.OPERATION_TYPE_FEE;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v4.BinanceOperationTypeV4.OPERATION_TYPE_FIAT_DEPOSIT;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v4.BinanceOperationTypeV4.OPERATION_TYPE_FIAT_WITHDRAWAL;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v4.BinanceOperationTypeV4.OPERATION_TYPE_LARGE_OTC_TRADING;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v4.BinanceOperationTypeV4.OPERATION_TYPE_SELL;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v4.BinanceOperationTypeV4.OPERATION_TYPE_SIMPLE_EARN_FLEXIBLE_INTEREST;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v4.BinanceOperationTypeV4.OPERATION_TYPE_SMALL_ASSETS_EXCHANGE_BNB;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v4.BinanceOperationTypeV4.OPERATION_TYPE_TRANSACTION_RELATED;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v4.BinanceOperationTypeV4.OPERATION_TYPE_WITHDRAWAL;

public class BinanceSwitcher {

    public static TransactionType operationTypeSwitcher(String operationType) {
        operationType = operationType.toUpperCase();
        if (OPERATION_TYPE_BUY.code.equals(operationType)) {
            return BUY;
        }
        if (OPERATION_TYPE_SELL.code.equals(operationType)) {
            return SELL;
        }
        if (List.of(OPERATION_TYPE_DEPOSIT.code, OPERATION_TYPE_FIAT_DEPOSIT.code).contains(operationType)) {
            return DEPOSIT;
        }
        if (List.of(OPERATION_TYPE_WITHDRAWAL.code,OPERATION_TYPE_FIAT_WITHDRAWAL.code).contains(operationType)) {
            return WITHDRAWAL;
        }
        if (OPERATION_TYPE_FEE.code.equals(operationType)) {
            return FEE;
        }
        if (OPERATION_TYPE_TRANSACTION_RELATED.code.equals(operationType) || OPERATION_TYPE_LARGE_OTC_TRADING.code.equals(operationType)
            || OPERATION_TYPE_SMALL_ASSETS_EXCHANGE_BNB.code.equals(operationType)) {
            return BUY;
        }
        if (OPERATION_TYPE_DISTRIBUTION.code.equals(operationType)) {
            return REWARD;
        }
        if (OPERATION_TYPE_COMMISSION_REBATE.code.equals(operationType) || OPERATION_TYPE_CARD_CASHBACK.code.equals(operationType)) {
            return REBATE;
        }
        if (OPERATION_TYPE_SIMPLE_EARN_FLEXIBLE_INTEREST.code.equals(operationType)) {
            return EARNING;
        }
        return UNKNOWN;
    }

}
