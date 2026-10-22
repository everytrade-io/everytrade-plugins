package io.everytrade.server.plugin.impl.everytrade.helius;

import io.everytrade.server.plugin.impl.everytrade.PlatformKeyFailure;
import io.everytrade.server.plugin.impl.everytrade.PlatformKeyFailure.ResponseClass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import si.mazi.rescu.HttpStatusIOException;
import si.mazi.rescu.RestProxyFactory;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

public class HeliusClient {
    private static final Logger LOG = LoggerFactory.getLogger(HeliusClient.class);
    private static final String HELIUS_URL = "https://api.helius.xyz/";
    private static final String HELIUS_RPC_URL = "https://mainnet.helius-rpc.com/";
    /**
     * Commitment of both RPC reads. "confirmed", not the RPC default "finalized": the Wallet API already lists
     * confirmed transactions, and the RPC window is bounded by the newest of them; under "finalized" a signature that
     * is only confirmed is unknown to the node and the window would come back empty.
     */
    private static final String COMMITMENT = "confirmed";

    private static final Set<Integer> RETRYABLE_STATUS = Set.of(408, 429, 500, 502, 503, 504);
    private static final int MAX_ATTEMPTS = 5;
    private static final long INITIAL_BACKOFF_MS = 1_000L;
    private static final long MAX_BACKOFF_MS = 30_000L;
    /** JSON-RPC error code of a spent credit quota, sent with HTTP 429 or inside an HTTP 200 envelope. */
    private static final int RPC_CODE_MAX_USAGE = -32429;
    /** JSON-RPC error code of a per-second rate limit (https://www.helius.dev/docs/billing/rate-limits). */
    private static final int RPC_CODE_RATE_LIMITED = -32005;
    /** JSON-RPC error code Helius sends with a rejected API key (seen with HTTP 401: "invalid api key provided"). */
    private static final int RPC_CODE_KEY_REJECTED = -32401;
    /**
     * JSON-RPC 2.0 request errors (-32600 invalid request, -32601 method not found, -32602 invalid params): the same
     * request gets the same answer, so retrying them only burns credits.
     */
    private static final Set<Integer> RPC_CODES_MALFORMED_REQUEST = Set.of(-32600, -32601, -32602);

    private final HeliusWalletAPI api;
    private final HeliusRpcAPI rpc;

    public HeliusClient() {
        this.api = RestProxyFactory.createProxy(HeliusWalletAPI.class, HELIUS_URL);
        this.rpc = RestProxyFactory.createProxy(HeliusRpcAPI.class, HELIUS_RPC_URL);
    }

    public HeliusResponseDto getTransactionHistory(String address, String apiKey, int limit, String before)
        throws IOException {
        return withRetry(() -> api.getTransactionHistory(address, apiKey, limit, before));
    }

    /*
     * Every attempt of an RPC call (retries included) first takes its slot from the JVM-wide HeliusRpcPacer (default
     * 125 ms = 8/s, under the FREE plan's 10 RPC requests/s). The Wallet API history is paced by the caller.
     */

    /**
     * {@code getSignaturesForAddress}: signatures of {@code address}, newest first, strictly older than
     * {@code before} and strictly newer than {@code until} (either may be {@code null}), at most {@code limit}.
     */
    public List<HeliusRpcSignatureDto> getSignaturesForAddress(
        String address, String apiKey, int limit, String before, String until
    ) throws IOException {
        final Map<String, Object> options = new LinkedHashMap<>();
        options.put("limit", limit);
        if (before != null) {
            options.put("before", before);
        }
        if (until != null) {
            options.put("until", until);
        }
        options.put("commitment", COMMITMENT);
        final var request = HeliusRpcRequestDto.of("getSignaturesForAddress", List.of(address, options));
        final var response = withRetry(() -> {
            HeliusRpcPacer.RPC.acquire();
            return retryTransientRpcAnswer("getSignaturesForAddress", rpc.getSignaturesForAddress(apiKey, request),
                HeliusRpcSignaturesResponseDto::getError);
        });
        checkRpcError("getSignaturesForAddress", response.getError());
        return response.getResult() == null ? List.of() : response.getResult();
    }

    /**
     * {@code getTransaction} (encoding json). A {@code "result": null} answer (the node does not know the signature, e.g.
     * it lags behind "confirmed") is retried; it fails as "not found" only after the last attempt.
     */
    public HeliusRpcTransactionDto getTransaction(String signature, String apiKey) throws IOException {
        final Map<String, Object> options = new LinkedHashMap<>();
        options.put("encoding", "json");
        options.put("maxSupportedTransactionVersion", 0);
        options.put("commitment", COMMITMENT);
        final var request = HeliusRpcRequestDto.of("getTransaction", List.of(signature, options));
        final var response = withRetry(() -> {
            HeliusRpcPacer.RPC.acquire();
            final var answer = retryTransientRpcAnswer("getTransaction", rpc.getTransaction(apiKey, request),
                HeliusRpcTransactionResponseDto::getError);
            if (answer.getError() == null && answer.getResult() == null) {
                throw new IOException(String.format("Helius RPC getTransaction: transaction %s not found.", signature));
            }
            return answer;
        });
        checkRpcError("getTransaction", response.getError());
        return response.getResult();
    }

