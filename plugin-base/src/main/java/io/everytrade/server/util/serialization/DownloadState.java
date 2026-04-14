package io.everytrade.server.util.serialization;

import lombok.Getter;
import lombok.Setter;

import java.util.Objects;

@Getter
@Setter
public final class DownloadState {
    private static final String STATE_SEP = "|";
    private static final String KV_SEP = "=";

    private static final String K_TRD = "TRD";
    private static final String K_DEP = "DEP";
    private static final String K_WDR = "WDR";

    private String lastTradeId;
    private String lastDepositTs;
    private String lastWithdrawalTs;

    private String newTradeId;
    private String newDepositTs;
    private String newWithdrawalTs;

    public static DownloadState from(String serialized) {
        DownloadState s = new DownloadState();
        s.deserialize(serialized);
        return s;
    }

    public String serialize() {
        String trd = firstNonNull(newTradeId, lastTradeId, "");
        String dep = firstNonNull(newDepositTs, lastDepositTs, "");
        String wdr = firstNonNull(newWithdrawalTs, lastWithdrawalTs, "");

        return K_TRD + KV_SEP + trd + STATE_SEP
            + K_DEP + KV_SEP + dep + STATE_SEP
            + K_WDR + KV_SEP + wdr;
    }

    public void deserialize(String state) {
        lastTradeId = null;
        lastDepositTs = null;
        lastWithdrawalTs = null;

        if (state == null || state.isEmpty()) {
            return;
        }

        String[] parts = state.split("\\" + STATE_SEP, -1);
        for (String p : parts) {
            int idx = p.indexOf(KV_SEP);
            if (idx <= 0) {
                continue;
            }

            String key = p.substring(0, idx).trim();
            String val = p.substring(idx + 1).trim();
            if (val.isEmpty()) {
                val = null;
            }

            switch (key) {
                case K_TRD -> lastTradeId = val;
                case K_DEP -> lastDepositTs = val;
                case K_WDR -> lastWithdrawalTs = val;
                default -> { /* ignore */ }
            }
        }
    }

    private static String firstNonNull(String a, String b, String c) {
        if (a != null) {
            return a;
        }
        if (b != null) {
            return b;
        }
        return Objects.requireNonNull(c);
    }
}