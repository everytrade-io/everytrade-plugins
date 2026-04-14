package io.everytrade.server.plugin.impl.everytrade.parser.exchange.coinbank;

public enum CoinbankOperationTypeV1 {

    OPERATION_TYPE_BUY("BUY", false),
    OPERATION_TYPE_SELL("SELL", false),
    OPERATION_TYPE_WITHDRAWAL("VÝBĚR", false),
    OPERATION_TYPE_DEPOSIT("VKLAD", false);

    public final String code;
    public final boolean isMultiRowType;

    CoinbankOperationTypeV1(String code, boolean isMultiRowType) {
        this.code = code;
        this.isMultiRowType = isMultiRowType;
    }

    public static CoinbankOperationTypeV1 getEnum(String name) {
        for (CoinbankOperationTypeV1 e : values()) {
            if (e.code.equalsIgnoreCase(name)) {
                return e;
            }
        }
        return null;
    }

}
