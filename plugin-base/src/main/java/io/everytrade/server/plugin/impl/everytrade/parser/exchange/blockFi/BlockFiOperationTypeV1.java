package io.everytrade.server.plugin.impl.everytrade.parser.exchange.blockFi;

public enum BlockFiOperationTypeV1 {
    OPERATION_TYPE_CRYPTO_TRANSFER("CRYPTO TRANSFER", false),
    OPERATION_TYPE_INTEREST_PAYMENT("INTEREST PAYMENT", false),
    OPERATION_TYPE_REFERRAL_BONUS("REFERRAL BONUS", false),
    OPERATION_TYPE_BONUS_PAYMENT("BONUS PAYMENT", false),
    OPERATION_TYPE_TRADE("TRADE", true);

    public final String code;
    public final boolean isMultiRowType;

    BlockFiOperationTypeV1(String code, boolean isMultiRowType) {
        this.code = code;
        this.isMultiRowType = isMultiRowType;
    }

    public static BlockFiOperationTypeV1 getEnum(String name) {
        for (BlockFiOperationTypeV1 e : values()) {
            if (e.code.equalsIgnoreCase(name)) {
                return e;
            }
        }
        return null;
    }
}
