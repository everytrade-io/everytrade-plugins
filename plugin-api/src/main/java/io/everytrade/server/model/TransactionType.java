package io.everytrade.server.model;

import java.util.HashMap;
import java.util.Map;

public enum TransactionType {
    UNKNOWN(0),
    BUY(2),
    SELL(3),
    DEPOSIT(6),
    WITHDRAWAL(7),
    FEE(4),
    REBATE(5),
    STAKE(8),
    UNSTAKE(9),
    STAKING_REWARD(10),
    AIRDROP(11),
    EARNING(12),
    REWARD(13),
    FORK(14),
    INCOMING_PAYMENT(15),
    OUTGOING_PAYMENT(16)
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

    public boolean isDepositOrWithdrawal() {
        return this == DEPOSIT || this == WITHDRAWAL;
    }

    public boolean isFeeOrRebate() {
        return this == FEE || this == REBATE;
    }

    public boolean isZeroCostGain() {
        return this == STAKING_REWARD || this == AIRDROP || this == EARNING || this == REWARD || this == FORK;
    }

    public boolean isStaking() {
        return this == STAKE || this == UNSTAKE || this == STAKING_REWARD;
    }

    public boolean isPayment() {
        return this == INCOMING_PAYMENT || this == OUTGOING_PAYMENT;
    }

    public boolean isDepositOrWithdrawalOrPayment() {
        return this == DEPOSIT || this == WITHDRAWAL || this == INCOMING_PAYMENT || this == OUTGOING_PAYMENT;
    }

}
