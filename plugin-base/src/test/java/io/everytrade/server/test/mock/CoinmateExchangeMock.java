package io.everytrade.server.test.mock;

import org.knowm.xchange.coinmate.service.CoinmateAccountService;
import org.knowm.xchange.coinmate.service.CoinmateTradeService;
import org.knowm.xchange.dto.account.FundingRecord;
import org.knowm.xchange.dto.marketdata.Trades;
import org.knowm.xchange.dto.trade.UserTrade;
import org.knowm.xchange.dto.trade.UserTrades;
import org.knowm.xchange.service.account.AccountService;
import org.knowm.xchange.service.trade.TradeService;

import java.util.List;

import static java.util.Collections.emptyList;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;


public class CoinmateExchangeMock extends KnowmExchangeMock {
    public CoinmateExchangeMock(List<UserTrade> trades, List<FundingRecord> fundingRecords) {
        super(trades, fundingRecords);
    }

    protected TradeService mockTradeService() throws Exception {
        var mock = mock(TradeService.class);

        when(mock.createTradeHistoryParams()).thenReturn(new CoinmateTradeService.CoinmateTradeHistoryHistoryParams());

        when(mock.getTradeHistory(any()))
            .thenReturn(
                new UserTrades(trades, Trades.TradeSortType.SortByTimestamp),
                new UserTrades(emptyList(), Trades.TradeSortType.SortByTimestamp)
            );
        return mock;
    }

    protected AccountService mockAccountService() throws Exception {
        var mock = mock(CoinmateAccountService.class);

        CoinmateAccountService.CoinmateFundingHistoryParams value = new CoinmateAccountService.CoinmateFundingHistoryParams();
        when(mock.createFundingHistoryParams())
            .thenReturn(value);

        when(mock.getTransfersHistory(any()))
            .thenReturn(fundingRecords)
            .thenReturn(emptyList());

        return mock;
    }
}
