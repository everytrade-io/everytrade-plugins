package io.everytrade.server.plugin.impl.everytrade;

import io.everytrade.server.plugin.api.connector.DownloadResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.regex.Matcher;

import static io.everytrade.server.plugin.impl.everytrade.BlockchainSolPlatformKeyFailureTest.assertMarker;
import static io.everytrade.server.plugin.impl.everytrade.BlockchainSolPlatformKeyFailureTest.chain;
import static io.everytrade.server.plugin.impl.everytrade.BlockchainSolPlatformKeyFailureTest.findMarker;
import static io.everytrade.server.plugin.impl.everytrade.BlockchainSolPlatformKeyFailureTest.markerThrowable;
import static io.everytrade.server.plugin.impl.everytrade.FakeEtherscan.BLOCK;
import static io.everytrade.server.plugin.impl.everytrade.FakeEtherscan.TXLIST;
import static io.everytrade.server.plugin.impl.everytrade.FakeEtherscan.normal;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ETD-2217 point 9: the ETH connector reads Etherscan with OUR platform key ({@code etherscan.api.token}). Etherscan
 * answers HTTP 200 with {@code {"status":"0","message":"NOTOK","result":"<reason>"}} to a bad key, a spent quota and a
 * per-second rate limit alike (https://docs.etherscan.io/resources/common-error-messages). A bad key and a spent quota
 * fail the sync with the stable marker the host recognises; the per-second limit is retried; anything else (a bad
 * address) fails exactly as before. The sync throws on every failure, so the stored state is not advanced.
 */
class BlockchainEthPlatformKeyFailureTest {

    private static final String WALLET = "0x2a7ee2b9b4ae1b2e5b2a1e0f0e2ad0e6b11c1fd0";
    private static final String STRANGER = "0x9999999999999999999999999999999999999999";
    private static final String API_KEY = "secret-etherscan-token-456";

    private FakeEtherscan etherscan;

    @BeforeEach
    void startFake() throws Exception {
        etherscan = new FakeEtherscan().head(20_000_000L);
        etherscan.add(TXLIST, normal("0xa1", 10_000_000L, STRANGER, WALLET, "1000000000000000000", 21_000L,
            35_000_000_000L, 0));
    }

    @AfterEach
    void stopFake() {
        etherscan.close();
    }

    @Test
    void invalidKeyIsKeyInvalidAndIsNotRetried() {
        for (String result : List.of(
            "Invalid API Key",
            "Missing/Invalid API Key",
            "Invalid API Key (#err2)|1",
            "Too many invalid api key attempts, please try again later"
        )) {
            etherscan.clearRequests();
            etherscan.rejectNext(1, "NOTOK", result);

            final Matcher marker = assertMarker(assertThrows(IllegalStateException.class, () -> download(null)));

            assertEquals("ETHERSCAN", marker.group(1));
            assertEquals("KEY_INVALID", marker.group(2));
            assertEquals(1, etherscan.requests().size(), "a rejected key does not get better by retrying: " + result);
        }
    }

    @Test
    void spentQuotaIsQuotaExhaustedAndIsNotRetried() {
        for (String result : List.of(
            "Max daily rate limit reached",
            "Max daily limit reached. 100000 (100%)",
            "Community Free API limit reached. Resets 2026-06-24 12:00:00 UTC. Upgrade to a paid plan for higher limits."
        )) {
            etherscan.clearRequests();
            etherscan.rejectNext(1, "NOTOK", result);

            final Matcher marker = assertMarker(assertThrows(IllegalStateException.class, () -> download(null)));

            assertEquals("ETHERSCAN", marker.group(1));
            assertEquals("QUOTA_EXHAUSTED", marker.group(2));
            assertEquals(1, etherscan.requests().size());
        }
    }

    @Test
    void keyFailureOnAHistoryPageOfAnIncrementalSyncIsRecognisedAndThrows() {
        // the last-block request passes, the txlist page is refused; the sync throws, so state "9999999;-1;9999999"
        // stays what the host stored
        etherscan.rejectNextOn(TXLIST, 1, "NOTOK", "Max daily rate limit reached");

        final IllegalStateException e = assertThrows(IllegalStateException.class,
            () -> download("9999999;-1;9999999"));

        final Matcher marker = assertMarker(e);
        assertEquals("QUOTA_EXHAUSTED", marker.group(2));
        assertEquals(1, etherscan.requests(BLOCK).size());
        assertEquals(1, etherscan.requests(TXLIST).size());
    }

    @Test
    void perSecondRateLimitIsRetriedAndRaisesNoMarker() {
        for (String result : List.of(
            "Max calls per sec rate limit reached (3/sec)",
            "Max rate limit reached, please use API Key for higher rate limit",
            "Max rate limit reached"
        )) {
            etherscan.clearRequests();
            etherscan.rejectNext(1, "NOTOK", result);

            final DownloadResult downloaded = download(null);

            assertEquals(1, downloaded.getParseResult().getTransactionClusters().size());
            assertEquals(2, etherscan.requests(BLOCK).size(), "one rate-limited attempt, then the real one: " + result);
        }
    }

    @Test
    void aRateLimitThatNeverClearsFailsWithoutMarker() {
        etherscan.rejectNext(50, "NOTOK", "Max calls per sec rate limit reached (3/sec)");

        final IllegalStateException e = assertThrows(IllegalStateException.class, () -> download(null));

        assertNull(findMarker(e), "a transient limit is not a key problem: " + chain(e));
        assertTrue(etherscan.requests(BLOCK).size() > 1, "it was retried");
    }

    @Test
    void aBadAddressFailsAsBeforeWithoutMarker() {
        // the last-block call has no address; a refusal there for a bad address cannot happen, but the text decides
        etherscan.rejectNext(1, "NOTOK", "Error! Invalid address format");

        final IllegalStateException e = assertThrows(IllegalStateException.class, () -> download(null));

        assertNull(findMarker(e), chain(e).toString());
        assertTrue(chain(e).stream().anyMatch(m -> m.contains("Etherscan rejected the request: Error! Invalid address format")),
            "today's readable reason is kept: " + chain(e));
        assertEquals(1, etherscan.requests().size());
    }

    @Test
    void markerMessageCarriesNeitherKeyNorAddress() {
        etherscan.rejectNext(1, "NOTOK", "Invalid API Key " + API_KEY + " for " + WALLET);

        final IllegalStateException e = assertThrows(IllegalStateException.class, () -> download(null));

        final Throwable markerException = markerThrowable(e);
        assertNotNull(markerException, chain(e).toString());
        assertEquals(IOException.class, markerException.getClass(), "the host matches the exact class");
        assertFalse(markerException.getMessage().contains(API_KEY), markerException.getMessage());
        assertFalse(markerException.getMessage().contains(WALLET), markerException.getMessage());
    }

    @Test
    void http429IsARateLimitAndIsRetried() {
        etherscan.failNext(1, 429, "Too Many Requests");

        final DownloadResult downloaded = download(null);

        assertEquals(1, downloaded.getParseResult().getTransactionClusters().size());
    }

    private DownloadResult download(String state) {
        return new BlockchainEthDownloader(WALLET, API_KEY, "USD", false, false, true, false, etherscan.client())
            .download(state);
    }
}
