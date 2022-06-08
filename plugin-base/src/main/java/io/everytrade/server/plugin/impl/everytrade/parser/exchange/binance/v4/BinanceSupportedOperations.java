package io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v4;

import java.util.ArrayList;
import java.util.List;

public class BinanceSupportedOperations {

    public static final List<String> supportedAccountTypes = new ArrayList<>();
    public static final List<String> supportedOperationTypes = new ArrayList<>();

    public static final List<String> buySellOperationTypes = new ArrayList<>();
    public static final List<String> depositWithdrawalOperationTypes = new ArrayList<>();
    public static final List<String> feeOperationTypes = new ArrayList<>();

    public static final List<String> unsupportedAccountTypes = new ArrayList<>();
    public static final List<String> unsupportedOperationTypes = new ArrayList<>();

    static {
        // supported accounts
        supportedAccountTypes.add(BinanceConstantsV4.ACCOUNT_TYPE_SPOT.code);
        // supported operations
        supportedOperationTypes.add(BinanceConstantsV4.OPERATION_TYPE_BUY.code);
        supportedOperationTypes.add(BinanceConstantsV4.OPERATION_TYPE_SELL.code);
        supportedOperationTypes.add(BinanceConstantsV4.OPERATION_TYPE_TRANSACTION_RELATED.code);
        supportedOperationTypes.add(BinanceConstantsV4.OPERATION_TYPE_DEPOSIT.code);
        supportedOperationTypes.add(BinanceConstantsV4.OPERATION_TYPE_WITHDRAWAL.code);
        supportedOperationTypes.add(BinanceConstantsV4.OPERATION_TYPE_FEE.code);

        buySellOperationTypes.add(BinanceConstantsV4.OPERATION_TYPE_BUY.code);
        buySellOperationTypes.add(BinanceConstantsV4.OPERATION_TYPE_SELL.code);
        buySellOperationTypes.add(BinanceConstantsV4.OPERATION_TYPE_TRANSACTION_RELATED.code);

        depositWithdrawalOperationTypes.add(BinanceConstantsV4.OPERATION_TYPE_DEPOSIT.code);
        depositWithdrawalOperationTypes.add(BinanceConstantsV4.OPERATION_TYPE_WITHDRAWAL.code);

        feeOperationTypes.add(BinanceConstantsV4.OPERATION_TYPE_FEE.code);

        //unsupported accounts
        unsupportedAccountTypes.add(BinanceConstantsV4.ACCOUNT_TYPE_CROSS_MARGIN.code);
        unsupportedAccountTypes.add(BinanceConstantsV4.ACCOUNT_TYPE_ISOLATED_MARGIN.code);
        unsupportedAccountTypes.add(BinanceConstantsV4.ACCOUNT_TYPE_P2P.code);
        unsupportedAccountTypes.add(BinanceConstantsV4.ACCOUNT_TYPE_POOL.code);
        unsupportedAccountTypes.add(BinanceConstantsV4.ACCOUNT_TYPE_USDT_FUTURES.code);

        //unsupported operations
        unsupportedOperationTypes.add(BinanceConstantsV4.OPERATION_TYPE_MARGIN_LOAN.code);
        unsupportedOperationTypes.add(BinanceConstantsV4.OPERATION_TYPE_MARGIN_REPAYMENT.code);
        unsupportedOperationTypes.add(BinanceConstantsV4.OPERATION_TYPE_BNB_DEDUCT_FEE.code);
        unsupportedOperationTypes.add(BinanceConstantsV4.OPERATION_TYPE_ISOLATED_MARGIN_LOAN.code);
        unsupportedOperationTypes.add(BinanceConstantsV4.OPERATION_TYPE_ISOLATED_MARGIN_REPAYMENT.code);
        unsupportedOperationTypes.add(BinanceConstantsV4.OPERATION_TYPE_TRANSFER_IN.code);
        unsupportedOperationTypes.add(BinanceConstantsV4.OPERATION_TYPE_TRANSFER_OUT.code);
        unsupportedOperationTypes.add(BinanceConstantsV4.OPERATION_TYPE_P2P_TRADING.code);
        unsupportedOperationTypes.add(BinanceConstantsV4.OPERATION_TYPE_POOL_DISTRIBUTION.code);
        unsupportedOperationTypes.add(BinanceConstantsV4.OPERATION_TYPE_BNB_DEDUCTS_FEE.code);
        unsupportedOperationTypes.add(BinanceConstantsV4.OPERATION_TYPE_COMMISSION_FEE_SHARED_WITH_YOU.code);
        unsupportedOperationTypes.add(BinanceConstantsV4.OPERATION_TYPE_COMMISSION_HISTORY.code);
        unsupportedOperationTypes.add(BinanceConstantsV4.OPERATION_TYPE_DISTRIBUTION.code);
        unsupportedOperationTypes.add(BinanceConstantsV4.OPERATION_TYPE_LARGE_OTC_TRADING.code);
        unsupportedOperationTypes.add(BinanceConstantsV4.OPERATION_TYPE_LAUNCHPOOL_INTEREST.code);
        unsupportedOperationTypes.add(BinanceConstantsV4.OPERATION_TYPE_LIQUID_SWAP_ADD.code);
        unsupportedOperationTypes.add(BinanceConstantsV4.OPERATION_TYPE_POS_SAVINGS_INTEREST.code);
        unsupportedOperationTypes.add(BinanceConstantsV4.OPERATION_TYPE_POS_SAVINGS_PURCHASE.code);
        unsupportedOperationTypes.add(BinanceConstantsV4.OPERATION_TYPE_POS_SAVINGS_REDEMPTION.code);
        unsupportedOperationTypes.add(BinanceConstantsV4.OPERATION_TYPE_SAVINGS_INTEREST.code);
        unsupportedOperationTypes.add(BinanceConstantsV4.OPERATION_TYPE_SAVINGS_PRINCIPAL_REDEMPTION.code);
        unsupportedOperationTypes.add(BinanceConstantsV4.OPERATION_TYPE_SAVINGS_PURCHASE.code);
        unsupportedOperationTypes.add(BinanceConstantsV4.OPERATION_TYPE_SMALL_ASSET_EXCHANGE_BNB.code);
        unsupportedOperationTypes.add(BinanceConstantsV4.OPERATION_TYPE_SUPER_BNB_MINING.code);
        unsupportedOperationTypes.add(BinanceConstantsV4.OPERATION_TYPE_FUNDING_FEE.code);
        unsupportedOperationTypes.add(BinanceConstantsV4.OPERATION_TYPE_INSURANCE_FUND_COMPENSATION.code);
        unsupportedOperationTypes.add(BinanceConstantsV4.OPERATION_TYPE_REALIZE_PROFIT_AND_LOSS.code);
        unsupportedOperationTypes.add(BinanceConstantsV4.OPERATION_TYPE_REFEREE_REBATES.code);
    }
}