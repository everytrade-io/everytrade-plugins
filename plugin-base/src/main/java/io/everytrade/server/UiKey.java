package io.everytrade.server;

public class UiKey {

    public static final String CONNECTION_CURRENCY_PAIRS_DESC = "api_connection.currency_pairs";
    public static final String CONNECTION_CURRENCY_PAIRS_SETTINGS = "api_connection.pair_settings";
    public static final String CONNECTION_USER_NAME = "api_connection.user_name";
    public static final String CONNECTION_WALLET_ADDR = "api_connection.wallet_address";
    public static final String CONNECTION_WALLET_ADDR_XPUB_KEY = "api_connection.wallet_address_xpub";
    public static final String CONNECTION_FIAT_CURRENCY = "api_connection.fiat_currency";
    public static final String CONNECTION_DEPOSITS_AS_BUY_OPT = "api_connection.deposits_as_buy_tx";
    public static final String CONNECTION_WITHDRAWAL_AS_SELL_OPT = "api_connection.withdrawals_as_sell_tx";
    public static final String CONNECTION_DEPOSIT_MINING_FEE_AS_BUY_OPT = "api_connection.deposit_mining_fee_as_buy";
    public static final String CONNECTION_WITHDRAWAL_MINING_FEE_AS_SELL_OPT =
        "api_connection.withdrawal_mining_fee_as_sell";
    /** ETH/SOL option {@code importFeesFromWithdrawals}: book every fee the wallet paid (2026-10-20). */
    public static final String CONNECTION_FEES_PAID_BY_WALLET = "api_connection.fees_paid_by_wallet";
    public static final String CONNECTION_FEES_PAID_BY_WALLET_NOTE = "api_connection.fees_paid_by_wallet_note";
    /** ETH/SOL option {@code importFeesFromDeposits}: also book the sender's fee on a deposit (2026-10-20). */
    public static final String CONNECTION_SENDER_FEE_ON_DEPOSIT = "api_connection.sender_fee_on_deposit";
    public static final String CONNECTION_SENDER_FEE_ON_DEPOSIT_NOTE = "api_connection.sender_fee_on_deposit_note";
    public static final String CONNECTION_WALLET_ADDR_LTUB = "api_connection.wallet_address_ltub";
    public static final String CONNECTION_PASSPHRASE = "api_connection.passphrase";
    public static final String NORMAL_TXS = "api_connection.normal_transactions";
    public static final String ERC20_TXS = "api_connection.erc20_transactions";
    public static final String INTERPRETATION_LABEL = "api_connection.interpretation";
    public static final String FEES_LABEL = "api_connection.fees";
    public static final String SOURCES_LABEL = "api_connection.sources";
    public static final String IS_PAID_SUBSCRIPTION = "api_connection.isPaidSubscription";
}
