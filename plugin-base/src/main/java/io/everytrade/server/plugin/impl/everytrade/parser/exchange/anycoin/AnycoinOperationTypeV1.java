package io.everytrade.server.plugin.impl.everytrade.parser.exchange.anycoin;

public enum AnycoinOperationTypeV1 {

    // Supported operation types
    OPERATION_TYPE_TRADE_PAYMENT("TRADE PAYMENT",true),
    OPERATION_TYPE_TRADE_FILL("TRADE FILL",true),
    OPERATION_TYPE_DEPOSIT("DEPOSIT",false),
    OPERATION_TYPE_STAKE("STAKE",true),
    OPERATION_TYPE_STAKE_REWARD("STAKE REWARD",true),
    OPERATION_TYPE_UNSTAKE("UNSTAKE",true),
    OPERATION_TYPE_WITHDRAWAL("WITHDRAWAL",false),

    // Unsupported operation types,
    OPERATION_TYPE_TRADE_REFUND("TRADE REFUND",true),
    OPERATION_TYPE_WITHDRAWAL_BLOCK("WITHDRAWAL_BLOCK",true),
    OPERATION_TYPE_WITHDRAWAL_UNBLOCK("WITHDRAWAL_UNBLOCK",true);


    AnycoinOperationTypeV1(String code, boolean isMultiRowType) {
        this.code = code;
        this.isMultiRowType = isMultiRowType;
    }

    public final String code;
    public final boolean isMultiRowType;

    public static AnycoinOperationTypeV1 getEnum(String code) {
        for (AnycoinOperationTypeV1 operationType : values()) {
            if (operationType.code.equals(code)) {
                return operationType;
            }
        }
        return null;
    }
}
