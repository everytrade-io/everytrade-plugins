package io.everytrade.server.plugin.impl.everytrade;

import io.everytrade.server.util.serialization.DownloadState;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.dase.dto.account.ApiAccountTxn;
import org.knowm.xchange.dase.dto.account.ApiGetAccountTxnsOutput;
import org.knowm.xchange.dase.service.DaseAccountService;
import org.knowm.xchange.dase.service.DaseTradeService;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.account.FundingRecord;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.knowm.xchange.dto.trade.OpenOrders;
import org.knowm.xchange.dto.trade.UserTrade;
import org.knowm.xchange.instrument.Instrument;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

public class DaseDownloader {
    private final DaseAccountService accountService;
    private final DaseTradeService tradeService;
    private final DownloadState state;
    private static final int MAX_TRADES_PER_RUN = 100_000;

    public DaseDownloader(String downloadState, Exchange exchange) {
        this.accountService = (DaseAccountService) exchange.getAccountService();
        this.tradeService = (DaseTradeService) exchange.getTradeService();
        this.state = DownloadState.from(downloadState);
    }

    public String serializeState() {
        return state.serialize();
    }

    public List<UserTrade> downloadTrades() {
        final List<UserTrade> result = new ArrayList<>(Math.min(1_000, MAX_TRADES_PER_RUN));

        final String lastTradeId = state.getLastTradeId();

        String before = null;
        boolean firstPage = true;
        boolean reachedLast = false;

        while (true) {
            OpenOrders page;
            try {
                page = tradeService.getFilledOrders(before);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            List<LimitOrder> orders = page.getOpenOrders();
            if (orders == null || orders.isEmpty()) {
                break;
            }

            if (firstPage) {
                String newestId = orders.get(0).getId();
                if (newestId != null && !newestId.isBlank()) {
                    state.setNewTradeId(newestId);
                }
                firstPage = false;
            }

            for (LimitOrder o : orders) {
                if (lastTradeId != null && lastTradeId.equals(o.getId())) {
                    reachedLast = true;
                    break;
                }

                result.add(adaptTrade(o));

                if (result.size() >= MAX_TRADES_PER_RUN) {
                    break;
                }
            }

            if (reachedLast || result.size() == MAX_TRADES_PER_RUN) {
                break;
            }

            String nextBefore = orders.get(orders.size() - 1).getId();
            if (nextBefore == null || nextBefore.equals(before)) {
                break;
            }

            before = nextBefore;
        }

        result.sort(
            Comparator
                .comparing(UserTrade::getTimestamp, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(UserTrade::getId, Comparator.nullsLast(Comparator.naturalOrder()))
                .reversed()
        );

        return result;
    }

    public List<FundingRecord> downloadFundings() {
        final List<FundingRecord> result = new ArrayList<>();

        final String lastDepCursor = state.getLastDepositTs();
        final String lastWdrCursor = state.getLastWithdrawalTs();

        final int limit = 100;
        String before = null;

        boolean firstPage = true;
        boolean reachedLast = false;

        int pages = 0;
        final int maxPages = 200;

        while (pages++ < maxPages) {
            ApiGetAccountTxnsOutput resp;
            try {
                resp = accountService.getAccountTransactions(limit, before);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            List<ApiAccountTxn> txns = (resp == null) ? null : resp.getTransactions();
            if (txns == null || txns.isEmpty()) {
                break;
            }

            if (firstPage) {
                String newestId = txns.get(0).getId();
                if (newestId != null && !newestId.isBlank()) {
                    state.setNewDepositTs(newestId);
                    state.setNewWithdrawalTs(newestId);
                }
                firstPage = false;
            }

            for (ApiAccountTxn t : txns) {
                if (t == null) {
                    continue;
                }

                String id = t.getId();
                if (id != null && (id.equals(lastDepCursor) || id.equals(lastWdrCursor))) {
                    reachedLast = true;
                    break;
                }

                FundingRecord fr = adaptFundingRecord(t);
                if (fr != null) {
                    result.add(fr);
                }
            }

            if (reachedLast) {
                break;
            }

            String nextBefore = txns.get(txns.size() - 1).getId();
            if (nextBefore == null || nextBefore.isBlank() || nextBefore.equals(before)) {
                break;
            }
            before = nextBefore;
        }

        result.sort(Comparator.comparing(FundingRecord::getDate, Comparator.nullsLast(Comparator.naturalOrder())).reversed());

        return result;
    }

    private UserTrade adaptTrade(LimitOrder order) {
        Order.OrderType type = order.getType();
        Instrument instrument = order.getInstrument();

        return new UserTrade(
            type,
            order.getOriginalAmount(),
            instrument,
            order.getAveragePrice(),
            order.getTimestamp(),
            order.getId(),
            order.getId(),
            null,
            null,
            null
        );
    }

    private static FundingRecord adaptFundingRecord(ApiAccountTxn t) {
        if (t == null) {
            return null;
        }

        Currency currency = t.getCurrency() == null ? null : Currency.getInstance(t.getCurrency());
        Date date = new Date(t.getCreatedAt());

        FundingRecord.Type type = mapTxnTypeToFundingType(t.getTxnType());
        FundingRecord.Status status = FundingRecord.Status.COMPLETE;
        String description = t.getTxnType();

        return new FundingRecord.Builder()
            .setDate(date)
            .setCurrency(currency)
            .setAmount(t.getAmount())
            .setInternalId(t.getId())
            .setType(type)
            .setStatus(status)
            .setDescription(description)
            .build();
    }

    private static FundingRecord.Type mapTxnTypeToFundingType(String txnType) {
        if (txnType == null) {
            throw new IllegalArgumentException("txnType is null");
        }
        return switch (txnType) {
            case "deposit" -> FundingRecord.Type.DEPOSIT;
            case "withdrawal_commit" -> FundingRecord.Type.WITHDRAWAL;
            case "withdrawal_block" -> FundingRecord.Type.OTHER_OUTFLOW;
            case "withdrawal_unblock" -> FundingRecord.Type.OTHER_INFLOW;
            case "trade_fill_fee_base", "trade_fill_fee_quote" -> FundingRecord.Type.OTHER_OUTFLOW;
            case "trade_fill_credit_base", "trade_fill_credit_quote" -> FundingRecord.Type.OTHER_INFLOW;
            case "trade_fill_debit_base", "trade_fill_debit_quote" -> FundingRecord.Type.OTHER_OUTFLOW;
            case "portfolio_transfer_credit" -> FundingRecord.Type.INTERNAL_DEPOSIT;
            case "portfolio_transfer_debit" -> FundingRecord.Type.INTERNAL_WITHDRAWAL;
            default -> throw new IllegalArgumentException("Unknown txnType: " + txnType);
        };
    }
}