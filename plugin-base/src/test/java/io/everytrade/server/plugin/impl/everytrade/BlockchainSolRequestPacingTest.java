package io.everytrade.server.plugin.impl.everytrade;

import io.everytrade.server.plugin.api.connector.DownloadResult;
import io.everytrade.server.plugin.impl.everytrade.helius.HeliusClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static io.everytrade.server.plugin.impl.everytrade.FakeHelius.GET_SIGNATURES;
import static io.everytrade.server.plugin.impl.everytrade.FakeHelius.GET_TRANSACTION;
import static io.everytrade.server.plugin.impl.everytrade.FakeHelius.WALLET_HISTORY;
import static io.everytrade.server.plugin.impl.everytrade.FakeHelius.nativeChange;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * OWNER DECISION 2026-10-20: the pacing of the Helius JSON-RPC calls ({@code getSignaturesForAddress},
 * {@code getTransaction}) is shortened; the pacing of the Wallet API history stays at 500 ms.
 * <p>
 * Helius rate limits (https://www.helius.dev/docs/billing/rate-limits, read 2026-10-20): RPC 10 / 50 / 200 / 500
 * requests per second on Free / Developer / Business / Professional; "All Wallet API Endpoints" 2 / 10 / 50 / 100 per
 * second. The default RPC interval of 125 ms (8/s) stays under the FREE limit, and the limiter is JVM-wide, so
 * several SOL connections syncing at once never exceed it together.
 * <p>
 * The arrivals are measured where the requests reach a {@link FakeHelius}. The pacer enforces only a MINIMUM
 * start-to-start spacing on a fixed slot grid; whatever the client does after its slot (cold JIT, (de)serialisation, GC,
 * a busy CPU) lands in the measured gap, so a single gap proves nothing on a loaded machine. The RPC pacing of a sync is
 * therefore asserted on the whole span of its arrivals.
 */
class BlockchainSolRequestPacingTest {

    private static final String ADDRESS = "GQPo4rWCpn5MrzgR3UpDbKVVwyjdeFriYu8taSAhTvVU";
    private static final String COUNTERPARTY = "9H883zHik5LmR72aWEo1e8uYgfyBQ1fLPzT7nHQkzhvA";
    private static final String NATIVE_SOL = "So11111111111111111111111111111111111111111";
    private static final String API_KEY = "test-api-key";

    /** The RPC interval of the JVM-wide pacer (default, no system property in tests). */
    private static final long RPC_INTERVAL_MS = 125;
    /** The old shared pacing that applied to the RPC calls too, before the owner decision of 2026-10-20. */
    private static final long OLD_PACING_MS = 500;
    /**
     * Allowed shortfall of a span below its slot grid: the FIRST arrival may be late after its slot (cold client) while
     * the last one is on time. Two intervals; an unpaced sync of 12 local requests takes a few tens of ms.
     */
    private static final long SPAN_TOLERANCE_MS = 2 * RPC_INTERVAL_MS;
    /** The RPC interval is 125 ms; a gap is never allowed below this (25 ms of measurement jitter). */
    private static final long RPC_MIN_GAP_MS = 100;
    /** The Wallet API pacing is unchanged: 500 ms before each history page (20 ms of jitter allowed). */
    private static final long WALLET_MIN_GAP_MS = 480;
    /** FREE plan RPC limit: 10 requests per second. */
    private static final int FREE_RPC_PER_SECOND = 10;

    private FakeHelius helius;

    @BeforeEach
    void setUp() throws Exception {
        helius = new FakeHelius(ADDRESS);
    }

    @AfterEach
    void tearDown() {
        helius.close();
    }

    @Test
    void theRpcCallsOfASyncArePacedAtTheShortRpcInterval() {
        for (int i = 0; i < 8; i++) {
            helius.success("sig-in-" + i, 5000L, COUNTERPARTY,
                List.of(nativeChange(NATIVE_SOL, "0.01" + i)));
            if (i % 3 == 0) {
                helius.failed("sig-failed-" + i, 5000L, ADDRESS);
            }
        }

        downloader().download(null);

        final List<FakeHelius.Request> rpc = helius.requests().stream()
            .filter(r -> GET_TRANSACTION.equals(r.method) || GET_SIGNATURES.equals(r.method))
            .collect(Collectors.toList());
        // 8 getTransaction of the successful rows + 1 getSignaturesForAddress + 3 getTransaction of failed ones.
        assertEquals(12, rpc.size(), "RPC calls of the sync");
        final long spanMs = TimeUnit.NANOSECONDS.toMillis(rpc.get(rpc.size() - 1).receivedNanos - rpc.get(0).receivedNanos);
        final int steps = rpc.size() - 1;
        // Paced: the slots of n calls lie at least (n - 1) intervals apart, whatever the client does in between.
        final long minSpanMs = steps * RPC_INTERVAL_MS - SPAN_TOLERANCE_MS;
        assertTrue(spanMs >= minSpanMs, "the " + rpc.size() + " RPC calls must span at least " + minSpanMs
            + " ms (pacing " + RPC_INTERVAL_MS + " ms), measured " + spanMs + " ms, gaps " + gapsMs(rpc));
        // Not the old pacing: midway between the new and the old grid, so 500 ms pacing (11 x 500 = 5500 ms) fails
        // while a slow machine has 2 s of headroom above the 1375 ms grid.
        final long maxSpanMs = steps * (RPC_INTERVAL_MS + OLD_PACING_MS) / 2;
        assertTrue(spanMs <= maxSpanMs, "the " + rpc.size() + " RPC calls must span at most " + maxSpanMs
            + " ms (old pacing " + OLD_PACING_MS + " ms), measured " + spanMs + " ms, gaps " + gapsMs(rpc));
    }

    @Test
    void theWalletApiHistoryKeepsItsFiveHundredMillisecondPacing() {
        helius.success("sig-1", 5000L, COUNTERPARTY, List.of(nativeChange(NATIVE_SOL, "0.01")));
        final BlockchainSolDownloader downloader = downloader();
        final DownloadResult first = downloader.download(null);
        final String state = first.getDownloadStateData();
        helius.clearRequests();

        // Three incremental syncs that find nothing new: one Wallet API page each and no RPC call at all.
        downloader.download(state);
        downloader.download(state);
        downloader.download(state);

        final List<FakeHelius.Request> wallet = helius.requests(WALLET_HISTORY);
        assertEquals(3, wallet.size(), "Wallet API pages");
        assertEquals(wallet.size(), helius.requests().size(), "no RPC call when nothing is new");
        final List<Long> gaps = gapsMs(wallet);
        assertTrue(gaps.stream().allMatch(g -> g >= WALLET_MIN_GAP_MS),
            "every Wallet API gap must stay at least " + WALLET_MIN_GAP_MS + " ms, measured " + gaps);
    }

    @Test
    void theRpcLimitHoldsJvmWideAcrossConcurrentClients() throws Exception {
        for (int i = 0; i < 10; i++) {
            helius.success("sig-" + i, 5000L, COUNTERPARTY, List.of(nativeChange(NATIVE_SOL, "0.01")));
        }
        final int threads = 3;
        final int callsPerThread = 8;
        final ExecutorService pool = Executors.newFixedThreadPool(threads);
        final CountDownLatch start = new CountDownLatch(1);
        try {
            final List<Future<?>> futures = new ArrayList<>();
            for (int t = 0; t < threads; t++) {
                // A client per thread, as each SOL connection builds its own downloader and client.
                final HeliusClient client = helius.client();
                futures.add(pool.submit(() -> {
                    start.await();
                    for (int i = 0; i < callsPerThread; i++) {
                        client.getTransaction("sig-" + (i % 10), API_KEY);
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> f : futures) {
                f.get(60, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        final List<FakeHelius.Request> rpc = helius.requests(GET_TRANSACTION);
        assertEquals(threads * callsPerThread, rpc.size(), "getTransaction calls");
        final List<Long> times = rpc.stream().map(r -> r.receivedNanos).sorted().collect(Collectors.toList());
        final List<Long> gaps = new ArrayList<>();
        for (int i = 1; i < times.size(); i++) {
            gaps.add(TimeUnit.NANOSECONDS.toMillis(times.get(i) - times.get(i - 1)));
        }
        assertTrue(gaps.stream().allMatch(g -> g >= RPC_MIN_GAP_MS),
            "concurrent clients share one RPC pace; every gap must be at least " + RPC_MIN_GAP_MS
                + " ms, measured " + gaps);
        // Any window of one second holds at most the FREE plan's 10 requests.
        for (int i = 0; i < times.size(); i++) {
            final long windowEnd = times.get(i) + TimeUnit.SECONDS.toNanos(1);
            final int from = i;
            final long inWindow = times.stream().skip(from).filter(t -> t < windowEnd).count();
            assertTrue(inWindow <= FREE_RPC_PER_SECOND,
                inWindow + " RPC requests within one second from request " + i + ", gaps " + gaps);
        }
    }

    // --- transient RPC answers are retried through the pacer (DEFECT A and B, 2026-10-22) ---------------------------

    @Test
    void anEmptyRpcBodyIsRetriedAndTheSyncSucceeds() {
        seedSmallHistory();
        // rescu 2.1.0 maps an HTTP 200 with an empty body to a null response object
        helius.emptyRpcBodyNext(1);

        final DownloadResult result = downloader().download(null);

        assertEquals("sig-b", result.getDownloadStateData());
        assertEquals(1, helius.requests("empty-200").size(), "one empty answer");
        assertEquals(3, result.getParseResult().getTransactionClusters().size());
    }

    @Test
    void anEmptyRpcBodyOfGetSignaturesIsRetried() throws Exception {
        seedSmallHistory();
        helius.emptyRpcBodyNext(1);

        final var page = helius.client().getSignaturesForAddress(ADDRESS, API_KEY, 1000, null, null);

        assertEquals(3, page.size(), "the retry got the real page");
        assertEquals(1, helius.requests("empty-200").size());
        assertEquals(1, helius.requests(GET_SIGNATURES).size());
    }

    @Test
    void transientJsonRpcErrorEnvelopesAreRetriedAndTheSyncSucceeds() {
        seedSmallHistory();
        // -32603 internal error, -32004 / -32016 node unavailable / lagging: transient, a later attempt answers
        helius.failNextRpc(1, 200, rpcErrorBody(-32603, "Internal error"));
        helius.failNextRpc(1, 200, rpcErrorBody(-32004, "Block not available for slot"));
        helius.failNextRpc(1, 200, rpcErrorBody(-32016, "Minimum context slot has not been reached"));

        final DownloadResult result = downloader().download(null);

        assertEquals("sig-b", result.getDownloadStateData());
        assertEquals(3, helius.requests("failure-200").size(), "three rejected attempts");
        assertEquals(3, result.getParseResult().getTransactionClusters().size());
    }

    @Test
    void aNullGetTransactionResultIsRetried() throws Exception {
        seedSmallHistory();
        // a node lagging behind "confirmed" does not know the signature yet
        helius.failNextRpc(1, 200, "{\"jsonrpc\":\"2.0\",\"result\":null,\"id\":1}");

        final var tx = helius.client().getTransaction("sig-a", API_KEY);

        assertNotNull(tx);
        assertEquals(1, helius.requests("failure-200").size());
        assertEquals(1, helius.requests(GET_TRANSACTION).size());
    }

    @Test
    void aPersistentlyNullGetTransactionResultFailsAsNotFoundAfterTheLastAttempt() {
        seedSmallHistory();

        final IOException e = assertThrows(IOException.class,
            () -> helius.client().getTransaction("sig-unknown", API_KEY));

        assertTrue(e.getMessage().contains("sig-unknown not found"), e.getMessage());
        assertTrue(e.getMessage().contains("after 5 attempts"), e.getMessage());
        assertEquals(5, helius.requests(GET_TRANSACTION).size(), "MAX_ATTEMPTS");
    }

    @Test
    void aQuotaEnvelopeIsNeverRetried() {
        seedSmallHistory();
        helius.rpcError(-32429, "max usage reached");

        final IOException e = assertThrows(IOException.class, () -> helius.client().getTransaction("sig-a", API_KEY));

        assertNotNull(PlatformKeyFailure.find(e), "a QUOTA_EXHAUSTED marker: " + e.getMessage());
        assertEquals(1, helius.requests(GET_TRANSACTION).size(), "no retry of spent credits");
    }

    @Test
    void aKeyRejectedEnvelopeIsNeverRetried() {
        seedSmallHistory();
        helius.rpcError(-32401, "invalid api key provided");

        assertThrows(IOException.class, () -> helius.client().getTransaction("sig-a", API_KEY));

        assertEquals(1, helius.requests(GET_TRANSACTION).size(), "a rejected key cannot recover by retrying");
    }

    @Test
    void aMalformedRequestEnvelopeIsNeverRetried() {
        seedSmallHistory();
        // JSON-RPC 2.0 -32602 invalid params: the same request gets the same answer
        helius.rpcError(-32602, "Invalid param: WrongSize");

        final IOException e = assertThrows(IOException.class,
            () -> helius.client().getSignaturesForAddress(ADDRESS, API_KEY, 1000, null, null));

        assertNull(PlatformKeyFailure.find(e));
        assertEquals(1, helius.requests(GET_SIGNATURES).size());
    }

    private void seedSmallHistory() {
        helius.success("sig-a", 5000L, ADDRESS, List.of(nativeChange(NATIVE_SOL, "1.0")))
            .failed("sig-failed", 5000L, ADDRESS)
            .success("sig-b", 5000L, ADDRESS, List.of(nativeChange(NATIVE_SOL, "-0.1")));
    }

    private static String rpcErrorBody(int code, String message) {
        return "{\"jsonrpc\":\"2.0\",\"error\":{\"code\":" + code + ",\"message\":\"" + message + "\"},\"id\":1}";
    }

    private BlockchainSolDownloader downloader() {
        return new BlockchainSolDownloader(ADDRESS, API_KEY, "USD", false, false, helius.client());
    }

    private static List<Long> gapsMs(List<FakeHelius.Request> requests) {
        final List<Long> gaps = new ArrayList<>();
        for (int i = 1; i < requests.size(); i++) {
            gaps.add(TimeUnit.NANOSECONDS.toMillis(requests.get(i).receivedNanos - requests.get(i - 1).receivedNanos));
        }
        return gaps;
    }
}
