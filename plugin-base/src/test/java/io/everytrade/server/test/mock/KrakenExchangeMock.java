package io.everytrade.server.test.mock;

import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.dto.account.FundingRecord;
import org.knowm.xchange.dto.marketdata.Trades;
import org.knowm.xchange.dto.trade.UserTrade;
import org.knowm.xchange.dto.trade.UserTrades;
import org.knowm.xchange.kraken.KrakenUtils;
import org.knowm.xchange.kraken.service.KrakenAccountService;
import org.knowm.xchange.kraken.service.KrakenTradeHistoryParams;
import org.knowm.xchange.service.trade.TradeService;

import java.util.List;

import static java.util.Collections.emptyList;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;


public class KrakenExchangeMock extends KnowmExchangeMock {

    public KrakenExchangeMock(List<FundingRecord> stakingRecords) {
        super(stakingRecords);
    }
    public KrakenExchangeMock(List<UserTrade> trades, List<FundingRecord> fundingRecords) {
        super(trades, fundingRecords);
    }

    protected TradeService mockTradeService() throws Exception {
        var mock = mock(TradeService.class);
        when(mock.createTradeHistoryParams()).thenReturn(new KrakenTradeHistoryParams());
        when(mock.getTradeHistory(any()))
            .thenReturn(
                new UserTrades(trades, Trades.TradeSortType.SortByTimestamp),
                new UserTrades(emptyList(), Trades.TradeSortType.SortByTimestamp)
            );
        mockKrakenCurrencies();
        return mock;
    }

    protected void mockKrakenCurrencies() {
        var mockCurrencies = mockStatic(KrakenUtils.class);
        mockCurrencies.when( () -> KrakenUtils.getKrakenCurrencyCode(Currency.BTC)).thenReturn(Currency.BTC.getCurrencyCode());
        mockCurrencies.when( () -> KrakenUtils.getKrakenCurrencyCode(Currency.USD)).thenReturn(Currency.USD.getCurrencyCode());
    }

    protected KrakenAccountService mockAccountService() throws Exception {
        var mock = mock(KrakenAccountService.class);
        when(mock.createFundingHistoryParams())
            .thenReturn(new KrakenAccountService.KrakenFundingHistoryParams(null, null, null, (Currency[]) null));

        when(mock.getFundingHistory(any()))
            .thenReturn(fundingRecords)
            .thenReturn(emptyList());

        when(mock.getStakingHistory())
            .thenReturn(staking)
            .thenReturn(emptyList());
        return mock;
    }
}
