package io.everytrade.server.test.mock;

import lombok.SneakyThrows;
import lombok.experimental.FieldDefaults;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.dto.account.FundingRecord;
import org.knowm.xchange.dto.meta.ExchangeMetaData;
import org.knowm.xchange.dto.trade.UserTrade;
import org.knowm.xchange.dto.trade.UserTrades;
import org.knowm.xchange.exceptions.ExchangeException;
import org.knowm.xchange.huobi.service.HuobiFundingHistoryParams;
import org.knowm.xchange.huobi.service.HuobiTradeHistoryParams;
import org.knowm.xchange.huobi.service.HuobiTradeService;
import org.knowm.xchange.instrument.Instrument;
import org.knowm.xchange.service.account.AccountService;
import org.knowm.xchange.service.marketdata.MarketDataService;
import org.knowm.xchange.service.trade.TradeService;
import si.mazi.rescu.SynchronizedValueFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static lombok.AccessLevel.PRIVATE;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@FieldDefaults(level = PRIVATE, makeFinal = true)
public class HuobiExchangeMock extends KnowmExchangeMock {

    public HuobiExchangeMock(List<UserTrade> trades, List<FundingRecord> fundingRecords) {
        super(trades, fundingRecords);

    }
    public HuobiExchangeMock(UserTrades trades, List<FundingRecord> fundingRecords) {
        super(trades, fundingRecords);
    }

    @Override
    public ExchangeSpecification getExchangeSpecification() {
        return null;
    }

    @Override
    public ExchangeMetaData getExchangeMetaData() {
        return null;
    }

    @Override
    public List<Instrument> getExchangeInstruments() {
        return null;
    }

    @Override
    public SynchronizedValueFactory<Long> getNonceFactory() {
        return null;
    }

    @Override
    public ExchangeSpecification getDefaultExchangeSpecification() {
        return null;
    }

    @Override
    public void applySpecification(ExchangeSpecification exchangeSpecification) {

    }

    @Override
    public MarketDataService getMarketDataService() {
        return null;
    }

    @Override
    public void remoteInit() throws IOException, ExchangeException {

    }

    @Override
    protected TradeService mockTradeService() throws Exception {
        var mock = mock(HuobiTradeService.class);
        when(mock.createTradeHistoryParams()).thenReturn(new HuobiTradeHistoryParams());
        when(mock.getTradeHistory(any(), anyString())).thenReturn(trade);
        return mock;
    }

    @SneakyThrows
    protected AccountService mockAccountService() {
        var mock = mock(AccountService.class);

        when(mock.createFundingHistoryParams()).thenReturn(new HuobiFundingHistoryParams(null, null, null));

        when(mock.getFundingHistory(any()))
            .thenReturn(fundingRecords)
            .thenReturn(new ArrayList<>());

        return mock;
    }
}
