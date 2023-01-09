package io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v4;

import java.util.Arrays;
import java.util.Optional;

public enum BinanceConstantsV4 {
    // Supported account types
    ACCOUNT_TYPE_SPOT("Spot"),
    // Unsupported account types
    ACCOUNT_TYPE_CROSS_MARGIN("CrossMargin"),
    ACCOUNT_TYPE_ISOLATED_MARGIN("IsolatedMargin"),
    ACCOUNT_TYPE_P2P("P2P"),
    ACCOUNT_TYPE_POOL("Pool"),
    ACCOUNT_TYPE_USDT_FUTURES("USDT-Futures"),

    // Supported operations
    OPERATION_TYPE_BUY("Buy"),
    OPERATION_TYPE_SELL("Sell"),
    OPERATION_TYPE_WITHDRAWAL("Withdraw"),
    OPERATION_TYPE_DEPOSIT("Deposit"),
    OPERATION_TYPE_TRANSACTION_RELATED("Transaction Related"),
    OPERATION_TYPE_FEE("Fee"),
    OPERATION_TYPE_DISTRIBUTION("Distribution"),

    // Unsupported operations
    OPERATION_TYPE_MARGIN_LOAN("Margin loan"),
    OPERATION_TYPE_MARGIN_REPAYMENT("Margin Repayment"),
    OPERATION_TYPE_BNB_DEDUCT_FEE("BNB deducts fee"),
    OPERATION_TYPE_ISOLATED_MARGIN_LOAN("IsolatedMargin loan"),
    OPERATION_TYPE_ISOLATED_MARGIN_REPAYMENT("IsolatedMargin repayment"),
    OPERATION_TYPE_TRANSFER_IN("transfer_in"),
    OPERATION_TYPE_TRANSFER_OUT("transfer_out"),
    OPERATION_TYPE_P2P_TRADING("P2P Trading"),
    OPERATION_TYPE_POOL_DISTRIBUTION("Pool Distribution"),
    OPERATION_TYPE_BNB_DEDUCTS_FEE("BNB deducts fee"),
    OPERATION_TYPE_COMMISSION_FEE_SHARED_WITH_YOU("Commission Fee Shared With You"),
    OPERATION_TYPE_COMMISSION_HISTORY("Commission History"),
    OPERATION_TYPE_LARGE_OTC_TRADING("Large OTC trading"),
    OPERATION_TYPE_LAUNCHPOOL_INTEREST("Launchpool Interest"),
    OPERATION_TYPE_LIQUID_SWAP_ADD("Liquid Swap add"),
    OPERATION_TYPE_POS_SAVINGS_INTEREST("POS savings interest"),
    OPERATION_TYPE_POS_SAVINGS_PURCHASE("POS savings purchase"),
    OPERATION_TYPE_POS_SAVINGS_REDEMPTION("POS savings redemption"),
    OPERATION_TYPE_SAVINGS_INTEREST("Savings Interest"),
    OPERATION_TYPE_SAVINGS_PRINCIPAL_REDEMPTION("Savings Principal redemption"),
    OPERATION_TYPE_SAVINGS_PURCHASE("Savings purchase"),
    OPERATION_TYPE_SMALL_ASSET_EXCHANGE_BNB("Small assets exchange BNB"),
    OPERATION_TYPE_SUPER_BNB_MINING("Super BNB Mining"),
    OPERATION_TYPE_FUNDING_FEE("Funding Fee"),
    OPERATION_TYPE_INSURANCE_FUND_COMPENSATION("Insurance fund compensation"),
    OPERATION_TYPE_REALIZE_PROFIT_AND_LOSS("Realize profit and loss"),
    OPERATION_TYPE_REFEREE_REBATES("Referee rebates"),

    //Remarks
    REMARKS_NO_FEE("Withdraw fee is included");

    public final String code;

    BinanceConstantsV4(String code) {
        this.code = code;
    }

    public static Optional<BinanceConstantsV4> getEnum(String s) {
        return Arrays.stream(BinanceConstantsV4.values()).filter(e -> e.code.equals(s))
            .findFirst();
    }
}
