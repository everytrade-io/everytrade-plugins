package io.everytrade.server.plugin.impl.everytrade;

import io.everytrade.server.parser.exchange.GbApiTransactionBean;
import io.everytrade.server.plugin.impl.generalbytes.GbApiDto;
import io.everytrade.server.plugin.impl.generalbytes.GbConnector;
import io.everytrade.server.plugin.impl.generalbytes.IGbApi;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GbConnectorTest {

    String apiKey = "test";
    String apiSecret = "test";
    String url = "http://localhost:5555";

    @Test
    void testIgnoreZeroVolumeAndQuantity() {
        var apiData = getDummyData();
        var mockIGBApi = mock(IGbApi.class);

        when(mockIGBApi.getTransactions(any(), any(), any(), any()))
            .thenReturn(apiData);
        var connector = new GbConnector(url, apiKey, apiSecret);
        connector.setApi(mockIGBApi);
        var result = connector.getTransactions(null);
        assertEquals(2, result.getParseResult().getTransactionClusters().size());
        assertEquals(2, result.getParseResult().getParsingProblems().size());
        assertEquals("Volume or Quantity is zero. ", result.getParseResult().getParsingProblems().get(0).getMessage());
        assertEquals("Volume or Quantity is zero. ", result.getParseResult().getParsingProblems().get(1).getMessage());
    }

    private GbApiDto getDummyData() {
        GbApiDto dataApi = new GbApiDto();
        dataApi.setHeader(getGBApiHeader());
        List<GbApiTransactionBean> txs = new ArrayList<>();
        var tx1 = createDummyTx("BTC", "USD", "BUY", "0.000959","5.00", "0.0000728", "BTC");
        var tx2WrongQuantity = createDummyTx("BTC", "USD", "BUY", "0","5.00", "0.0000728", "BTC");
        var tx3WrongVolume = createDummyTx("BTC", "USD", "BUY", "0.000959","0", "0.0000728", "BTC");
        var tx4 = createDummyTx("BTC", "USD", "BUY", "0.000959","6", "0.0000728", "BTC");
        txs.add(tx1);
        txs.add(tx2WrongQuantity);
        txs.add(tx3WrongVolume);
        txs.add(tx4);
        dataApi.setTransactions(txs);
        return dataApi;
    }

    private String[] getGBApiHeader() {
        return new String[]{"uid", "timestamp", "base", "quote", "action", "quantity",
            "volume", "expense", "expensecurrency", "status", "classification"};
    }

    private GbApiTransactionBean createDummyTx(String base, String quote, String action, String quantity, String volume, String expense,
                                               String expenseCurrency) {
        var tx = new GbApiTransactionBean();
        tx.setUid(UUID.randomUUID().toString());
        tx.setTimestamp(new Date());
        tx.setBase(base);
        tx.setQuote(quote);
        tx.setAction(action);
        tx.setQuantity(new BigDecimal(quantity));
        tx.setVolume(new BigDecimal(volume));
        tx.setExpense(new BigDecimal(expense));
        tx.setExpenseCurrency(expenseCurrency);
        tx.setStatus("COMPLETED (0)");
        return tx;
    }

}
