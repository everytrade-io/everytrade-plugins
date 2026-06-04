package io.everytrade.server.test.mock;

import org.knowm.xchange.coinbase.v2.service.CoinbaseTradeHistoryParams;
import org.knowm.xchange.coinbase.v2.service.CoinbaseTradeService;
import org.knowm.xchange.coinbase.v3.dto.transactions.CoinbaseAdvancedTradeOrderFillsResponse;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.dto.account.AccountInfo;
import org.knowm.xchange.dto.account.Balance;
import org.knowm.xchange.dto.account.Wallet;
import org.knowm.xchange.service.account.AccountService;
import org.knowm.xchange.service.trade.TradeService;

import java.util.List;

import static java.util.Collections.emptyList;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Exchange mock that feeds a fixed sequence of Advanced Trade fill pages to the connector so the
 * cursor-based pagination in {@code CoinbaseDownloader.downloadAdvancedTrade} can be tested without
 * any real API key or network. The V2 trades / funding paths are intentionally left unmocked so they
 * fail soft and every produced cluster originates from the advanced-trade fills under test.
 */
public class CoinbaseAdvancedTradeExchangeMock extends KnowmExchangeMock {

    private final List<CoinbaseAdvancedTradeOrderFillsResponse> fillPages;

    public CoinbaseAdvancedTradeExchangeMock(List<CoinbaseAdvancedTradeOrderFillsResponse> fillPages) {
        // skip the base init so fillPages is assigned before the mocks are built
        super(emptyList(), emptyList(), false);
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
        // Mockito returns each page in turn, then keeps returning the last one (handy for the
        // "stuck cursor" guard test, where the same page is returned on every call).
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
                    .id("89eefb19-c55a-44cb-8a11-8679078d9bf2")
                    .build())
            );
        return mock;
    }
}
