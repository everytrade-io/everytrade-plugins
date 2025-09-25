package io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v4;

import io.everytrade.server.model.TransactionType;

import java.util.HashMap;
import java.util.Map;

import static io.everytrade.server.model.TransactionType.AIRDROP;
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
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v4.BinanceOperationTypeV4.OPERATION_TYPE_AIRDROP_ASSETS;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v4.BinanceOperationTypeV4.OPERATION_TYPE_BINANCE_CONVERT;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v4.BinanceOperationTypeV4.OPERATION_TYPE_BNB_FEE_DEDUCTION;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v4.BinanceOperationTypeV4.OPERATION_TYPE_BNB_VAULT_REWARDS;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v4.BinanceOperationTypeV4.OPERATION_TYPE_BUY;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v4.BinanceOperationTypeV4.OPERATION_TYPE_BUY_CRYPTO;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v4.BinanceOperationTypeV4.OPERATION_TYPE_BUY_CRYPTO_WITH_FIAT;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v4.BinanceOperationTypeV4.OPERATION_TYPE_C2C_TRANSFER;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v4.BinanceOperationTypeV4.OPERATION_TYPE_CARD_CASHBACK;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v4.BinanceOperationTypeV4.OPERATION_TYPE_CASHBACK_VOUCHER;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v4.BinanceOperationTypeV4.OPERATION_TYPE_COMMISSION_REBATE;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v4.BinanceOperationTypeV4.OPERATION_TYPE_CONVERT_FIAT_TO_CRYPTO_OCBS;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v4.BinanceOperationTypeV4.OPERATION_TYPE_DEPOSIT;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v4.BinanceOperationTypeV4.OPERATION_TYPE_DEPOSIT_FIAT_OCBS;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v4.BinanceOperationTypeV4.OPERATION_TYPE_DISTRIBUTION;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v4.BinanceOperationTypeV4.OPERATION_TYPE_ETH2_0_STAKING;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v4.BinanceOperationTypeV4.OPERATION_TYPE_ETH2_0_STAKING_REWARDS;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v4.BinanceOperationTypeV4.OPERATION_TYPE_FEE;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v4.BinanceOperationTypeV4.OPERATION_TYPE_FIAT_DEPOSIT;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v4.BinanceOperationTypeV4.OPERATION_TYPE_FIAT_WITHDRAW;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v4.BinanceOperationTypeV4.OPERATION_TYPE_FIAT_WITHDRAWAL;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v4.BinanceOperationTypeV4.OPERATION_TYPE_LARGE_OTC_TRADING;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v4.BinanceOperationTypeV4.OPERATION_TYPE_REFERRAL_COMMISSION;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v4.BinanceOperationTypeV4.OPERATION_TYPE_SAVING_DISTRIBUTION;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v4.BinanceOperationTypeV4.OPERATION_TYPE_SELL;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v4.BinanceOperationTypeV4.OPERATION_TYPE_SELL_CRYPTO_TO_FIAT;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v4.BinanceOperationTypeV4.OPERATION_TYPE_SIMPLE_EARN_FLEXIBLE_INTEREST;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v4.BinanceOperationTypeV4.OPERATION_TYPE_SIMPLE_EARN_FLEXIBLE_REDEMPTION;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v4.BinanceOperationTypeV4.OPERATION_TYPE_SIMPLE_EARN_FLEXIBLE_SUBSCRIPTION;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v4.BinanceOperationTypeV4.OPERATION_TYPE_SIMPLE_EARN_LOCKED_REDEMPTION;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v4.BinanceOperationTypeV4.OPERATION_TYPE_SIMPLE_EARN_LOCKED_REWARDS;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v4.BinanceOperationTypeV4.OPERATION_TYPE_SMALL_ASSETS_EXCHANGE_BNB;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v4.BinanceOperationTypeV4.OPERATION_TYPE_STAKING_PURCHASE;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v4.BinanceOperationTypeV4.OPERATION_TYPE_STAKING_REDEMPTION;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v4.BinanceOperationTypeV4.OPERATION_TYPE_STAKING_REWARDS;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v4.BinanceOperationTypeV4.OPERATION_TYPE_TOKEN_SWAP;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v4.BinanceOperationTypeV4.OPERATION_TYPE_TRANSACTION_BUY;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v4.BinanceOperationTypeV4.OPERATION_TYPE_TRANSACTION_FEE;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v4.BinanceOperationTypeV4.OPERATION_TYPE_TRANSACTION_RELATED;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v4.BinanceOperationTypeV4.OPERATION_TYPE_TRANSACTION_REVENUE;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v4.BinanceOperationTypeV4.OPERATION_TYPE_TRANSACTION_SOLD;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v4.BinanceOperationTypeV4.OPERATION_TYPE_TRANSACTION_SPEND;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v4.BinanceOperationTypeV4.OPERATION_TYPE_TRANSFER_BETWEEN_MAIN_ACC_AND_SUB_ACC;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v4.BinanceOperationTypeV4.OPERATION_TYPE_WITHDRAWAL;

