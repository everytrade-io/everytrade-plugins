package io.everytrade.server.test.mock;

import org.knowm.xchange.coinbase.v2.dto.account.CoinbaseExpandTransactionsResponse;
import org.knowm.xchange.coinbase.v2.dto.account.transactions.CoinbaseShowTransactionV2;
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
import org.mockito.Answers;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;

import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;


public class CoinbaseExchangeMock extends KnowmExchangeMock {

    public List<CoinbaseShowTransactionV2> coinbaseTrades;

    public CoinbaseExchangeMock(List<UserTrade> trades, List<FundingRecord> fundingRecords) {
        this(trades, fundingRecords, new ArrayList<>());
    }

    public CoinbaseExchangeMock(List<UserTrade> trades, List<FundingRecord> fundingRecords, List<CoinbaseShowTransactionV2> coinbaseTrades) {
        super(trades, fundingRecords);
    }

    @Override
    protected TradeService mockTradeService() throws Exception {
        var mock = mock(CoinbaseTradeService.class);
        when(mock.createTradeHistoryParams()).thenReturn(new CoinbaseTradeHistoryParams());

        mockAdvancedTrade(mock);

        return mock;
    }

    protected void mockAdvancedTrade(CoinbaseTradeService mock) throws Exception {
        // Mock pro Advanced Trading (Legacy)
        var advResponse = mock(org.knowm.xchange.coinbase.v3.dto.transactions.CoinbaseAdvancedTradeOrderFillsResponse.class);
        when(advResponse.getFills()).thenReturn(new ArrayList<>());
        when(advResponse.getCursor()).thenReturn(null);

        when(mock.getAdvancedTradeOrderFillsRow(any())).thenReturn(advResponse);
    }

    @Override
    protected AccountService mockAccountService() throws Exception {
        var mock = mock(org.knowm.xchange.coinbase.v2.service.CoinbaseAccountService.class, Answers.RETURNS_DEEP_STUBS);

        mockWallets(mock);
        mockExpandTransactions(mock);

        return mock;
    }

    protected void mockWallets(org.knowm.xchange.coinbase.v2.service.CoinbaseAccountService mock) throws Exception {
        // Mockování getAccountInfo pro getWalletIds()
        var walletId = "89eefb19-c55a-44cb-8a11-8679078d9bf2";
        var accountInfo = mock(AccountInfo.class, Answers.RETURNS_DEEP_STUBS);
        var wallets = new java.util.HashMap<String, Wallet>();

        // Peněženka pro testy (musí mít 36 znaků)
        var wallet = mock(Wallet.class, Answers.RETURNS_DEEP_STUBS);
        when(wallet.getId()).thenReturn(walletId);
        wallets.put(walletId, wallet);

        when(accountInfo.getWallets()).thenReturn(wallets);
        when(mock.getAccountInfo()).thenReturn(accountInfo);
    }

    protected void mockExpandTransactions(org.knowm.xchange.coinbase.v2.service.CoinbaseAccountService mock) throws Exception {
        // Mockování getExpandTransactions pro downloadTrades
        CoinbaseExpandTransactionsResponse response = mock(CoinbaseExpandTransactionsResponse.class, Answers.RETURNS_DEEP_STUBS);

        when(response.getPagination().getNextUri()).thenReturn(null);
        when(response.getData()).thenReturn(coinbaseTrades);

        when(mock.getExpandTransactions(anyString(), any(), anyString())).thenReturn(response);
    }

    private List<UserTrade> filterTrades(Order.OrderType type) {
        return trades.stream()
            .filter(it -> it.getType() == type)
            .collect(toList());
    }
}
