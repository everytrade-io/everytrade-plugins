package io.everytrade.server.plugin.impl.everytrade.parser.exchange.coinbank;

public enum CoinbankStatus {
    SUBMITTED_BY_CLIENT("Zadaná klientem",0),
    PROCESSED_BY_OPERATOR("Proveden operátorem",1),
    CANCELED("Zrušen.",2),
    REALIZED("Realizován",4);

    private final String status;
    private final int id;

    CoinbankStatus(String status, int id) {
        this.status = status;
        this.id = id;
    }

    public int getId() {
        return id;
    }

    public static CoinbankStatus getEnum(int id) {
        for (CoinbankStatus e : values()) {
            if (e.id == id) {
                return e;
            }
        }
        return null;
    }
}
