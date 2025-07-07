package io.everytrade.server.plugin.impl.everytrade;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import si.mazi.rescu.RestProxyFactory;

import javax.ws.rs.core.HttpHeaders;
import java.math.BigDecimal;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class EveryTradeApiTest {

    WireMockServer wireMockServer;
    IEveryTradeApi api;

    @BeforeEach
    void setUp() {
        wireMockServer = new WireMockServer(8888);
        wireMockServer.start();
        WireMock.configureFor("localhost", 8888);

        api = RestProxyFactory.createProxy(IEveryTradeApi.class, "http://localhost:8888");
    }

    @AfterEach
    void tearDown() {
        wireMockServer.stop();
    }

    @Test
    void testGetTransactions() {
        String jsonResponse = """
            {
              "header": [],
              "transactions": [
                {
                  "uid": "123",
                  "timestamp": 1722436800,
                  "base": "BTC",
                  "quote": "USD",
                  "action": "BUY",
                  "quantity": 0.5,
                  "volume": 15000,
                  "fee": 10,
                  "feeCurrency": "USD",
                  "rebate": 0,
                  "rebateCurrency": "USD",
                  "addressFrom": "abc",
                  "addressTo": "xyz",
                  "note": "test",
                  "labels": "label1,label2",
                  "partner": "partner1",
                  "reference": "ref123"
                }
              ]
            }
            """;

        stubFor(post(urlEqualTo("/transactions/query"))
            .withHeader("API-Key", equalTo("test-key"))
            .willReturn(aResponse()
                .withHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .withBody(jsonResponse)));

        WhaleBooksApiDto dto = api.getTransactions(
            "test-key",
            (request) -> "mock-signature",
            "0",
            10
        );

        assertNotNull(dto);
        assertEquals(1, dto.getTransactions().size());
        assertEquals("123", dto.getTransactions().get(0).getUid());
        assertEquals(new BigDecimal("15000"), dto.getTransactions().get(0).getVolume());
    }
}