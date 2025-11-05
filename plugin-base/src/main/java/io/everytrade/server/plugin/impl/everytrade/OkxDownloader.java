package io.everytrade.server.plugin.impl.everytrade;

import org.knowm.xchange.Exchange;
import org.knowm.xchange.dto.account.FundingRecord;
import org.knowm.xchange.dto.meta.ExchangeMetaData;
import org.knowm.xchange.dto.trade.UserTrade;
import org.knowm.xchange.dto.trade.UserTrades;
import org.knowm.xchange.okex.dto.OkexResponse;
import org.knowm.xchange.okex.dto.account.OkexDeposit;
import org.knowm.xchange.okex.dto.account.OkexWithdrawal;
import org.knowm.xchange.okex.dto.trade.OkexOrderDetails;
import org.knowm.xchange.okex.service.OkexAccountServiceRaw;
import org.knowm.xchange.okex.service.OkexTradeServiceRaw;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

//https://www.okex.com/docs/en/#spot-account_information - limit 20 requests per second
public class OkxDownloader {
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
    private final Exchange exchange;
    private final OkexAccountServiceRaw accountRaw;
    private final OkexTradeServiceRaw tradeRaw;

    public OkxDownloader(String downloadState, Exchange exchange) {
        this.exchange = exchange;
        this.accountRaw = (OkexAccountServiceRaw) exchange.getAccountService();
        this.tradeRaw = (OkexTradeServiceRaw) exchange.getTradeService();
        deserializeState(downloadState);
    }

