package io.everytrade.server.test.mock;

import lombok.experimental.FieldDefaults;
import org.knowm.xchange.bitmex.service.BitmexTradeHistoryParams;
import org.knowm.xchange.bittrex.dto.account.BittrexDepositHistory;
import org.knowm.xchange.bittrex.dto.account.BittrexWithdrawalHistory;
import org.knowm.xchange.bittrex.service.BittrexAccountService;
import org.knowm.xchange.currency.Currency;
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
import static lombok.AccessLevel.PRIVATE;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;


@FieldDefaults(level = PRIVATE, makeFinal = true)
public class BittrexExchangeMock extends KnowmExchangeMock {

    List<BittrexDepositHistory> deposits;
    List<BittrexWithdrawalHistory> withdrawals;

    public BittrexExchangeMock(List<UserTrade> trades, List<BittrexDepositHistory> deposits, List<BittrexWithdrawalHistory> withdrawals) {
        super(trades, emptyList(), false);
        this.deposits = deposits;
        this.withdrawals = withdrawals;
        initMocks();
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
        var mock = mock(BittrexAccountService.class);

        when(mock.getBittrexDepositsClosed(nullable(String.class), nullable(String.class), nullable(String.class), anyInt()))
            .thenReturn(deposits);

        when(mock.getBittrexWithdrawalsClosed(nullable(String.class), nullable(String.class), nullable(String.class), anyInt()))
            .thenReturn(withdrawals);

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
