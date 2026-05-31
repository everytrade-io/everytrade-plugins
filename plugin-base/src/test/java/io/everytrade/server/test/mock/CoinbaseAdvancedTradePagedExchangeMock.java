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
 * Faithful mock of the Coinbase Advanced Trade {@code orders/historical/fills} endpoint used to
 * regression-test the ETD-2134 / ETS-4965 pagination fix. Unlike {@link CoinbaseAdvancedTradeExchangeMock}
 * (which returns everything in a single page), this mock emulates the real API semantics that the bug
 * depended on:
 * <ul>
 *   <li>fills are filtered by {@code end_sequence_timestamp} (a fill is returned only when its
 *       trade_time is &lt;= the requested window end), compared at the FULL microsecond precision; and</li>
 *   <li>pagination advances through the filtered, time-descending result via an opaque {@code cursor}.</li>
 * </ul>
 * With the buggy downloader (which narrowed the window end to the millisecond-truncated trade_time of
 * the last fill on each page) any fill sharing a trade_time that fell just past the truncated boundary
 * is dropped here too - exactly as it was against the live API. With the fix (fixed window + cursor)
 * every fill survives.
 * <p>
 * All data is synthetic - no real org id, e-mails, portfolio names or DB transactions are used.
 */
public class CoinbaseAdvancedTradePagedExchangeMock extends KnowmExchangeMock {

    private static final int PAGE_LIMIT = 100; // mirrors CoinbaseDownloader.TRANSACTIONS_PER_REQUEST_LIMIT

    private final List<CoinbaseAdvancedTradeFills> allFills;

    public CoinbaseAdvancedTradePagedExchangeMock(List<CoinbaseAdvancedTradeFills> allFills) {
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
            Instant end = params.getEndDateTime();
            String cursor = params.getCursor();

            // 1) filter by end_sequence_timestamp at full precision (the truncation bug lives in the caller)
            List<CoinbaseAdvancedTradeFills> eligible = new ArrayList<>();
            for (CoinbaseAdvancedTradeFills f : allFills) {
                if (end == null || !Instant.parse(f.getTradeTime()).isAfter(end)) {
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
