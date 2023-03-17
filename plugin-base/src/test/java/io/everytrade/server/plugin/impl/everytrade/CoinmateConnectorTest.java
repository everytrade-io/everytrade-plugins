package io.everytrade.server.plugin.impl.everytrade;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.everytrade.server.model.CurrencyPair;

import org.junit.jupiter.api.Test;
import org.knowm.xchange.coinmate.dto.trade.CoinmateTransactionHistoryEntry;

import java.util.Collections;
import java.util.List;

import static io.everytrade.server.model.Currency.BTC;
import static io.everytrade.server.model.Currency.USD;

class CoinmateConnectorTest {

    private static final CurrencyPair PAIR = new CurrencyPair(BTC, USD);
    private static final String ADDRESS = "addrs0";

    List<CoinmateTransactionHistoryEntry> coinMateDummyData() throws JsonProcessingException {
        String jsonString = "[{\"transactionType\":\"INSTANT_SELL\",\"amount\":0.39615682,\"priceCurrency\":\"EUR\",\"orderId\"" +
            ":1983035523,\"price\":71.69,\"fee\":0.05112086,\"feeCurrency\":\"EUR\",\"transactionId\":10166112,\"amountCurrency\"" +
            ":\"LTC\",\"status\":\"OK\",\"timestamp\":1670417533892},{\"transactionType\":\"INSTANT_SELL\",\"amount\":1.54854311,\"" +
            "priceCurrency\":\"EUR\",\"orderId\":1983035523,\"price\":71.63,\"fee\":0.19965985,\"feeCurrency\":\"EUR\",\"transactionId\"" +
            ":10166113,\"amountCurrency\":\"LTC\",\"status\":\"OK\",\"timestamp\":1670417533901},{\"transactionType\":\"" +
            "INSTANT_SELL\",\"amount\":7.68935393,\"priceCurrency\":\"EUR\",\"orderId\":1983035523,\"price\":71.61,\"fee\"" +
            ":0.99114234,\"feeCurrency\":\"EUR\",\"transactionId\":10166114,\"amountCurrency\":\"LTC\",\"status\":\"OK\",\"" +
            "timestamp\":1670417533909},{\"transactionType\":\"BUY\",\"amount\":0.62896312,\"priceCurrency\":\"EUR\",\"orderId\"" +
            ":1983058061,\"price\":73.24,\"fee\":0.08291746,\"feeCurrency\":\"EUR\",\"transactionId\":10166142,\"amountCurrency\"" +
            ":\"LTC\",\"status\":\"OK\",\"timestamp\":1670419065734},{\"transactionType\":\"WITHDRAWAL\",\"amount\":0.62896312,\"" +
            "orderId\":0,\"fee\":0.0004,\"description\":\"LTC: 44e48a621f208658ffa89a98516b35fa46e417b11df014fc5c24860f7fc24ba4\"" +
            ",\"feeCurrency\":\"LTC\",\"transactionId\":10166143,\"amountCurrency\":\"LTC\",\"status\":\"COMPLETED\",\"timestamp\"" +
            ":1670419072976}]";
        return Collections.singletonList(new ObjectMapper().readValue(jsonString, CoinmateTransactionHistoryEntry.class));
    }

    @Test
    void testBuySellDepositWithdrawal() throws JsonProcessingException {
        // TODO - test
    }

}
