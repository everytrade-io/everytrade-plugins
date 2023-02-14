package io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v4;

import java.util.ArrayList;
import java.util.List;

public class BinanceSupportedOperations {

    public static final List<String> SUPPORTED_ACCOUNT_TYPES = new ArrayList<>();
    public static final List<String> SUPPORTED_OPERATION_TYPES = new ArrayList<>();

    public static final List<String> BUY_SELL_OPERATION_TYPES = new ArrayList<>();
    public static final List<String> DEPOSIT_WITHDRAWAL_OPERATION_TYPES = new ArrayList<>();
    public static final List<String> FEE_OPERATION_TYPES = new ArrayList<>();
    public static final List<String> REWARD_OPERATION_TYPES = new ArrayList<>();

    public static final List<String> UNSUPPORTED_ACCOUNT_TYPES = new ArrayList<>();
    public static final List<String> UNSUPPORTED_OPERATION_TYPES = new ArrayList<>();

    static {
        // supported accounts
        SUPPORTED_ACCOUNT_TYPES.add(BinanceOperationTypeV4.ACCOUNT_TYPE_SPOT.code);
        SUPPORTED_ACCOUNT_TYPES.add(BinanceOperationTypeV4.ACCOUNT_TYPE_CARD.code);
        // supported operations
        SUPPORTED_OPERATION_TYPES.add(BinanceOperationTypeV4.OPERATION_TYPE_BUY.code);
        SUPPORTED_OPERATION_TYPES.add(BinanceOperationTypeV4.OPERATION_TYPE_SELL.code);
        SUPPORTED_OPERATION_TYPES.add(BinanceOperationTypeV4.OPERATION_TYPE_TRANSACTION_RELATED.code);
        SUPPORTED_OPERATION_TYPES.add(BinanceOperationTypeV4.OPERATION_TYPE_DEPOSIT.code);
        SUPPORTED_OPERATION_TYPES.add(BinanceOperationTypeV4.OPERATION_TYPE_WITHDRAWAL.code);
        SUPPORTED_OPERATION_TYPES.add(BinanceOperationTypeV4.OPERATION_TYPE_FEE.code);
        SUPPORTED_OPERATION_TYPES.add(BinanceOperationTypeV4.OPERATION_TYPE_DISTRIBUTION.code);
        SUPPORTED_OPERATION_TYPES.add(BinanceOperationTypeV4.OPERATION_TYPE_FIAT_DEPOSIT.code);
        SUPPORTED_OPERATION_TYPES.add(BinanceOperationTypeV4.OPERATION_TYPE_FIAT_WITHDRAWAL.code);
        SUPPORTED_OPERATION_TYPES.add(BinanceOperationTypeV4.OPERATION_TYPE_CARD_CASHBACK.code);
        SUPPORTED_OPERATION_TYPES.add(BinanceOperationTypeV4.OPERATION_TYPE_COMMISSION_REBATE.code);
        SUPPORTED_OPERATION_TYPES.add(BinanceOperationTypeV4.OPERATION_TYPE_LARGE_OTC_TRADING.code);

        BUY_SELL_OPERATION_TYPES.add(BinanceOperationTypeV4.OPERATION_TYPE_BUY.code);
        BUY_SELL_OPERATION_TYPES.add(BinanceOperationTypeV4.OPERATION_TYPE_SELL.code);
        BUY_SELL_OPERATION_TYPES.add(BinanceOperationTypeV4.OPERATION_TYPE_TRANSACTION_RELATED.code);

        DEPOSIT_WITHDRAWAL_OPERATION_TYPES.add(BinanceOperationTypeV4.OPERATION_TYPE_DEPOSIT.code);
        DEPOSIT_WITHDRAWAL_OPERATION_TYPES.add(BinanceOperationTypeV4.OPERATION_TYPE_WITHDRAWAL.code);

        FEE_OPERATION_TYPES.add(BinanceOperationTypeV4.OPERATION_TYPE_FEE.code);

        REWARD_OPERATION_TYPES.add(BinanceOperationTypeV4.OPERATION_TYPE_DISTRIBUTION.code);

        //unsupported accounts
        UNSUPPORTED_ACCOUNT_TYPES.add(BinanceOperationTypeV4.ACCOUNT_TYPE_CROSS_MARGIN.code);
        UNSUPPORTED_ACCOUNT_TYPES.add(BinanceOperationTypeV4.ACCOUNT_TYPE_ISOLATED_MARGIN.code);
        UNSUPPORTED_ACCOUNT_TYPES.add(BinanceOperationTypeV4.ACCOUNT_TYPE_P2P.code);
        UNSUPPORTED_ACCOUNT_TYPES.add(BinanceOperationTypeV4.ACCOUNT_TYPE_POOL.code);
        UNSUPPORTED_ACCOUNT_TYPES.add(BinanceOperationTypeV4.ACCOUNT_TYPE_USDT_FUTURES.code);

        //unsupported operations
        UNSUPPORTED_OPERATION_TYPES.add(BinanceOperationTypeV4.OPERATION_TYPE_MARGIN_LOAN.code);
        UNSUPPORTED_OPERATION_TYPES.add(BinanceOperationTypeV4.OPERATION_TYPE_MARGIN_REPAYMENT.code);
        UNSUPPORTED_OPERATION_TYPES.add(BinanceOperationTypeV4.OPERATION_TYPE_BNB_DEDUCT_FEE.code);
        UNSUPPORTED_OPERATION_TYPES.add(BinanceOperationTypeV4.OPERATION_TYPE_ISOLATED_MARGIN_LOAN.code);
        UNSUPPORTED_OPERATION_TYPES.add(BinanceOperationTypeV4.OPERATION_TYPE_ISOLATED_MARGIN_REPAYMENT.code);
        UNSUPPORTED_OPERATION_TYPES.add(BinanceOperationTypeV4.OPERATION_TYPE_TRANSFER_IN.code);
        UNSUPPORTED_OPERATION_TYPES.add(BinanceOperationTypeV4.OPERATION_TYPE_TRANSFER_OUT.code);
        UNSUPPORTED_OPERATION_TYPES.add(BinanceOperationTypeV4.OPERATION_TYPE_P2P_TRADING.code);
        UNSUPPORTED_OPERATION_TYPES.add(BinanceOperationTypeV4.OPERATION_TYPE_POOL_DISTRIBUTION.code);
        UNSUPPORTED_OPERATION_TYPES.add(BinanceOperationTypeV4.OPERATION_TYPE_BNB_DEDUCTS_FEE.code);
        UNSUPPORTED_OPERATION_TYPES.add(BinanceOperationTypeV4.OPERATION_TYPE_COMMISSION_FEE_SHARED_WITH_YOU.code);
        UNSUPPORTED_OPERATION_TYPES.add(BinanceOperationTypeV4.OPERATION_TYPE_COMMISSION_HISTORY.code);
        UNSUPPORTED_OPERATION_TYPES.add(BinanceOperationTypeV4.OPERATION_TYPE_DISTRIBUTION.code);
        UNSUPPORTED_OPERATION_TYPES.add(BinanceOperationTypeV4.OPERATION_TYPE_LAUNCHPOOL_INTEREST.code);
        UNSUPPORTED_OPERATION_TYPES.add(BinanceOperationTypeV4.OPERATION_TYPE_LIQUID_SWAP_ADD.code);
        UNSUPPORTED_OPERATION_TYPES.add(BinanceOperationTypeV4.OPERATION_TYPE_POS_SAVINGS_INTEREST.code);
        UNSUPPORTED_OPERATION_TYPES.add(BinanceOperationTypeV4.OPERATION_TYPE_POS_SAVINGS_PURCHASE.code);
        UNSUPPORTED_OPERATION_TYPES.add(BinanceOperationTypeV4.OPERATION_TYPE_POS_SAVINGS_REDEMPTION.code);
        UNSUPPORTED_OPERATION_TYPES.add(BinanceOperationTypeV4.OPERATION_TYPE_SAVINGS_INTEREST.code);
        UNSUPPORTED_OPERATION_TYPES.add(BinanceOperationTypeV4.OPERATION_TYPE_SAVINGS_PRINCIPAL_REDEMPTION.code);
        UNSUPPORTED_OPERATION_TYPES.add(BinanceOperationTypeV4.OPERATION_TYPE_SAVINGS_PURCHASE.code);
        UNSUPPORTED_OPERATION_TYPES.add(BinanceOperationTypeV4.OPERATION_TYPE_SMALL_ASSET_EXCHANGE_BNB.code);
        UNSUPPORTED_OPERATION_TYPES.add(BinanceOperationTypeV4.OPERATION_TYPE_SUPER_BNB_MINING.code);
        UNSUPPORTED_OPERATION_TYPES.add(BinanceOperationTypeV4.OPERATION_TYPE_FUNDING_FEE.code);
        UNSUPPORTED_OPERATION_TYPES.add(BinanceOperationTypeV4.OPERATION_TYPE_INSURANCE_FUND_COMPENSATION.code);
        UNSUPPORTED_OPERATION_TYPES.add(BinanceOperationTypeV4.OPERATION_TYPE_REALIZE_PROFIT_AND_LOSS.code);
        UNSUPPORTED_OPERATION_TYPES.add(BinanceOperationTypeV4.OPERATION_TYPE_REFEREE_REBATES.code);
    }
}
