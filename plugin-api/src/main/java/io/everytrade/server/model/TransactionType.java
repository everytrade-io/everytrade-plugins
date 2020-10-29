package io.everytrade.server.model;

import java.util.HashMap;
import java.util.Map;

public enum TransactionType {
    UNKNOWN(0), BUY(2), SELL(3);

    private final int code;
    private static final Map<Integer, TransactionType> BY_CODE;

    static {
        BY_CODE = new HashMap<>();
        for (TransactionType value : values()) {
            BY_CODE.put(value.code, value);
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
}
