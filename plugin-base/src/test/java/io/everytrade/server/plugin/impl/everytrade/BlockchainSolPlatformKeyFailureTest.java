package io.everytrade.server.plugin.impl.everytrade;

import io.everytrade.server.plugin.api.connector.DownloadResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static io.everytrade.server.model.Currency.USD;
import static io.everytrade.server.plugin.impl.everytrade.FakeHelius.GET_SIGNATURES;
import static io.everytrade.server.plugin.impl.everytrade.FakeHelius.GET_TRANSACTION;
import static io.everytrade.server.plugin.impl.everytrade.FakeHelius.WALLET_HISTORY;
import static io.everytrade.server.plugin.impl.everytrade.FakeHelius.nativeChange;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * ETD-2217 point 9: the SOL connector reads Helius with OUR platform key ({@code helius.api.key}). When that key is
 * invalid or its monthly credits are spent the sync fails with a stable marker the host recognises (no plugin-api
 * change); a per-second rate limit is retried and raises nothing; everything else fails as before. On every failure
 * the sync throws, so the stored cursor is not advanced.
 * <p>
 * The marker contract is spelled out here literally (not read from production code) so that a reworded marker turns
 * this test red: the host matches exactly this text.
 */
class BlockchainSolPlatformKeyFailureTest {

    /** The host-side contract, copied literally. */
    static final Pattern MARKER = Pattern.compile(
        "^Platform API key failure: provider=(HELIUS|ETHERSCAN) reason=(KEY_INVALID|QUOTA_EXHAUSTED)\\.");

    private static final String ADDRESS = "GQPo4rWCpn5MrzgR3UpDbKVVwyjdeFriYu8taSAhTvVU";
    private static final String NATIVE_SOL = "So11111111111111111111111111111111111111111";
    private static final String API_KEY = "secret-platform-key-123";
    private static final long FEE = 5000L;

    private FakeHelius helius;

    @BeforeEach
    void setUp() throws Exception {
        helius = new FakeHelius(ADDRESS);
        helius
            .success("sig-a", FEE, ADDRESS, List.of(nativeChange(NATIVE_SOL, "1.0")))
            .failed("sig-failed", FEE, ADDRESS)
            .success("sig-b", FEE, ADDRESS, List.of(nativeChange(NATIVE_SOL, "-0.1")));
    }

    @AfterEach
    void tearDown() {
        helius.close();
    }

    @Test
    void walletApi401IsKeyInvalidAndIsNotRetried() {
        helius.failNext(1, 401, "{\"jsonrpc\":\"2.0\",\"error\":{\"code\":-32401,\"message\":\"invalid api key provided\"}}");

        final Matcher marker = assertMarker(assertThrows(IllegalStateException.class, () -> download(null)));

        assertEquals("HELIUS", marker.group(1));
        assertEquals("KEY_INVALID", marker.group(2));
        assertEquals(1, helius.requests().size(), "a rejected key does not get better by retrying");
    }

    @Test
    void walletApi429MaxUsageReachedIsQuotaExhaustedAndIsNotRetried() {
        helius.failNext(1, 429, "{\"jsonrpc\":\"2.0\",\"error\":{\"code\":-32429,\"message\":\"max usage reached\"}}");

        final Matcher marker = assertMarker(assertThrows(IllegalStateException.class, () -> download(null)));

        assertEquals("HELIUS", marker.group(1));
        assertEquals("QUOTA_EXHAUSTED", marker.group(2));
        assertEquals(1, helius.requests().size());
    }

    @Test
    void rpc429MaxUsageReachedIsQuotaExhausted() {
        helius.failNextRpc(1, 429, "{\"jsonrpc\":\"2.0\",\"error\":{\"code\":-32429,\"message\":\"max usage reached\"},\"id\":1}");

        final Matcher marker = assertMarker(assertThrows(IllegalStateException.class, () -> download(null)));

        assertEquals("QUOTA_EXHAUSTED", marker.group(2));
    }

    @Test
    void rpc401IsKeyInvalid() {
        helius.failNextRpc(1, 401, "Unauthorized");

        final Matcher marker = assertMarker(assertThrows(IllegalStateException.class, () -> download(null)));

        assertEquals("KEY_INVALID", marker.group(2));
    }

    @Test
    void rpcErrorEnvelopeWithHttp200MaxUsageReachedIsQuotaExhausted() {
        helius.rpcError(-32429, "max usage reached");

        final Matcher marker = assertMarker(assertThrows(IllegalStateException.class, () -> download(null)));

        assertEquals("HELIUS", marker.group(1));
        assertEquals("QUOTA_EXHAUSTED", marker.group(2));
    }

