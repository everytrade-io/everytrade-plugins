package io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v4;

public enum BinanceOperationTypeV4 {
    // Supported account types
    ACCOUNT_TYPE_SPOT("Spot", true),
    ACCOUNT_TYPE_CARD("Card", true),
    // Unsupported account types
    ACCOUNT_TYPE_CROSS_MARGIN("CrossMargin", true),
    ACCOUNT_TYPE_ISOLATED_MARGIN("IsolatedMargin", true),
    ACCOUNT_TYPE_P2P("P2P", true),
    ACCOUNT_TYPE_POOL("Pool", true),
    ACCOUNT_TYPE_USDT_FUTURES("USDT-Futures", true),

    // Supported operations
    OPERATION_TYPE_BUY("Buy", true),
    OPERATION_TYPE_SELL("Sell", true),
    OPERATION_TYPE_WITHDRAWAL("Withdraw", true),
    OPERATION_TYPE_DEPOSIT("Deposit", true),
    OPERATION_TYPE_TRANSACTION_RELATED("Transaction Related", true),
    OPERATION_TYPE_FEE("Fee", true),
    OPERATION_TYPE_DISTRIBUTION("Distribution", true),
    OPERATION_TYPE_FIAT_WITHDRAWAL("Fiat Withdrawal", true),
    OPERATION_TYPE_FIAT_DEPOSIT("Fiat Deposit", true),
    OPERATION_TYPE_CARD_CASHBACK("Card Cashback", false),
    OPERATION_TYPE_COMMISSION_REBATE("Commission Rebate", false),
    OPERATION_TYPE_SMALL_ASSETS_EXCHANGE_BNB("Small assets exchange BNB", true),

    // Unsupported operations
    OPERATION_TYPE_MARGIN_LOAN("Margin loan", true),
    OPERATION_TYPE_MARGIN_REPAYMENT("Margin Repayment", true),
    OPERATION_TYPE_BNB_DEDUCT_FEE("BNB deducts fee", true),
    OPERATION_TYPE_ISOLATED_MARGIN_LOAN("IsolatedMargin loan", true),
    OPERATION_TYPE_ISOLATED_MARGIN_REPAYMENT("IsolatedMargin repayment", true),
    OPERATION_TYPE_TRANSFER_IN("transfer_in", true),
    OPERATION_TYPE_TRANSFER_OUT("transfer_out", true),
    OPERATION_TYPE_P2P_TRADING("P2P Trading", true),
    OPERATION_TYPE_POOL_DISTRIBUTION("Pool Distribution", true),
    OPERATION_TYPE_BNB_DEDUCTS_FEE("BNB deducts fee", true),
    OPERATION_TYPE_COMMISSION_FEE_SHARED_WITH_YOU("Commission Fee Shared With You", true),
    OPERATION_TYPE_COMMISSION_HISTORY("Commission History", true),
    OPERATION_TYPE_LARGE_OTC_TRADING("Large OTC trading", true),
    OPERATION_TYPE_LAUNCHPOOL_INTEREST("Launchpool Interest", true),
    OPERATION_TYPE_LIQUID_SWAP_ADD("Liquid Swap add", true),
    OPERATION_TYPE_POS_SAVINGS_INTEREST("POS savings interest", true),
    OPERATION_TYPE_POS_SAVINGS_PURCHASE("POS savings purchase", true),
    OPERATION_TYPE_POS_SAVINGS_REDEMPTION("POS savings redemption", true),
    OPERATION_TYPE_SAVINGS_INTEREST("Savings Interest", true),
    OPERATION_TYPE_SAVINGS_PRINCIPAL_REDEMPTION("Savings Principal redemption", true),
    OPERATION_TYPE_SAVINGS_PURCHASE("Savings purchase", true),
    OPERATION_TYPE_SMALL_ASSET_EXCHANGE_BNB("Small assets exchange BNB", true),
    OPERATION_TYPE_SUPER_BNB_MINING("Super BNB Mining", true),
    OPERATION_TYPE_FUNDING_FEE("Funding Fee", true),
    OPERATION_TYPE_INSURANCE_FUND_COMPENSATION("Insurance fund compensation", true),
    OPERATION_TYPE_REALIZE_PROFIT_AND_LOSS("Realize profit and loss", true),
    OPERATION_TYPE_REFEREE_REBATES("Referee rebates", true),

    //Remarks
    REMARKS_NO_FEE("Withdraw fee is included", true);

    public final String code;
    public final boolean isMultiRowType;

    BinanceOperationTypeV4(String code, boolean isMultiRowType) {
        this.code = code;
        this.isMultiRowType = isMultiRowType;
    }

    public static BinanceOperationTypeV4 getEnum(String name) {
        for (BinanceOperationTypeV4 e : values()) {
            if (e.code.equals(name)) {
                return e;
            }
        }
        return null;
    }

}
