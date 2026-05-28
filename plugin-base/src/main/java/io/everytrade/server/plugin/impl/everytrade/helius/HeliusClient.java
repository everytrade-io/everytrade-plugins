package io.everytrade.server.plugin.impl.everytrade.helius;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import si.mazi.rescu.HttpStatusIOException;
import si.mazi.rescu.RestProxyFactory;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

public class HeliusClient {
    private static final Logger LOG = LoggerFactory.getLogger(HeliusClient.class);
    private static final String HELIUS_URL = "https://api.helius.xyz/";

    private static final Set<Integer> RETRYABLE_STATUS = Set.of(408, 429, 500, 502, 503, 504);
    private static final int MAX_ATTEMPTS = 5;
    private static final long INITIAL_BACKOFF_MS = 1_000L;
    private static final long MAX_BACKOFF_MS = 30_000L;

    private final HeliusWalletAPI api;

    public HeliusClient() {
        this.api = RestProxyFactory.createProxy(HeliusWalletAPI.class, HELIUS_URL);
    }

    public HeliusResponseDto getTransactionHistory(String address, String apiKey, int limit, String before)
        throws IOException {
        long delayMs = INITIAL_BACKOFF_MS;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return api.getTransactionHistory(address, apiKey, limit, before);
            } catch (HttpStatusIOException e) {
                int status = e.getHttpStatusCode();
                if (isQuotaExhausted(e)) {
                    throw decorate(e, attempt, "monthly credit quota exhausted (no retry possible)");
                }
                if (!RETRYABLE_STATUS.contains(status)) {
                    throw decorate(e, attempt, "non-retryable status");
                }
                if (attempt == MAX_ATTEMPTS) {
                    throw decorate(e, attempt, "max retries exhausted");
                }
                long sleep = retryAfterMs(e).orElse(jitter(delayMs));
                LOG.warn("Helius API returned HTTP {} (attempt {}/{}). Body: {}. Backing off {} ms.",
                    status, attempt, MAX_ATTEMPTS, safeBody(e), sleep);
                sleep(sleep);
            } catch (IOException e) {
                if (attempt == MAX_ATTEMPTS) {
                    throw new IOException(
                        "Helius request failed after " + MAX_ATTEMPTS + " attempts: " + e.getMessage(), e);
                }
                long sleep = jitter(delayMs);
                LOG.warn("Helius API network error (attempt {}/{}): {}. Backing off {} ms.",
                    attempt, MAX_ATTEMPTS, e.getMessage(), sleep);
                sleep(sleep);
            }
            delayMs = Math.min(delayMs * 2, MAX_BACKOFF_MS);
        }
        throw new IOException("Unreachable retry loop exit");
    }

    private static IOException decorate(HttpStatusIOException e, int attempt, String reason) {
        return new IOException(
            String.format("Helius HTTP %d (%s after %d attempt(s)). Body: %s. Headers: %s",
                e.getHttpStatusCode(), reason, attempt, safeBody(e), e.getResponseHeaders()),
            e
        );
    }

    private static boolean isQuotaExhausted(HttpStatusIOException e) {
        String body = safeBody(e);
        return body.contains("\"code\":-32429") || body.contains("max usage reached");
    }

    private static String safeBody(HttpStatusIOException e) {
        try {
            String body = e.getHttpBody();
            return body == null ? "<empty>" : body;
        } catch (Exception ignored) {
            return "<unavailable>";
        }
    }

    private static Optional<Long> retryAfterMs(HttpStatusIOException e) {
        Map<String, List<String>> headers = e.getResponseHeaders();
        if (headers == null) {
            return Optional.empty();
        }
        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase("Retry-After")
                && entry.getValue() != null && !entry.getValue().isEmpty()) {
                try {
                    long seconds = Long.parseLong(entry.getValue().get(0).trim());
                    return Optional.of(Math.min(seconds * 1000L, MAX_BACKOFF_MS));
                } catch (NumberFormatException ignored) {
                    return Optional.empty();
                }
            }
        }
        return Optional.empty();
    }

    private static long jitter(long baseMs) {
        double factor = 0.75 + ThreadLocalRandom.current().nextDouble() * 0.5;
        return (long) (baseMs * factor);
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while waiting to retry Helius request", ie);
        }
    }
}
