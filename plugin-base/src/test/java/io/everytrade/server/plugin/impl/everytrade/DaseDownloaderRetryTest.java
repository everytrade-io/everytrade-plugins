package io.everytrade.server.plugin.impl.everytrade;

import org.junit.jupiter.api.Test;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.dase.DaseExchange;
import org.knowm.xchange.dase.dto.account.ApiAccountTxn;
import org.knowm.xchange.dase.dto.account.ApiGetAccountTxnsOutput;
import org.knowm.xchange.dase.service.DaseAccountService;
import si.mazi.rescu.HttpStatusIOException;
import si.mazi.rescu.InvocationResult;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * HTTP 429 handling of {@link DaseDownloader}: a {@code Retry-After} wait counts as an attempt (six in total), a
 * {@code Retry-After} above 60 s ends the run, and the waits of one request stay within five minutes. The sleeper
 * only records, so every test runs in milliseconds; the timeouts guard against a loop that never ends.
 */
class DaseDownloaderRetryTest {

    private static final Duration GUARD = Duration.ofSeconds(10);

    private final List<Long> sleeps = new ArrayList<>();

    private DaseDownloader downloader(DaseAccountService service) {
        return new DaseDownloader(null, service, 100, 200, Duration.ZERO, () -> 1_700_000_000_000L, sleeps::add);
    }

    @Test
    void persistent429WithRetryAfterStopsAfterSixAttempts() {
        ScriptedDase dase = new ScriptedDase(429, "1", Integer.MAX_VALUE);

        RuntimeException e = assertTimeoutPreemptively(GUARD,
            () -> assertThrows(RuntimeException.class, () -> downloader(dase).downloadAllTransactions(1000)));

        assertEquals(6, dase.requests, "a Retry-After wait is an attempt");
        assertEquals(List.of(1000L, 1000L, 1000L, 1000L, 1000L), sleeps);
        assertTrue(e.getMessage().contains("6 attempts"), e::getMessage);
    }

    @Test
    void oneTooManyRequestsThenSuccessReturnsAfterTwoCalls() {
        ScriptedDase dase = new ScriptedDase(429, "2", 1);

        List<ApiAccountTxn> rows = assertTimeoutPreemptively(GUARD, () -> downloader(dase).downloadAllTransactions(1000));

        assertEquals(List.of(), rows);
        assertEquals(2, dase.requests);
        assertEquals(List.of(2000L), sleeps);
    }

    @Test
    void retryAfterAboveTheCapEndsTheRunWithoutWaiting() {
        ScriptedDase dase = new ScriptedDase(429, "3600", Integer.MAX_VALUE);

        RuntimeException e = assertTimeoutPreemptively(GUARD,
            () -> assertThrows(RuntimeException.class, () -> downloader(dase).downloadAllTransactions(1000)));

        assertEquals(1, dase.requests);
        assertEquals(List.of(), sleeps);
        assertTrue(e.getMessage().contains("wait 3600 s"), e::getMessage);
    }

    /**
     * Guard, not a kill-check: with six attempts and at most 60 s per Retry-After the waits cannot exceed five
     * minutes even without the total-wait cap. The cap itself is pinned by
     * {@link #totalWaitCapEndsTheRunBeforeTheWaitThatWouldExceedIt()}.
     */
    @Test
    void persistentRetryAfterAtTheCapWaitsAtMostFiveMinutesInTotal() {
        ScriptedDase dase = new ScriptedDase(429, "60", Integer.MAX_VALUE);

        assertTimeoutPreemptively(GUARD,
            () -> assertThrows(RuntimeException.class, () -> downloader(dase).downloadAllTransactions(1000)));

        long total = sleeps.stream().mapToLong(Long::longValue).sum();
        assertTrue(total <= Duration.ofMinutes(5).toMillis(), "waited " + total + " ms");
        assertTrue(dase.requests <= 6, "requests " + dase.requests);
    }

    @Test
    void totalWaitCapEndsTheRunBeforeTheWaitThatWouldExceedIt() {
        ScriptedDase dase = new ScriptedDase(429, "1", Integer.MAX_VALUE);
        DaseDownloader downloader = new DaseDownloader(null, dase, 100, 200, Duration.ZERO,
            () -> 1_700_000_000_000L, sleeps::add, Duration.ofMillis(2500));

        RuntimeException e = assertTimeoutPreemptively(GUARD,
            () -> assertThrows(RuntimeException.class, () -> downloader.downloadAllTransactions(1000)));

        assertEquals(3, dase.requests, "the third wait would take the total to 3000 ms");
        assertEquals(List.of(1000L, 1000L), sleeps);
        assertTrue(e.getMessage().contains("in total"), e::getMessage);
    }

    /** Fails the first {@code failures} requests with the given status and Retry-After, then answers empty pages. */
    private static final class ScriptedDase extends DaseAccountService {
        private final int status;
        private final String retryAfter;
        private final int failures;
        int requests;

        ScriptedDase(int status, String retryAfter, int failures) {
            super(exchange());
            this.status = status;
            this.retryAfter = retryAfter;
            this.failures = failures;
        }

        @Override
        public ApiGetAccountTxnsOutput getAccountTransactions(Integer limit, String before) throws IOException {
            requests++;
            if (requests <= failures) {
                HttpStatusIOException e = new HttpStatusIOException("HTTP status code was not OK: " + status,
                    new InvocationResult("Too Many Requests", status));
                e.setResponseHeaders(Map.of("Retry-After", List.of(retryAfter)));
                throw e;
            }
            return new ApiGetAccountTxnsOutput(new ArrayList<>());
        }

        private static Exchange exchange() {
            Exchange exchange = mock(Exchange.class);
            when(exchange.getExchangeSpecification()).thenReturn(new DaseExchange().getDefaultExchangeSpecification());
            return exchange;
        }
    }
}