    @Test
    void walletApiRateLimitIsRetriedAndRaisesNoMarker() {
        helius.failNext(1, 429,
            "{\"error\":\"RATE_LIMIT_EXCEEDED\",\"code\":429,\"details\":\"Too many requests. Retry after 2 seconds.\"}");

        final DownloadResult result = download(null);

        assertEquals("sig-b", result.getDownloadStateData());
        assertEquals(1, helius.requests("failure-429").size(), "one rejected attempt");
        assertEquals(1, helius.requests(WALLET_HISTORY).size(), "then the real one");
        assertFalse(result.getParseResult().getTransactionClusters().isEmpty());
    }

    @Test
    void rpcRateLimitEnvelopeWithHttp200IsRetriedAndRaisesNoMarker() {
        helius.failNextRpc(1, 200, "{\"jsonrpc\":\"2.0\",\"error\":{\"code\":-32005,\"message\":\"Too many requests\"},\"id\":\"1\"}");

        final DownloadResult result = download(null);

        assertEquals("sig-b", result.getDownloadStateData());
        assertEquals(1, helius.requests("failure-200").size(), "one rejected attempt");
        assertEquals(1, helius.requests(GET_SIGNATURES).size());
        // the retry got the real answer: two successful rows and the failed one are read once each (D5 + D3)
        assertEquals(3, helius.requests(GET_TRANSACTION).size());
        assertEquals(3, result.getParseResult().getTransactionClusters().size());
    }

    @Test
    void otherClientErrorFailsAsBeforeWithoutMarker() {
        helius.failNext(1, 400, "{\"error\":\"invalid address\"}");

        final IllegalStateException e = assertThrows(IllegalStateException.class, () -> download(null));

        assertNull(findMarker(e), "a 400 is about the request (the user's address), not our key");
    }

    @Test
    void otherRpcErrorFailsAsBeforeWithoutMarker() {
        helius.rpcError(-32602, "Invalid param: WrongSize");

        final IllegalStateException e = assertThrows(IllegalStateException.class, () -> download(null));

        assertNull(findMarker(e));
    }

    @Test
    void markerMessageCarriesNeitherKeyNorAddress() {
        helius.failNext(1, 401, "{\"error\":\"invalid api key " + API_KEY + "\"}");

        final IllegalStateException e = assertThrows(IllegalStateException.class, () -> download(null));

        final Throwable markerException = markerThrowable(e);
        assertNotNull(markerException);
        assertEquals(IOException.class, markerException.getClass(), "the host matches the exact class");
        assertFalse(markerException.getMessage().contains(API_KEY), markerException.getMessage());
        assertFalse(markerException.getMessage().contains(ADDRESS), markerException.getMessage());
    }

    @Test
    void keyFailureOnAnIncrementalSyncLeavesTheStoredCursorAlone() {
        helius.failNext(1, 401, "Unauthorized");

        // the sync throws instead of returning a state; the host keeps "sig-a" as the cursor
        assertMarker(assertThrows(IllegalStateException.class, () -> download("sig-a")));
    }

    // --- helpers ----------------------------------------------------------------------------------------------------

    private DownloadResult download(String state) {
        return new BlockchainSolDownloader(ADDRESS, API_KEY, USD.code(), false, false, helius.client()).download(state);
    }

    static Matcher assertMarker(Throwable thrown) {
        final Matcher m = findMarker(thrown);
        assertNotNull(m, "no platform-key marker in the cause chain: " + chain(thrown));
        return m;
    }

    static Matcher findMarker(Throwable thrown) {
        final Throwable t = markerThrowable(thrown);
        if (t == null) {
            return null;
        }
        final Matcher m = MARKER.matcher(t.getMessage());
        return m.find() ? m : null;
    }

    static Throwable markerThrowable(Throwable thrown) {
        for (Throwable t = thrown; t != null; t = t.getCause() == t ? null : t.getCause()) {
            if (t.getMessage() != null && MARKER.matcher(t.getMessage()).find()) {
                return t;
            }
        }
        return null;
    }

    static List<String> chain(Throwable thrown) {
        final List<String> out = new ArrayList<>();
        for (Throwable t = thrown; t != null; t = t.getCause() == t ? null : t.getCause()) {
            out.add(t.getClass().getSimpleName() + ": " + t.getMessage());
        }
        return out;
    }
}
