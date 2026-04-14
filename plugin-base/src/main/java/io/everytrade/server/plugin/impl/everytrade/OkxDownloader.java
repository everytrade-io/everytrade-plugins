package io.everytrade.server.plugin.impl.everytrade;

import io.everytrade.server.util.serialization.DownloadState;
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
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// https://www.okex.com/docs/en/#spot-account_information - limit 20 requests per second
public class OkxDownloader {
    private final Exchange exchange;
    private final OkexAccountServiceRaw accountRaw;
    private final OkexTradeServiceRaw tradeRaw;

    private final DownloadState state;

    public OkxDownloader(String downloadState, Exchange exchange) {
        this.exchange = exchange;
        this.accountRaw = (OkexAccountServiceRaw) exchange.getAccountService();
        this.tradeRaw = (OkexTradeServiceRaw) exchange.getTradeService();
        this.state = DownloadState.from(downloadState);
    }

    public String serializeState() {
        return state.serialize();
    }

    public List<UserTrade> downloadTrades() {
        final List<UserTrade> allTrades = new ArrayList<>();
        final ExchangeMetaData meta = exchange.getExchangeMetaData();

        final String lastTradeId = state.getLastTradeId();

        try {
            String afterOrdId = null;
            boolean firstPage = true;
            boolean reachedLastTrade = false;

            for (; ; ) {
                OkexResponse<List<OkexOrderDetails>> resp;
                try {
                    resp = tradeRaw.getOrderHistory(
                        "SPOT",
                        null,
                        null,
                        afterOrdId, // after
                        null,       // before
                        "100"
                    );
                } catch (IOException e) {
                    if (is429(e)) {
                        sleepQuietly(300);
                        continue;
                    }
                    throw e;
                }

                List<OkexOrderDetails> page = resp.getData();
                if (page == null || page.isEmpty()) {
                    break;
                }

                if (firstPage) {
                    state.setNewTradeId(page.get(0).getOrderId());
                    firstPage = false;
                }

                for (OkexOrderDetails od : page) {
                    if (lastTradeId != null && lastTradeId.equals(od.getOrderId())) {
                        reachedLastTrade = true;
                        break;
                    }
                    UserTrades ut = OkxMappers.adaptUserTrades(Collections.singletonList(od), meta);
                    if (ut.getUserTrades() != null) {
                        allTrades.addAll(ut.getUserTrades());
                    }
                }

                if (reachedLastTrade) {
                    break;
                }

                afterOrdId = page.get(page.size() - 1).getOrderId();

                // OKX rate limiting
                sleepQuietly(80);
            }

        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        Map<String, UserTrade> dedup = new LinkedHashMap<>();
        for (UserTrade t : allTrades) {
            String id = (t.getId() != null) ? t.getId() : UUID.randomUUID().toString();
            dedup.put(id, t);
        }

        List<UserTrade> result = new ArrayList<>(dedup.values());

        result.sort(
            Comparator
                .comparing(UserTrade::getTimestamp, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(UserTrade::getId, Comparator.nullsLast(Comparator.naturalOrder()))
                .reversed()
        );

        return result;
    }

    public List<FundingRecord> downloadWithdrawals() {
        List<FundingRecord> results = new ArrayList<>();

        Long lastTs = parseLongOrNull(state.getLastWithdrawalTs());

        String after = null;
        boolean first = true;
        boolean reachedLast = false;

        for (; ; ) {
            OkexResponse<List<OkexWithdrawal>> resp;
            try {
                resp = accountRaw.getWithdrawalHistory(null, after, null);
            } catch (IOException e) {
                if (is429(e)) {
                    sleepQuietly(300);
                    continue;
                }
                throw new RuntimeException(e);
            }

            List<OkexWithdrawal> page = resp.getData();
            if (page == null || page.isEmpty()) {
                break;
            }

            if (first) {
                // newest timestamp cursor
                state.setNewWithdrawalTs(page.get(0).getTs());
                first = false;
            }

            for (OkexWithdrawal w : page) {
                long ts = Long.parseLong(w.getTs());

                if (lastTs != null && ts <= lastTs) {
                    reachedLast = true;
                    break;
                }

                results.add(OkxMappers.mapWithdrawal(w));
            }

            if (reachedLast) {
                break;
            }

            after = page.get(page.size() - 1).getTs();
            sleepQuietly(80);
        }

        results.sort(Comparator.comparing(FundingRecord::getDate).reversed());
        return results;
    }

    public List<FundingRecord> downloadDeposits() {
        List<FundingRecord> results = new ArrayList<>();

        Long lastTs = parseLongOrNull(state.getLastDepositTs());

        String after = null;
        boolean first = true;
        boolean reachedLast = false;

        for (; ; ) {
            OkexResponse<List<OkexDeposit>> resp;
            try {
                resp = accountRaw.getDepositHistory(null, after, null);
            } catch (IOException e) {
                if (is429(e)) {
                    sleepQuietly(300);
                    continue;
                }
                throw new RuntimeException(e);
            }

            List<OkexDeposit> page = resp.getData();
            if (page == null || page.isEmpty()) {
                break;
            }

            if (first) {
                state.setNewDepositTs(page.get(0).getTs());
                first = false;
            }

            for (OkexDeposit d : page) {
                long ts = Long.parseLong(d.getTs());

                if (lastTs != null && ts <= lastTs) {
                    reachedLast = true;
                    break;
                }

                results.add(OkxMappers.mapDeposit(d));
            }

            if (reachedLast) {
                break;
            }

            after = page.get(page.size() - 1).getTs();
            sleepQuietly(80);
        }

        results.sort(Comparator.comparing(FundingRecord::getDate).reversed());
        return results;
    }

    private static boolean is429(IOException e) {
        String msg = e.getMessage();
        return msg != null && msg.contains("429");
    }

    private static Long parseLongOrNull(String v) {
        if (v == null || v.isBlank()) {
            return null;
        }
        return Long.parseLong(v);
    }

    private static void sleepQuietly(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }
}