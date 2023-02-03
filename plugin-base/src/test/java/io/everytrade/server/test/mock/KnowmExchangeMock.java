package io.everytrade.server.test.mock;

import lombok.SneakyThrows;
import lombok.experimental.FieldDefaults;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.account.FundingRecord;
import org.knowm.xchange.dto.meta.ExchangeMetaData;
import org.knowm.xchange.dto.trade.UserTrade;
import org.knowm.xchange.exceptions.ExchangeException;
import org.knowm.xchange.service.account.AccountService;
import org.knowm.xchange.service.marketdata.MarketDataService;
import org.knowm.xchange.service.trade.TradeService;
import si.mazi.rescu.SynchronizedValueFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static lombok.AccessLevel.PROTECTED;


@FieldDefaults(level = PROTECTED)
public abstract class KnowmExchangeMock implements Exchange {

    List<UserTrade> trades;
    List<FundingRecord> fundingRecords;
    List<FundingRecord> staking = new ArrayList<>();

    TradeService tradeService;
    AccountService accountService;

    @SneakyThrows
    public KnowmExchangeMock(List<UserTrade> trades, List<FundingRecord> fundingRecords) {
        this.trades = trades;
        this.fundingRecords = fundingRecords;
        initMocks();
    }

    @SneakyThrows
    public KnowmExchangeMock(List<FundingRecord> stakingRecords) {
        this.trades = null;
        this.fundingRecords = null;
        this.staking.addAll(stakingRecords);
        initMocks();
    }

    @SneakyThrows
    public KnowmExchangeMock(List<UserTrade> trades, List<FundingRecord> fundingRecords, boolean init) {
        this.trades = trades;
        this.fundingRecords = fundingRecords;
        if (init) {
            initMocks();
        }
    }

    @SneakyThrows
    protected void initMocks() {
        this.tradeService = mockTradeService();
        this.accountService = mockAccountService();
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
    public List<CurrencyPair> getExchangeSymbols() {
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

    @SneakyThrows
    @Override
    public TradeService getTradeService() {
        return tradeService;
    }

    @SneakyThrows
    @Override
    public AccountService getAccountService() {
        return accountService;
    }

    @Override
    public void remoteInit() throws IOException, ExchangeException {

    }

    protected abstract TradeService mockTradeService() throws Exception;

    protected abstract AccountService mockAccountService() throws Exception;
}