public class BinanceSwitcher {

    private static final Map<String, TransactionType> OPERATION_MAP;

    static {
        Map<String, TransactionType> map = new HashMap<>();

        putAll(map, BUY,
            OPERATION_TYPE_BUY.code,
            OPERATION_TYPE_BINANCE_CONVERT.code,
            OPERATION_TYPE_ETH2_0_STAKING.code,
            OPERATION_TYPE_BUY_CRYPTO_WITH_FIAT.code,
            OPERATION_TYPE_TRANSACTION_RELATED.code,
            OPERATION_TYPE_LARGE_OTC_TRADING.code,
            OPERATION_TYPE_SMALL_ASSETS_EXCHANGE_BNB.code,
            OPERATION_TYPE_TRANSACTION_BUY.code,
            OPERATION_TYPE_BUY_CRYPTO.code,
            OPERATION_TYPE_TRANSACTION_REVENUE.code,
            OPERATION_TYPE_CONVERT_FIAT_TO_CRYPTO_OCBS.code
        );

        putAll(map, SELL,
            OPERATION_TYPE_SELL.code,
            OPERATION_TYPE_TRANSACTION_SPEND.code,
            OPERATION_TYPE_TRANSACTION_SOLD.code,
            OPERATION_TYPE_SELL_CRYPTO_TO_FIAT.code
        );

        putAll(map, DEPOSIT,
            OPERATION_TYPE_DEPOSIT.code,
            OPERATION_TYPE_FIAT_DEPOSIT.code,
            OPERATION_TYPE_SIMPLE_EARN_FLEXIBLE_REDEMPTION.code,
            OPERATION_TYPE_SAVING_DISTRIBUTION.code,
            OPERATION_TYPE_SIMPLE_EARN_FLEXIBLE_SUBSCRIPTION.code,
            OPERATION_TYPE_SIMPLE_EARN_LOCKED_REDEMPTION.code,
            OPERATION_TYPE_DEPOSIT_FIAT_OCBS.code
        );

        putAll(map, WITHDRAWAL,
            OPERATION_TYPE_WITHDRAWAL.code,
            OPERATION_TYPE_FIAT_WITHDRAWAL.code,
            OPERATION_TYPE_FIAT_WITHDRAW.code,
            OPERATION_TYPE_C2C_TRANSFER.code,
            OPERATION_TYPE_TRANSFER_BETWEEN_MAIN_ACC_AND_SUB_ACC.code
        );

        putAll(map, FEE,
            OPERATION_TYPE_FEE.code,
            OPERATION_TYPE_TRANSACTION_FEE.code,
            OPERATION_TYPE_BNB_FEE_DEDUCTION.code
        );

        putAll(map, AIRDROP,
            OPERATION_TYPE_AIRDROP_ASSETS.code,
            OPERATION_TYPE_TOKEN_SWAP.code
        );

        putAll(map, REWARD,
            OPERATION_TYPE_DISTRIBUTION.code,
            OPERATION_TYPE_BNB_VAULT_REWARDS.code,
            OPERATION_TYPE_REFERRAL_COMMISSION.code
        );

        putAll(map, REBATE,
            OPERATION_TYPE_COMMISSION_REBATE.code,
            OPERATION_TYPE_CARD_CASHBACK.code,
            OPERATION_TYPE_CASHBACK_VOUCHER.code
        );

        putAll(map, EARNING,
            OPERATION_TYPE_SIMPLE_EARN_FLEXIBLE_INTEREST.code
        );

        putAll(map, STAKING_REWARD,
            OPERATION_TYPE_STAKING_REWARDS.code,
            OPERATION_TYPE_ETH2_0_STAKING_REWARDS.code,
            OPERATION_TYPE_SIMPLE_EARN_LOCKED_REWARDS.code
        );

        putAll(map, UNSTAKE,
            OPERATION_TYPE_STAKING_REDEMPTION.code
        );

        putAll(map, STAKE,
            OPERATION_TYPE_STAKING_PURCHASE.code
        );

        OPERATION_MAP = Map.copyOf(map);
    }

    private static void putAll(Map<String, TransactionType> map, TransactionType type, String... ops) {
        for (String op : ops) {
            map.put(op, type);
        }
    }

    public static TransactionType operationTypeSwitcher(String operationType) {
        if (operationType == null) {
            return UNKNOWN;
        }
        return OPERATION_MAP.getOrDefault(operationType.toUpperCase(), UNKNOWN);
    }
}