    public List<UserTrade> downloadTrades() {
        final List<UserTrade> allTrades = new ArrayList<>();
        final ExchangeMetaData meta = exchange.getExchangeMetaData();

        try {
            if (lastTradeId == null || lastTradeId.isEmpty()) {
                // OLDER txs
                String afterOrdId = null;
                boolean firstPage = true;

                for (; ; ) {
                    OkexResponse<List<OkexOrderDetails>> resp;
                    try {
                        resp = tradeRaw.getOrderHistory(
                            "SPOT",
                            null,
                            null,
                            afterOrdId,
                            null,
                            "100"
                        );
                    } catch (IOException e) {
                        String msg = e.getMessage();
                        if (msg != null && msg.contains("429")) {
                            try {
                                Thread.sleep(300);
                            } catch (InterruptedException ignored) {
                                Thread.currentThread().interrupt();
                            }
                            continue;
                        }
                        throw e;
                    }
                    List<OkexOrderDetails> page = resp.getData();
                    if (page == null || page.isEmpty()) {
                        break;
                    }
                    if (firstPage) {
                        newTradeId = page.get(0).getOrderId();
                        firstPage = false;
                    }
                    UserTrades ut = OkxMappers.adaptUserTrades(page, meta);
                    if (ut.getUserTrades() != null) {
                        allTrades.addAll(ut.getUserTrades());
                    }
                    afterOrdId = page.get(page.size() - 1).getOrderId();
                    try {
                        Thread.sleep(80);
                    } catch (InterruptedException ignored) {
                        Thread.currentThread().interrupt();
                    }
                }

            } else {
                // NEWER txs
                String beforeOrdId = lastTradeId;
                boolean firstPage = true;

                for (; ; ) {
                    OkexResponse<List<OkexOrderDetails>> resp;
                    try {
                        resp = tradeRaw.getOrderHistory(
                            "SPOT",
                            null,
                            null,
                            null,
                            beforeOrdId,
                            "100"
                        );
                    } catch (IOException e) {
                        String msg = e.getMessage();
                        if (msg != null && msg.contains("429")) {
                            try {
                                Thread.sleep(300);
                            } catch (InterruptedException ignored) {
                                Thread.currentThread().interrupt();
                            }
                            continue;
                        }
                        throw e;
                    }
                    List<OkexOrderDetails> page = resp.getData();
                    if (page == null || page.isEmpty()) {
                        break;
                    }
                    if (firstPage) {
                        newTradeId = page.get(0).getOrderId();
                        firstPage = false;
                    }
                    UserTrades ut = OkxMappers.adaptUserTrades(page, meta);
                    if (ut.getUserTrades() != null) {
                        allTrades.addAll(ut.getUserTrades());
                    }
                    beforeOrdId = page.get(0).getOrderId();
                    try {
                        Thread.sleep(80);
                    } catch (InterruptedException ignored) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        allTrades.sort(Comparator
            .comparing(UserTrade::getTimestamp, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(UserTrade::getId, Comparator.nullsLast(Comparator.naturalOrder()))
            .reversed()
        );

        return allTrades;
    }

    public List<FundingRecord> downloadWithdrawals() {
        List<FundingRecord> results = new ArrayList<>();

        if (lastWithdrawalTs == null || lastWithdrawalTs.isEmpty()) {
            String after = null;
            boolean first = true;
            for (; ; ) {
                OkexResponse<List<OkexWithdrawal>> resp;
                try {
                    resp = accountRaw.getWithdrawalHistory(null, after, null);
                } catch (IOException e) {
                    String msg = e.getMessage();
                    if (msg != null && msg.contains("429")) {
                        try {
                            Thread.sleep(300);
                        } catch (InterruptedException ignored) {
                            Thread.currentThread().interrupt();
                        }
                        continue;
                    }
                    throw new RuntimeException(e);
                }
                List<OkexWithdrawal> page = resp.getData();
                if (page == null || page.isEmpty()) {
                    break;
                }

                if (first) {
                    newWithdrawalTs = page.get(0).getTs();
                    first = false;
                }
                for (OkexWithdrawal w : page) {
                    results.add(OkxMappers.mapWithdrawal(w));
                }
                after = page.get(page.size() - 1).getTs(); // older
            }
        } else {
            String before = lastWithdrawalTs;
            boolean first = true;
            for (; ; ) {
                OkexResponse<List<OkexWithdrawal>> resp;
                try {
                    resp = accountRaw.getWithdrawalHistory(null, null, before);
                } catch (IOException e) {
                    String msg = e.getMessage();
                    if (msg != null && msg.contains("429")) {
                        try {
                            Thread.sleep(300);
                        } catch (InterruptedException ignored) {
                            Thread.currentThread().interrupt();
                        }
                        continue;
                    }
                    throw new RuntimeException(e);
                }
                List<OkexWithdrawal> page = resp.getData();
                if (page == null || page.isEmpty()) {
                    break;
                }

                if (first) {
                    newWithdrawalTs = page.get(0).getTs();
                    first = false;
                }
                for (OkexWithdrawal w : page) {
                    results.add(OkxMappers.mapWithdrawal(w));
                }
                before = page.get(0).getTs(); // newer
                try {
                    Thread.sleep(80);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        results.sort(Comparator.comparing(FundingRecord::getDate).reversed());
        return results;
    }

    public List<FundingRecord> downloadDeposits() {
        List<FundingRecord> results = new ArrayList<>();

        if (lastDepositTs == null || lastDepositTs.isEmpty()) {
            String after = null;
            boolean first = true;
            for (; ; ) {
                OkexResponse<List<OkexDeposit>> resp;
                try {
                    resp = accountRaw.getDepositHistory(null, after, null);
                } catch (IOException e) {
                    String msg = e.getMessage();
                    if (msg != null && msg.contains("429")) {
                        try {
                            Thread.sleep(300);
                        } catch (InterruptedException ignored) {
                            Thread.currentThread().interrupt();
                        }
                        continue;
                    }
                    throw new RuntimeException(e);
                }

                List<OkexDeposit> page = resp.getData();
                if (page == null || page.isEmpty()) {
                    break;
                }

                if (first) {
                    newDepositTs = page.get(0).getTs();
                    first = false;
                }
                for (OkexDeposit d : page) {
                    results.add(OkxMappers.mapDeposit(d));
                }
                after = page.get(page.size() - 1).getTs(); // older
            }
        } else {
            String before = lastDepositTs;
            boolean first = true;
            for (; ; ) {
                OkexResponse<List<OkexDeposit>> resp;
                try {
                    resp = accountRaw.getDepositHistory(null, null, before);
                } catch (IOException e) {
                    String msg = e.getMessage();
                    if (msg != null && msg.contains("429")) {
                        try {
                            Thread.sleep(300);
                        } catch (InterruptedException ignored) {
                            Thread.currentThread().interrupt();
                        }
                        continue;
                    }
                    throw new RuntimeException(e);
                }

                List<OkexDeposit> page = resp.getData();
                if (page == null || page.isEmpty()) {
                    break;
                }

                if (first) {
                    newDepositTs = page.get(0).getTs();
                    first = false;
                }
                for (OkexDeposit d : page) {
                    results.add(OkxMappers.mapDeposit(d));
                }
                before = page.get(0).getTs(); // newer
                try {
                    Thread.sleep(80);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        results.sort(Comparator.comparing(FundingRecord::getDate).reversed());
        return results;
    }

    private void deserializeState(String state) {
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
                case K_TRD:
                    lastTradeId = val;
                    break;
                case K_DEP:
                    lastDepositTs = val;
                    break;
                case K_WDR:
                    lastWithdrawalTs = val;
                    break;
                default: // ignore unknown keys
            }
        }
    }

    public String serializeState() {
        String trd = (newTradeId != null ? newTradeId : (lastTradeId != null ? lastTradeId : ""));
        String dep = (newDepositTs != null ? newDepositTs : (lastDepositTs != null ? lastDepositTs : ""));
        String wdr = (newWithdrawalTs != null ? newWithdrawalTs : (lastWithdrawalTs != null ? lastWithdrawalTs : ""));

        return K_TRD + KV_SEP + trd + STATE_SEP
            + K_DEP + KV_SEP + dep + STATE_SEP
            + K_WDR + KV_SEP + wdr;
    }
}