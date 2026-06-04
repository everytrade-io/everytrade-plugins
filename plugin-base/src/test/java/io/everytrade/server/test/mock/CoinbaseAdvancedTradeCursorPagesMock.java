package io.everytrade.server.test.mock;

import org.knowm.xchange.coinbase.v2.service.CoinbaseTradeHistoryParams;
import org.knowm.xchange.coinbase.v2.service.CoinbaseTradeService;
import org.knowm.xchange.coinbase.v3.dto.transactions.CoinbaseAdvancedTradeOrderFillsResponse;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.dto.account.AccountInfo;
import org.knowm.xchange.dto.account.Balance;
import org.knowm.xchange.dto.account.Wallet;
import org.knowm.xchange.dto.trade.UserTrade;
import org.knowm.xchange.service.account.AccountService;
import org.knowm.xchange.service.trade.TradeService;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Exchange mock that returns a caller-supplied SEQUENCE of Advanced Trade fill pages (each with its own
 * cursor) from {@code getAdvancedTradeOrderFillsRow}, so the cursor-driven paging in
 * {@link io.everytrade.server.plugin.impl.everytrade.CoinbaseDownloader} can be exercised with exact,
 * hand-built cursor scenarios (empty cursor = last page; a non-advancing cursor = malformed source).
 * Unlike {@link CoinbaseAdvancedTradePagedExchangeMock} it does not emulate end_sequence_timestamp
 * filtering - it simply hands back the pages as given. No API key, no network.
 */
public class CoinbaseAdvancedTradeCursorPagesMock extends KnowmExchangeMock {

    private final List<CoinbaseAdvancedTradeOrderFillsResponse> fillPages;

    public CoinbaseAdvancedTradeCursorPagesMock(List<CoinbaseAdvancedTradeOrderFillsResponse> fillPages) {
        // skip the base init so fillPages is assigned before the mocks are built
        super(new ArrayList<UserTrade>(), new ArrayList<>(), false);
        this.fillPages = fillPages;
        initMocks();
    }

    @Override
    protected TradeService mockTradeService() throws Exception {
        var mock = mock(CoinbaseTradeService.class);
        when(mock.createTradeHistoryParams()).thenReturn(new CoinbaseTradeHistoryParams());

        var first = fillPages.get(0);
        var rest = fillPages.subList(1, fillPages.size())
            .toArray(new CoinbaseAdvancedTradeOrderFillsResponse[0]);
        // Each call returns the next page; once exhausted Mockito keeps returning the last one
        // (used by the "stuck cursor" test, where the same non-advancing cursor is returned repeatedly).
        when(mock.getAdvancedTradeOrderFillsRow(any())).thenReturn(first, rest);
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
