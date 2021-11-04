package io.everytrade.server.model;

import java.util.HashMap;
import java.util.Map;

public enum TransactionType {
    UNKNOWN(0),
    BUY(2),
    SELL(3),
    FEE(4),
    REBATE(5),
    DEPOSIT(6),
    WITHDRAW(7)
    ;

    private final int code;
    private static final Map<Integer, TransactionType> BY_CODE = new HashMap<>();

    static {
        for (TransactionType value : values()) {
            final TransactionType previousMapping = BY_CODE.put(value.code, value);
            if (previousMapping != null) {
                throw new IllegalStateException(
                    String.format("Multiple constants map to %d: '%s', '%s'.", value.code, value, previousMapping)
                );
            }
        }
    }

    TransactionType(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public static TransactionType fromCode(int code) {
        final TransactionType transactionType = BY_CODE.get(code);
        if (transactionType == null) {
            throw new IllegalArgumentException(String.format("Unrecognized transaction type code: %d.", code));
        }
        return transactionType;
    }

    public boolean isBuyOrSell() {
        return this == BUY || this == SELL;
    }

    public boolean isDepositOrWithdraw() {
        return this == DEPOSIT || this == WITHDRAW;
    }
}
