package io.everytrade.server.plugin.impl.everytrade.helius;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

/**
 * Start-to-start pacing of the Helius JSON-RPC calls ({@code getSignaturesForAddress}, {@code getTransaction}),
 * shared by the whole JVM through {@link #RPC}: every {@link HeliusClient}, every SOL connection and every retry
 * attempt takes its slot from the same pacer, so several connections syncing at once stay under the plan's limit
 * together.
 * <p>
 * Helius rate limits (https://www.helius.dev/docs/billing/rate-limits, read 2026-10-20): RPC 10 / 50 / 200 / 500
 * requests per second on the Free / Developer / Business / Professional plan. The default interval of
 * {@value #DEFAULT_INTERVAL_MS} ms (8 requests/s) is safe for the FREE plan. A paid plan may shorten it with the JVM
 * system property {@value #INTERVAL_PROPERTY} (whole milliseconds, 1 to {@value #MAX_INTERVAL_MS}; e.g. 25 = 40/s for
 * the Developer plan's 50/s); an invalid value is ignored with a warning and the default applies. The property is
 * read once, when this class is loaded.
 * <p>
 * The Wallet API history is NOT paced here: it keeps its own 500 ms pacing in the SOL downloader (the Wallet API
 * limit is 2 requests/s on FREE, a separate pool).
 */
public final class HeliusRpcPacer {
    private static final Logger LOG = LoggerFactory.getLogger(HeliusRpcPacer.class);

    public static final String INTERVAL_PROPERTY = "everytrade.helius.rpcMinIntervalMs";
    static final long DEFAULT_INTERVAL_MS = 125L;
    static final long MAX_INTERVAL_MS = 60_000L;

    /** The one pacer of every Helius RPC call in this JVM. */
    static final HeliusRpcPacer RPC = new HeliusRpcPacer(intervalMs(System.getProperty(INTERVAL_PROPERTY)));

    private final long intervalNanos;
    /** The earliest start of the next call; guarded by {@code this}. */
    private long nextSlotNanos;

    HeliusRpcPacer(long intervalMs) {
        this.intervalNanos = TimeUnit.MILLISECONDS.toNanos(intervalMs);
        this.nextSlotNanos = System.nanoTime();
    }

    /**
     * Blocks until this caller's slot: at least one interval after the start of the previous call. The slot is taken
     * under the lock and waited for outside it, so concurrent callers queue in order without holding the lock.
     */
    void acquire() {
        final long waitNanos;
        synchronized (this) {
            final long now = System.nanoTime();
            final long slot = now - nextSlotNanos > 0 ? now : nextSlotNanos;
            nextSlotNanos = slot + intervalNanos;
            waitNanos = slot - now;
        }
        if (waitNanos > 0) {
            try {
                TimeUnit.NANOSECONDS.sleep(waitNanos);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted while pacing a Helius RPC request", e);
            }
        }
    }

    /** The interval in ms for a value of {@link #INTERVAL_PROPERTY}; the default when it is unset or invalid. */
    static long intervalMs(String value) {
        if (value == null || value.isBlank()) {
            return DEFAULT_INTERVAL_MS;
        }
        try {
            final long ms = Long.parseLong(value.trim());
            if (ms >= 1 && ms <= MAX_INTERVAL_MS) {
                return ms;
            }
        } catch (NumberFormatException ignored) {
            // falls through to the warning
        }
        LOG.warn("Ignoring invalid {}={}; expected whole milliseconds 1..{}. Using {} ms.",
            INTERVAL_PROPERTY, value, MAX_INTERVAL_MS, DEFAULT_INTERVAL_MS);
        return DEFAULT_INTERVAL_MS;
    }
}
