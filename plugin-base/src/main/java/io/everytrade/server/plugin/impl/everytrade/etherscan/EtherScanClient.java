package io.everytrade.server.plugin.impl.everytrade.etherscan;

import io.everytrade.server.plugin.impl.everytrade.PlatformKeyFailure;
import io.everytrade.server.plugin.impl.everytrade.PlatformKeyFailure.ResponseClass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import si.mazi.rescu.HttpStatusIOException;
import si.mazi.rescu.RestProxyFactory;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class EtherScanClient {
    private static final Logger LOG = LoggerFactory.getLogger(EtherScanClient.class);
    private static final String ETHERSCAN_URL = "https://api.etherscan.io/v2/";
    private static final Pattern RESULT = Pattern.compile("\"result\"\\s*:\\s*\"([^\"]*)\"");
    private static final Pattern MESSAGE = Pattern.compile("\"message\"\\s*:\\s*\"([^\"]*)\"");
    /** Attempts of one request that Etherscan refuses with its per-second rate limit. */
    private static final int RATE_LIMIT_ATTEMPTS = 5;
    private static final long MAX_BACKOFF_MS = 8_000L;

    EtherScanV1API api;
    /** First pause after a per-second rate limit, doubled per attempt; a field so that tests can shorten it. */
    long rateLimitBackoffMs = 1_000L;

    public EtherScanClient() {
        this.api = RestProxyFactory.createProxy(EtherScanV1API.class, ETHERSCAN_URL);
    }

    public EtherScanDto<List<EtherScanTransactionDto>> getNormalTxsByAddress(
        String address, long startBlock, long endBlock, int page, int offset, String sort, String apiKeyToken
    ) throws IOException {
        return withRetry(() -> api.getNormalTxsByAddress(
            "1", "account", "txlist", address, startBlock, endBlock, page, offset, sort, apiKeyToken));
    }

    public EtherScanDto<List<EtherScanErc20TransactionDto>> getErc20TxsByAddress(
        String address, String contractAddress, long startBlock, long endBlock, int page, int offset, String sort, String apiKeyToken
    ) throws IOException {
        return withRetry(() -> api.getErc20TxsByAddress(
            "1", "account", "tokentx", address, contractAddress, startBlock, endBlock, page, offset, sort, apiKeyToken));
    }

    public EtherScanDto<List<EtherScanInternalTransactionDto>> getInternalTxsByAddress(
        String address, long startBlock, long endBlock, int page, int offset, String sort, String apiKeyToken
    ) throws IOException {
        return withRetry(() -> api.getInternalTxsByAddress(
            "1", "account", "txlistinternal", address, startBlock, endBlock, page, offset, sort, apiKeyToken));
    }

    public EtherScanDto<Long> getBlockNumberByTimestamp(String timeStamp, String closest, String apiKeyToken) throws IOException {
        return withRetry(
            () -> api.getBlockNumberByTimestamp("1", "block", "getblocknobytime", timeStamp, closest, apiKeyToken));
    }

    /**
     * Runs one request. A bad platform key and a spent quota fail at once with the {@link PlatformKeyFailure} marker;
     * a per-second rate limit is backed off and retried (the downloader already paces requests at 3 a second, so it
     * means a burst from elsewhere on the same key); when the retries run out, and for every other refusal, the
     * failure is today's {@link #explain} text.
     */
    private <T> T withRetry(Call<T> call) throws IOException {
        long backoffMs = rateLimitBackoffMs;
        for (int attempt = 1; ; attempt++) {
            try {
                return call.execute();
            } catch (HttpStatusIOException e) {
                final int status = e.getHttpStatusCode();
                final String reason = reasonOf(e.getHttpBody());
                final ResponseClass responseClass = classify(status, reason);
                if (responseClass.isPlatformKeyFailure()) {
                    throw PlatformKeyFailure.of(PlatformKeyFailure.Provider.ETHERSCAN, responseClass, status,
                        responseClass == ResponseClass.KEY_INVALID
                            ? "Etherscan rejected the API key"
                            : "Etherscan call quota of the plan is used up",
                        explain(e));
                }
                if (responseClass != ResponseClass.RATE_LIMITED || attempt >= RATE_LIMIT_ATTEMPTS) {
                    throw explain(e);
                }
                final long sleep = jitter(backoffMs);
                LOG.warn("Etherscan rate limit (attempt {}/{}), backing off {} ms.", attempt, RATE_LIMIT_ATTEMPTS, sleep);
                sleep(sleep);
                backoffMs = Math.min(backoffMs * 2, MAX_BACKOFF_MS);
            }
        }
    }

    /**
     * Classifies a refusal by Etherscan's own words (https://docs.etherscan.io/resources/common-error-messages): it
     * answers HTTP 200 with {@code status "0"} and the reason in {@code result}. Matched case-insensitively on the
     * documented texts:
     * <ul>
     *   <li>KEY_INVALID - "Invalid API Key", "Missing/Invalid API Key", "Too many invalid api key attempts ...";</li>
     *   <li>RATE_LIMITED - "Max calls per sec rate limit reached (3/sec)", "Max rate limit reached ..." (and HTTP 429);</li>
     *   <li>QUOTA_EXHAUSTED - "Max daily rate limit reached", "Community Free API limit reached ...", and any other
     *       "... limit reached" that is not per second;</li>
     *   <li>OTHER - everything else, e.g. "Error! Invalid address format" (the user's address) or a server timeout.</li>
     * </ul>
     */
    static ResponseClass classify(int httpStatus, String reason) {
        final String text = reason == null ? "" : reason.toLowerCase(Locale.ROOT);
        if (text.contains("invalid api key") || text.contains("invalid api-key")) {
            return ResponseClass.KEY_INVALID;
        }
        if (text.contains("per sec") || text.contains("/sec") || text.startsWith("max rate limit reached")
            || (httpStatus == 429 && !text.contains("daily"))) {
            return ResponseClass.RATE_LIMITED;
        }
        if (text.contains("daily") && text.contains("limit")
            || text.contains("api limit reached")
            || text.contains("limit reached")) {
            return ResponseClass.QUOTA_EXHAUSTED;
        }
        return ResponseClass.OTHER;
    }

    /** Etherscan's reason: the {@code result} string, else the {@code message}; {@code null} when neither is there. */
    private static String reasonOf(String body) {
        if (body == null) {
            return null;
        }
        final Matcher result = RESULT.matcher(body);
        if (result.find() && !result.group(1).isBlank()) {
            return result.group(1);
        }
        final Matcher message = MESSAGE.matcher(body);
        if (message.find() && !message.group(1).isBlank()) {
            return message.group(1);
        }
        return null;
    }

    private static long jitter(long baseMs) {
        return (long) (baseMs * (0.75 + ThreadLocalRandom.current().nextDouble() * 0.5));
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting to retry an Etherscan request", ie);
        }
    }

    @FunctionalInterface
    private interface Call<T> {
        T execute() throws IOException;
    }

    /**
     * Turns Etherscan's way of reporting a problem into a message a human can act on.
     * <p>
     * Etherscan answers HTTP 200 to a rejected request and puts the reason in the body as
     * {@code {"status":"0","message":"NOTOK","result":"Error! Invalid address format"}}. {@link EtherScanDto}
     * declares {@code result} as the payload type, so for the list endpoints that String is handed to Jackson
     * where a List is expected and deserialization throws. rescu wraps that as an
     * {@link HttpStatusIOException} whose own message is the Jackson complaint - which is why a bad address, a
     * spent rate limit and a revoked key all reached the customer as one red error with no text (Sentry issue
     * 152285123, 7 occurrences to 2026-10-08).
     * <p>
     * The body is still on the exception, so the reason can simply be read back out of it. Anything we cannot
     * parse is passed through untouched rather than replaced by a guess.
     */
    private static IOException explain(HttpStatusIOException e) {
        final String reason = reasonOf(e.getHttpBody());
        return reason == null ? e : new IOException("Etherscan rejected the request: " + reason, e);
    }
}
