package io.everytrade.server.plugin.impl.everytrade.helius;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The JVM-wide pacer of the Helius JSON-RPC calls and its override
 * ({@code -Deverytrade.helius.rpcMinIntervalMs=...}, for a paid Helius plan).
 */
class HeliusRpcPacerTest {

    @Test
    void theDefaultIntervalIsSafeForTheFreePlan() {
        // FREE: 10 RPC requests/s (https://www.helius.dev/docs/billing/rate-limits); 125 ms = 8/s.
        assertEquals(125L, HeliusRpcPacer.DEFAULT_INTERVAL_MS);
        assertEquals(125L, HeliusRpcPacer.intervalMs(null));
        assertEquals(125L, HeliusRpcPacer.intervalMs(""));
        assertEquals(125L, HeliusRpcPacer.intervalMs("  "));
    }

    @Test
    void aValidOverrideIsTaken() {
        assertEquals(25L, HeliusRpcPacer.intervalMs("25"));
        assertEquals(500L, HeliusRpcPacer.intervalMs(" 500 "));
    }

    @Test
    void anInvalidOverrideFallsBackToTheDefault() {
        assertEquals(125L, HeliusRpcPacer.intervalMs("abc"));
        assertEquals(125L, HeliusRpcPacer.intervalMs("0"));
        assertEquals(125L, HeliusRpcPacer.intervalMs("-5"));
        assertEquals(125L, HeliusRpcPacer.intervalMs("12.5"));
        assertEquals(125L, HeliusRpcPacer.intervalMs("60001"));
    }

    /**
     * The slots lie on a fixed grid one interval apart, starting at the first call. A return can never precede its
     * slot (less 1 ms: {@code Thread.sleep} rounds a sub-millisecond remainder down), but it may come late, so the
     * gaps between two RETURNS say nothing; every return is measured against the time before the first call.
     */
    @Test
    void consecutiveAcquiresAreSpacedStartToStart() {
        final long intervalMs = 50L;
        final int calls = 6;
        final HeliusRpcPacer pacer = new HeliusRpcPacer(intervalMs);
        final long t0 = System.nanoTime();
        final List<Long> sinceStartMicros = new ArrayList<>();
        for (int i = 0; i < calls; i++) {
            pacer.acquire();
            sinceStartMicros.add(TimeUnit.NANOSECONDS.toMicros(System.nanoTime() - t0));
        }
        for (int k = 1; k <= calls; k++) {
            final long minMicros = TimeUnit.MILLISECONDS.toMicros((k - 1) * intervalMs - 1);
            assertTrue(sinceStartMicros.get(k - 1) >= minMicros,
                "call " + k + " returned " + sinceStartMicros.get(k - 1) + " us after the start, before its slot at "
                    + minMicros + " us; all " + sinceStartMicros);
        }
        // generous, CPU-proof: only catches a pacer that waits far longer than the grid
        final long maxMicros = TimeUnit.MILLISECONDS.toMicros((calls - 1) * intervalMs + 5_000L);
        assertTrue(sinceStartMicros.get(calls - 1) <= maxMicros, "total span " + sinceStartMicros);
    }

    @Test
    void anIdlePacerDoesNotDelayTheNextCall() throws Exception {
        final HeliusRpcPacer pacer = new HeliusRpcPacer(200L);
        pacer.acquire();
        Thread.sleep(250L);
        final long start = System.nanoTime();
        pacer.acquire();
        assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start) < 50L,
            "the interval has already passed, no wait");
    }
}
