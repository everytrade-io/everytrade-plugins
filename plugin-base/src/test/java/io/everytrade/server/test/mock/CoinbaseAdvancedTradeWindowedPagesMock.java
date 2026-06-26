package io.everytrade.server.test.mock;

import org.knowm.xchange.coinbase.v2.service.CoinbaseTradeHistoryParams;
import org.knowm.xchange.coinbase.v2.service.CoinbaseTradeService;
import org.knowm.xchange.coinbase.v3.dto.transactions.CoinbaseAdvancedTradeFills;
import org.knowm.xchange.coinbase.v3.dto.transactions.CoinbaseAdvancedTradeOrderFillsResponse;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.dto.account.AccountInfo;
import org.knowm.xchange.dto.account.Balance;
import org.knowm.xchange.dto.account.Wallet;
import org.knowm.xchange.dto.trade.UserTrade;
import org.knowm.xchange.service.account.AccountService;
import org.knowm.xchange.service.trade.TradeService;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Faithful mock of the Coinbase Advanced Trade {@code orders/historical/fills} endpoint that honours the
 * FULL request window - both {@code start_sequence_timestamp} AND {@code end_sequence_timestamp} - plus
 * opaque cursor paging.
 * <p>
 * It exists to regression-test the INCREMENTAL download contract behind the ETS-4965 duplicate complaint
 * ("it re-downloads the same transactions even into a brand-new container"): once a fill has been delivered,
 * a later resume from the persisted state must NOT deliver it again. The first pass runs over {@code [epoch, now]};
 * the second pass runs over {@code [watermark, now]}, so a fill dated before the watermark falls outside the
 * window and never comes back.
 * <p>
 * Distinct from {@link CoinbaseAdvancedTradePagedExchangeMock}, which ignores the start bound (it reproduces the
 * page-boundary truncation bug). Window bounds are inclusive at full (microsecond) precision. All data is
 * synthetic - no real org id, e-mails, portfolio names or DB transactions are used.
 */
public class CoinbaseAdvancedTradeWindowedPagesMock extends KnowmExchangeMock {

    private static final int PAGE_LIMIT = 100; // mirrors CoinbaseDownloader.TRANSACTIONS_PER_REQUEST_LIMIT

    private final List<CoinbaseAdvancedTradeFills> allFills;

    public CoinbaseAdvancedTradeWindowedPagesMock(List<CoinbaseAdvancedTradeFills> allFills) {
        super(new ArrayList<UserTrade>(), new ArrayList<>(), false);
        // newest first, like the real endpoint
        this.allFills = new ArrayList<>(allFills);
        this.allFills.sort(Comparator.comparing((CoinbaseAdvancedTradeFills f) -> Instant.parse(f.getTradeTime())).reversed());
        initMocks();
    }

    @Override
    protected TradeService mockTradeService() throws Exception {
        var mock = mock(CoinbaseTradeService.class);
        when(mock.createTradeHistoryParams()).thenReturn(new CoinbaseTradeHistoryParams());

        when(mock.getAdvancedTradeOrderFillsRow(any())).thenAnswer(invocation -> {
            CoinbaseTradeHistoryParams params = invocation.getArgument(0);
            Instant start = params.getStartDatetime();
            Instant end = params.getEndDateTime();
            String cursor = params.getCursor();

            // 1) filter by the FULL [start, end] window at full precision (both bounds inclusive)
            List<CoinbaseAdvancedTradeFills> eligible = new ArrayList<>();
            for (CoinbaseAdvancedTradeFills f : allFills) {
                Instant t = Instant.parse(f.getTradeTime());
                boolean afterStart = start == null || !t.isBefore(start);
                boolean beforeEnd = end == null || !t.isAfter(end);
                if (afterStart && beforeEnd) {
                    eligible.add(f);
                }
            }

            // 2) advance through the filtered result by the (offset) cursor
            int offset = 0;
            if (cursor != null && !cursor.isEmpty() && !cursor.equalsIgnoreCase("null")) {
                offset = Integer.parseInt(cursor);
            }
            if (offset >= eligible.size()) {
                return new CoinbaseAdvancedTradeOrderFillsResponse(new ArrayList<>(), "");
            }
            int toIndex = Math.min(offset + PAGE_LIMIT, eligible.size());
            List<CoinbaseAdvancedTradeFills> page = new ArrayList<>(eligible.subList(offset, toIndex));
            String nextCursor = toIndex < eligible.size() ? String.valueOf(toIndex) : "";
            return new CoinbaseAdvancedTradeOrderFillsResponse(page, nextCursor);
        });
        return mock;
    }

    @Override
    protected AccountService mockAccountService() throws Exception {
        var mock = mock(AccountService.class);
        when(mock.getAccountInfo())
            .thenReturn(
                new AccountInfo(Wallet.Builder
                    .from(List.of(Balance.zero(Currency.BTC)))
                    .id("00000000-0000-0000-0000-000000000000")
                    .build())
            );
        return mock;
    }
}
