package io.everytrade.server.plugin.impl.everytrade.helius;

import io.everytrade.server.plugin.impl.everytrade.PlatformKeyFailure.ResponseClass;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Helius answers classified by status and body (docs: helius.dev common-error-codes and billing/rate-limits). */
class HeliusClientClassifyTest {

    @Test
    void classifiesHttpAnswers() {
        assertEquals(ResponseClass.KEY_INVALID, HeliusClient.classify(401, "<empty>"));
        assertEquals(ResponseClass.QUOTA_EXHAUSTED, HeliusClient.classify(429,
            "{\"jsonrpc\":\"2.0\",\"error\":{\"code\":-32429,\"message\":\"max usage reached\"}}"));
        assertEquals(ResponseClass.QUOTA_EXHAUSTED, HeliusClient.classify(429, "{\"error\": {\"code\": -32429}}"));
        assertEquals(ResponseClass.QUOTA_EXHAUSTED, HeliusClient.classify(429, "Max usage reached"));
        assertEquals(ResponseClass.RATE_LIMITED, HeliusClient.classify(429,
            "{\"error\":\"RATE_LIMIT_EXCEEDED\",\"code\":429,\"details\":\"Too many requests. Retry after 2 seconds.\"}"));
        assertEquals(ResponseClass.OTHER, HeliusClient.classify(400, "{\"error\":\"invalid address\"}"));
        assertEquals(ResponseClass.OTHER, HeliusClient.classify(403, "Forbidden"));
        assertEquals(ResponseClass.OTHER, HeliusClient.classify(500, "<empty>"));
    }

    @Test
    void classifiesJsonRpcErrorEnvelopes() {
        assertEquals(ResponseClass.QUOTA_EXHAUSTED,
            HeliusClient.classifyRpcError(new HeliusRpcErrorDto(-32429, "max usage reached")));
        assertEquals(ResponseClass.RATE_LIMITED,
            HeliusClient.classifyRpcError(new HeliusRpcErrorDto(-32005, "Too many requests")));
        assertEquals(ResponseClass.OTHER,
            HeliusClient.classifyRpcError(new HeliusRpcErrorDto(-32602, "Invalid param: WrongSize")));
        assertEquals(ResponseClass.OTHER, HeliusClient.classifyRpcError(null));
    }
}
