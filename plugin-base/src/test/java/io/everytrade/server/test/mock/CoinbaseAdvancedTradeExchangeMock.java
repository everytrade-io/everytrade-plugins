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

import java.util.ArrayList;
import java.util.List;

import static java.util.Collections.emptyList;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Mock exchange used to exercise the Coinbase Advanced Trade fills download path of
 * {@link io.everytrade.server.plugin.impl.everytrade.CoinbaseDownloader}.
 * <p>
 * All data is synthetic - the fills passed in are returned by the (final) {@link CoinbaseTradeService}
 * via {@code getAdvancedTradeOrderFillsRow(...)}. The first call returns the provided fills (with a
 * {@code null} cursor), every subsequent call returns an empty block so the downloader's paging loop
 * terminates (a block smaller than the page limit ends the loop).
 */
public class CoinbaseAdvancedTradeExchangeMock extends KnowmExchangeMock {

    private final List<CoinbaseAdvancedTradeFills> advancedTradeFills;

    public CoinbaseAdvancedTradeExchangeMock(List<CoinbaseAdvancedTradeFills> advancedTradeFills) {
        super(new ArrayList<UserTrade>(), new ArrayList<>(), false);
        this.advancedTradeFills = advancedTradeFills;
        initMocks();
    }

    @Override
    protected TradeService mockTradeService() throws Exception {
        var mock = mock(CoinbaseTradeService.class);

        when(mock.createTradeHistoryParams()).thenReturn(new CoinbaseTradeHistoryParams());

        // First call returns all synthetic fills, following calls return an empty block to stop paging.
        when(mock.getAdvancedTradeOrderFillsRow(any()))
            .thenReturn(
                new CoinbaseAdvancedTradeOrderFillsResponse(advancedTradeFills, null),
                new CoinbaseAdvancedTradeOrderFillsResponse(emptyList(), null)
            );
        return mock;
    }

    @Override
    protected AccountService mockAccountService() throws Exception {
        var mock = mock(AccountService.class);

        // 36-char synthetic wallet id (matches REAL_WALLET_ID_LENGTH in the downloader).
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
