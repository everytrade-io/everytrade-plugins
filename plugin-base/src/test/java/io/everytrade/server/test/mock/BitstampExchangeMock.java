package io.everytrade.server.test.mock;

import org.knowm.xchange.bitstamp.BitstampUtils;
import org.knowm.xchange.bitstamp.service.BitstampTradeHistoryParams;
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


public class BitstampExchangeMock extends KnowmExchangeMock {
    public BitstampExchangeMock(List<UserTrade> trades, List<FundingRecord> fundingRecords) {
        super(trades, fundingRecords);
    }

    protected TradeService mockTradeService() throws Exception {
        var mock = mock(TradeService.class);

        when(mock.createTradeHistoryParams()).thenReturn(new BitstampTradeHistoryParams(null, BitstampUtils.MAX_TRANSACTIONS_PER_QUERY));

        when(mock.getTradeHistory(any()))
            .thenReturn(
                new UserTrades(trades, Trades.TradeSortType.SortByTimestamp),
                new UserTrades(emptyList(), Trades.TradeSortType.SortByTimestamp)
            );
        return mock;
    }

    protected AccountService mockAccountService() throws Exception {
        var mock = mock(AccountService.class);

        when(mock.createFundingHistoryParams())
            .thenReturn(new BitstampTradeHistoryParams(null, BitstampUtils.MAX_TRANSACTIONS_PER_QUERY));

        when(mock.getFundingHistory(any()))
            .thenReturn(fundingRecords)
            .thenReturn(emptyList());

        return mock;
    }

}