    private static void checkRpcError(String method, HeliusRpcErrorDto error) throws IOException {
        if (error != null) {
            if (classifyRpcError(error) == ResponseClass.QUOTA_EXHAUSTED) {
                throw PlatformKeyFailure.of(PlatformKeyFailure.Provider.HELIUS, ResponseClass.QUOTA_EXHAUSTED, 200,
                    "Helius RPC " + method + ": the plan's credits are used up (JSON-RPC " + error.getCode() + ")",
                    null);
            }
            throw new IOException(String.format("Helius RPC %s error %s: %s", method, error.getCode(),
                error.getMessage()));
        }
    }

    private <T> T withRetry(Call<T> call) throws IOException {
        long delayMs = INITIAL_BACKOFF_MS;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return call.execute();
            } catch (HttpStatusIOException e) {
                int status = e.getHttpStatusCode();
                final ResponseClass responseClass = classify(status, safeBody(e));
                if (responseClass == ResponseClass.KEY_INVALID) {
                    throw PlatformKeyFailure.of(PlatformKeyFailure.Provider.HELIUS, responseClass, status,
                        "Helius rejected the API key", decorate(e, attempt, "key rejected (no retry possible)"));
                }
                if (responseClass == ResponseClass.QUOTA_EXHAUSTED) {
                    throw PlatformKeyFailure.of(PlatformKeyFailure.Provider.HELIUS, responseClass, status,
                        "Helius credits of the plan are used up",
                        decorate(e, attempt, "monthly credit quota exhausted (no retry possible)"));
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

    /**
     * Classifies a non-2xx Helius answer. 401 is the documented answer to a missing, wrong, revoked or access-rule
     * blocked key (https://www.helius.dev/docs/api-reference/common-error-codes; an empty key answered 401 live on
     * 2026-10-20). Helius answers 429 both to a per-second rate limit and to spent credits; only the latter says
     * "max usage reached" / JSON-RPC code -32429, and retrying it cannot help. Every other status keeps today's
     * handling: retried when it is in {@link #RETRYABLE_STATUS}, else failed as it is.
     */
    static ResponseClass classify(int status, String body) {
        if (status == 401) {
            return ResponseClass.KEY_INVALID;
        }
        if (isMaxUsage(body)) {
            return ResponseClass.QUOTA_EXHAUSTED;
        }
        if (status == 429) {
            return ResponseClass.RATE_LIMITED;
        }
        return ResponseClass.OTHER;
    }

    /** Classifies the JSON-RPC {@code error} object of an HTTP 200 answer. */
    static ResponseClass classifyRpcError(HeliusRpcErrorDto error) {
        if (error == null) {
            return ResponseClass.OTHER;
        }
        final Integer code = error.getCode();
        if ((code != null && code == RPC_CODE_MAX_USAGE) || isMaxUsage(error.getMessage())) {
            return ResponseClass.QUOTA_EXHAUSTED;
        }
        if (code != null && code == RPC_CODE_RATE_LIMITED) {
            return ResponseClass.RATE_LIMITED;
        }
        return ResponseClass.OTHER;
    }

    private static boolean isMaxUsage(String text) {
        if (text == null) {
            return false;
        }
        final String compact = text.replace(" ", "");
        return compact.contains("\"code\":" + RPC_CODE_MAX_USAGE)
            || text.toLowerCase(Locale.ROOT).contains("max usage reached");
    }

    /**
     * Turns a transient HTTP 200 answer into a plain IOException, which {@link #withRetry} backs off and retries like a
     * network error (each attempt takes a new pacer slot): an empty body (rescu maps it to {@code null}) and any
     * JSON-RPC {@code error} envelope (rate limit -32005, internal error -32603, node unavailable or behind -32004 /
     * -32016, ...) EXCEPT spent credits, a rejected key and a malformed request, which no retry can fix: those are
     * returned as they are and {@link #checkRpcError} fails them after the loop, a spent quota with the
     * QUOTA_EXHAUSTED marker.
     */
    private static <T> T retryTransientRpcAnswer(String method, T response,
                                                 java.util.function.Function<T, HeliusRpcErrorDto> error)
        throws IOException {
        if (response == null) {
            throw new IOException("Helius RPC " + method + ": empty response body");
        }
        final HeliusRpcErrorDto e = error.apply(response);
        if (e != null && isTransientRpcError(e)) {
            throw new IOException(String.format("Helius RPC %s transient error %s: %s", method, e.getCode(),
                e.getMessage()));
        }
        return response;
    }

    private static boolean isTransientRpcError(HeliusRpcErrorDto error) {
        if (classifyRpcError(error) == ResponseClass.QUOTA_EXHAUSTED) {
            return false;
        }
        final Integer code = error.getCode();
        if (code != null && (code == RPC_CODE_KEY_REJECTED || RPC_CODES_MALFORMED_REQUEST.contains(code))) {
            return false;
        }
        final String message = error.getMessage();
        return message == null || !message.toLowerCase(Locale.ROOT).contains("invalid api key");
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

    @FunctionalInterface
    private interface Call<T> {
        T execute() throws IOException;
    }
}
