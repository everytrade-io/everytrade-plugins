package io.everytrade.server.plugin.impl.everytrade.etherscan;

import io.everytrade.server.plugin.impl.everytrade.PlatformKeyFailure.ResponseClass;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Etherscan reasons (the {@code result} of a status-0 answer) per https://docs.etherscan.io/resources/common-error-messages. */
class EtherScanClientClassifyTest {

    @Test
    void classifiesTheDocumentedReasons() {
        assertEquals(ResponseClass.KEY_INVALID, EtherScanClient.classify(200, "Invalid API Key"));
        assertEquals(ResponseClass.KEY_INVALID, EtherScanClient.classify(200, "Missing/Invalid API Key"));
        assertEquals(ResponseClass.KEY_INVALID,
            EtherScanClient.classify(200, "Too many invalid api key attempts, please try again later"));
        assertEquals(ResponseClass.RATE_LIMITED,
            EtherScanClient.classify(200, "Max calls per sec rate limit reached (3/sec)"));
        assertEquals(ResponseClass.RATE_LIMITED,
            EtherScanClient.classify(200, "Max rate limit reached, please use API Key for higher rate limit"));
        assertEquals(ResponseClass.RATE_LIMITED, EtherScanClient.classify(429, "Too Many Requests"));
        assertEquals(ResponseClass.QUOTA_EXHAUSTED, EtherScanClient.classify(200, "Max daily rate limit reached"));
        assertEquals(ResponseClass.QUOTA_EXHAUSTED, EtherScanClient.classify(200,
            "Community Free API limit reached. Resets 2026-06-24 12:00:00 UTC."));
        assertEquals(ResponseClass.OTHER, EtherScanClient.classify(200, "Error! Invalid address format"));
        assertEquals(ResponseClass.OTHER, EtherScanClient.classify(200,
            "Unexpected err, timeout occurred or server too busy. Please try again later"));
        assertEquals(ResponseClass.OTHER, EtherScanClient.classify(200, null));
        assertEquals(ResponseClass.OTHER, EtherScanClient.classify(500, null));
    }
}
