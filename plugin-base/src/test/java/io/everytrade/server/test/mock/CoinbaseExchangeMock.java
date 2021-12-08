package io.everytrade.server.test.mock;

import org.knowm.xchange.coinbase.v2.service.CoinbaseTradeHistoryParams;
import org.knowm.xchange.coinbase.v2.service.CoinbaseTradeService;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.account.AccountInfo;
import org.knowm.xchange.dto.account.Balance;
import org.knowm.xchange.dto.account.FundingRecord;
import org.knowm.xchange.dto.account.Wallet;
import org.knowm.xchange.dto.marketdata.Trades;
import org.knowm.xchange.dto.trade.UserTrade;
import org.knowm.xchange.dto.trade.UserTrades;
import org.knowm.xchange.service.account.AccountService;
import org.knowm.xchange.service.trade.TradeService;

import java.util.List;

import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;


public class CoinbaseExchangeMock extends KnowmExchangeMock {
    public CoinbaseExchangeMock(List<UserTrade> trades, List<FundingRecord> fundingRecords) {
        super(trades, fundingRecords);
    }

    protected TradeService mockTradeService() throws Exception {
        var mock = mock(CoinbaseTradeService.class);

        when(mock.createTradeHistoryParams()).thenReturn(new CoinbaseTradeHistoryParams());

        when(mock.getBuyTradeHistory(any(), any()))
            .thenReturn(
                new UserTrades(filterTrades(Order.OrderType.BID), Trades.TradeSortType.SortByTimestamp),
                new UserTrades(emptyList(), Trades.TradeSortType.SortByTimestamp)
            );

        when(mock.getSellTradeHistory(any(), any()))
            .thenReturn(
                new UserTrades(filterTrades(Order.OrderType.ASK), Trades.TradeSortType.SortByTimestamp),
                new UserTrades(emptyList(), Trades.TradeSortType.SortByTimestamp)
            );
        return mock;
    }

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

    private List<UserTrade> filterTrades(Order.OrderType type) {
        return trades.stream()
            .filter(it -> it.getType() == type)
            .collect(toList());
    }
}
