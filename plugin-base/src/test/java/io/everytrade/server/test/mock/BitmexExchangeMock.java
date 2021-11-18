package io.everytrade.server.test.mock;

import org.knowm.xchange.bitmex.service.BitmexTradeHistoryParams;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.dto.account.FundingRecord;
import org.knowm.xchange.dto.marketdata.Trades;
import org.knowm.xchange.dto.meta.CurrencyMetaData;
import org.knowm.xchange.dto.meta.ExchangeMetaData;
import org.knowm.xchange.dto.trade.UserTrade;
import org.knowm.xchange.dto.trade.UserTrades;
import org.knowm.xchange.service.account.AccountService;
import org.knowm.xchange.service.trade.TradeService;

import java.util.List;
import java.util.Map;

import static java.math.BigDecimal.ZERO;
import static java.util.Collections.emptyList;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;


public class BitmexExchangeMock extends KnowmExchangeMock {
    public BitmexExchangeMock(List<UserTrade> trades, List<FundingRecord> fundingRecords) {
        super(trades, fundingRecords);
    }

    private ExchangeMetaData exchangeMetaData = mockExchangeMetaData();

    @Override
    public ExchangeMetaData getExchangeMetaData() {
        return exchangeMetaData;
    }

    protected TradeService mockTradeService() throws Exception {
        var mock = mock(TradeService.class);

        when(mock.createTradeHistoryParams()).thenReturn(new BitmexTradeHistoryParams());

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
            .thenReturn(new BitmexTradeHistoryParams());

        when(mock.getFundingHistory(any()))
            .thenReturn(fundingRecords)
            .thenReturn(emptyList());

        return mock;
    }

    protected ExchangeMetaData mockExchangeMetaData() {
        var mock = mock(ExchangeMetaData.class);

        when(mock.getCurrencies())
            .thenReturn(Map.of(
                Currency.USD, new CurrencyMetaData(0, ZERO),
                Currency.BTC, new CurrencyMetaData(0, ZERO)
            ));

        return mock;
    }
}
