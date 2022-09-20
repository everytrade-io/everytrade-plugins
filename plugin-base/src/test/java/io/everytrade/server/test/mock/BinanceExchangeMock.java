package io.everytrade.server.test.mock;

import org.knowm.xchange.binance.service.BinanceFundingHistoryParams;
import org.knowm.xchange.binance.service.BinanceTradeHistoryParams;
import org.knowm.xchange.binance.service.BinanceTradeService;
import org.knowm.xchange.dto.account.FundingRecord;
import org.knowm.xchange.dto.marketdata.Trades;
import org.knowm.xchange.dto.trade.UserTrade;
import org.knowm.xchange.dto.trade.UserTrades;
import org.knowm.xchange.service.account.AccountService;
import org.knowm.xchange.service.trade.TradeService;

import java.util.ArrayList;
import java.util.List;

import static java.util.Collections.emptyList;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;


public class BinanceExchangeMock extends KnowmExchangeMock {

    List<UserTrade> buySell;
    List<UserTrade> convert;

    public BinanceExchangeMock(List<UserTrade> trades, List<FundingRecord> fundingRecords) {
        super(trades, fundingRecords);
    }

    protected TradeService mockTradeService() throws Exception {
        splitTrades(trades);
        var mock = mock(BinanceTradeService.class);
        var mockConvert = mock(BinanceTradeService.class);
        when(mock.createTradeHistoryParams()).thenReturn(new BinanceTradeHistoryParams());
        when(mock.getTradeHistory(any()))
            .thenReturn(
                new UserTrades(buySell, Trades.TradeSortType.SortByTimestamp),
                new UserTrades(emptyList(), Trades.TradeSortType.SortByTimestamp)
            );
        when(mock.getConvertHistory(any()))
            .thenReturn(
                new UserTrades(convert, Trades.TradeSortType.SortByTimestamp),
                new UserTrades(emptyList(), Trades.TradeSortType.SortByTimestamp)
            );

        return mock;
    }

    private void splitTrades(List<UserTrade> allTrades) {
        List<UserTrade> trades = new ArrayList<>();
        List<UserTrade> convert = new ArrayList<>();
        trades.add(allTrades.get(0));
        trades.add(allTrades.get(1));
        convert.add(allTrades.get(2));
        this.buySell = trades;
        this.convert = convert;
    }

    protected AccountService mockAccountService() throws Exception {
        var mock = mock(AccountService.class);

        when(mock.createFundingHistoryParams())
            .thenReturn(new BinanceFundingHistoryParams());

        when(mock.getFundingHistory(any()))
            .thenReturn(fundingRecords)
            .thenReturn(emptyList());

        return mock;
    }
}
