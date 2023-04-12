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
import static io.everytrade.server.model.TransactionType.STAKE;
import static io.everytrade.server.model.TransactionType.STAKING_REWARD;
import static io.everytrade.server.model.TransactionType.UNKNOWN;
import static io.everytrade.server.model.TransactionType.UNSTAKE;
import static io.everytrade.server.model.TransactionType.WITHDRAWAL;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v4.BinanceOperationTypeV4.OPERATION_TYPE_BINANCE_CONVERT;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v4.BinanceOperationTypeV4.OPERATION_TYPE_BNB_VAULT_REWARDS;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v4.BinanceOperationTypeV4.OPERATION_TYPE_BUY;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v4.BinanceOperationTypeV4.OPERATION_TYPE_BUY_CRYPTO;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v4.BinanceOperationTypeV4.OPERATION_TYPE_C2C_TRANSFER;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v4.BinanceOperationTypeV4.OPERATION_TYPE_CARD_CASHBACK;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v4.BinanceOperationTypeV4.OPERATION_TYPE_CASHBACK_VOUCHER;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v4.BinanceOperationTypeV4.OPERATION_TYPE_COMMISSION_REBATE;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v4.BinanceOperationTypeV4.OPERATION_TYPE_DEPOSIT;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v4.BinanceOperationTypeV4.OPERATION_TYPE_DISTRIBUTION;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v4.BinanceOperationTypeV4.OPERATION_TYPE_FEE;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v4.BinanceOperationTypeV4.OPERATION_TYPE_FIAT_DEPOSIT;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v4.BinanceOperationTypeV4.OPERATION_TYPE_FIAT_WITHDRAW;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v4.BinanceOperationTypeV4.OPERATION_TYPE_FIAT_WITHDRAWAL;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v4.BinanceOperationTypeV4.OPERATION_TYPE_LARGE_OTC_TRADING;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v4.BinanceOperationTypeV4.OPERATION_TYPE_SAVING_DISTRIBUTION;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v4.BinanceOperationTypeV4.OPERATION_TYPE_SELL;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v4.BinanceOperationTypeV4.OPERATION_TYPE_SIMPLE_EARN_FLEXIBLE_INTEREST;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v4.BinanceOperationTypeV4.OPERATION_TYPE_SIMPLE_EARN_FLEXIBLE_REDEMPTION;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v4.BinanceOperationTypeV4.OPERATION_TYPE_SIMPLE_EARN_FLEXIBLE_SUBSCRIPTION;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v4.BinanceOperationTypeV4.OPERATION_TYPE_SIMPLE_EARN_LOCKED_REDEMPTION;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v4.BinanceOperationTypeV4.OPERATION_TYPE_SIMPLE_EARN_LOCKED_REWARDS;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v4.BinanceOperationTypeV4.OPERATION_TYPE_SMALL_ASSETS_EXCHANGE_BNB;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v4.BinanceOperationTypeV4.OPERATION_TYPE_STAKING_PURCHASE;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v4.BinanceOperationTypeV4.OPERATION_TYPE_STAKING_REDEMPTION;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v4.BinanceOperationTypeV4.OPERATION_TYPE_STAKING_REWARDS;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v4.BinanceOperationTypeV4.OPERATION_TYPE_TRANSACTION_BUY;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v4.BinanceOperationTypeV4.OPERATION_TYPE_TRANSACTION_RELATED;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v4.BinanceOperationTypeV4.OPERATION_TYPE_TRANSACTION_REVENUE;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v4.BinanceOperationTypeV4.OPERATION_TYPE_TRANSACTION_SOLD;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v4.BinanceOperationTypeV4.OPERATION_TYPE_TRANSACTION_SPEND;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v4.BinanceOperationTypeV4.OPERATION_TYPE_WITHDRAWAL;

public class BinanceSwitcher {

    public static TransactionType operationTypeSwitcher(String operationType) {
        operationType = operationType.toUpperCase();
        if (OPERATION_TYPE_BUY.code.equals(operationType) || OPERATION_TYPE_BINANCE_CONVERT.code.equals(operationType)) {
            return BUY;
        }
        if (OPERATION_TYPE_SELL.code.equals(operationType) || OPERATION_TYPE_TRANSACTION_SPEND.code.equals(operationType)
        || OPERATION_TYPE_TRANSACTION_SOLD.code.equals(operationType)) {
            return SELL;
        }
        if (List.of(OPERATION_TYPE_DEPOSIT.code, OPERATION_TYPE_FIAT_DEPOSIT.code, OPERATION_TYPE_SIMPLE_EARN_FLEXIBLE_REDEMPTION.code,
            OPERATION_TYPE_SAVING_DISTRIBUTION.code, OPERATION_TYPE_SIMPLE_EARN_FLEXIBLE_SUBSCRIPTION.code,
            OPERATION_TYPE_SIMPLE_EARN_LOCKED_REDEMPTION.code).contains(operationType)) {
            return DEPOSIT;
        }
        if (List.of(OPERATION_TYPE_WITHDRAWAL.code, OPERATION_TYPE_FIAT_WITHDRAWAL.code, OPERATION_TYPE_FIAT_WITHDRAW.code,
                OPERATION_TYPE_C2C_TRANSFER.code)
            .contains(operationType)) {
            return WITHDRAWAL;
        }
        if (OPERATION_TYPE_FEE.code.equals(operationType)) {
            return FEE;
        }
        if (OPERATION_TYPE_TRANSACTION_RELATED.code.equals(operationType) || OPERATION_TYPE_LARGE_OTC_TRADING.code.equals(operationType)
            || OPERATION_TYPE_SMALL_ASSETS_EXCHANGE_BNB.code.equals(operationType)
            || OPERATION_TYPE_TRANSACTION_BUY.code.equals(operationType)
            || OPERATION_TYPE_BUY_CRYPTO.code.equals(operationType)
            || OPERATION_TYPE_TRANSACTION_REVENUE.code.equals(operationType)) {
            return BUY;
        }
        if (OPERATION_TYPE_DISTRIBUTION.code.equals(operationType) || OPERATION_TYPE_BNB_VAULT_REWARDS.code.equals(operationType)) {
            return REWARD;
        }
        if (OPERATION_TYPE_COMMISSION_REBATE.code.equals(operationType) || OPERATION_TYPE_CARD_CASHBACK.code.equals(operationType)
            || OPERATION_TYPE_CASHBACK_VOUCHER.code.equals(operationType)) {
            return REBATE;
        }
        if (OPERATION_TYPE_SIMPLE_EARN_FLEXIBLE_INTEREST.code.equals(operationType)
            || OPERATION_TYPE_SIMPLE_EARN_LOCKED_REWARDS.code.equals(operationType)) {
            return EARNING;
        }
        if (OPERATION_TYPE_STAKING_REWARDS.code.equals(operationType)) {
            return STAKING_REWARD;
        }
        if (OPERATION_TYPE_STAKING_REDEMPTION.code.equals(operationType)) {
            return UNSTAKE;
        }
        if (OPERATION_TYPE_STAKING_PURCHASE.code.equals(operationType)) {
            return STAKE;
        }
        return UNKNOWN;
    }

}
