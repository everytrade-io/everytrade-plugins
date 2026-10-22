package io.everytrade.server.plugin.impl.everytrade;

import io.everytrade.server.plugin.impl.everytrade.PlatformKeyFailure.Provider;
import io.everytrade.server.plugin.impl.everytrade.PlatformKeyFailure.ResponseClass;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.regex.Matcher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the marker contract the HOST matches (ETD-2217 point 9). Changing any literal here is a breaking change for
 * the host recognizer and must be made on both sides at once.
 */
class PlatformKeyFailureTest {

    @Test
    void markerTextIsExactlyTheHostContract() {
        final IOException e = PlatformKeyFailure.of(Provider.ETHERSCAN, ResponseClass.QUOTA_EXHAUSTED, 200,
            "Etherscan call quota of the plan is used up", null);

        assertEquals(IOException.class, e.getClass());
        assertTrue(e.getMessage().startsWith(
            "Platform API key failure: provider=ETHERSCAN reason=QUOTA_EXHAUSTED. "), e.getMessage());
        assertEquals("^Platform API key failure: provider=(HELIUS|ETHERSCAN) reason=(KEY_INVALID|QUOTA_EXHAUSTED)\\.",
            PlatformKeyFailure.MARKER.pattern());
    }

    @Test
    void markerIsFoundThroughTheConnectorsWrapper() {
        final IOException marker = PlatformKeyFailure.of(Provider.HELIUS, ResponseClass.KEY_INVALID, 401,
            "Helius rejected the API key", new IOException("provider detail"));
        final IllegalStateException wrapped =
            new IllegalStateException("Transaction history download failed, address=x", marker);

        final Matcher m = PlatformKeyFailure.find(wrapped);

        assertNotNull(m);
        assertEquals("HELIUS", m.group(1));
        assertEquals("KEY_INVALID", m.group(2));
        assertNull(PlatformKeyFailure.find(new IllegalStateException("x", new IOException("other"))));
    }

    @Test
    void onlyKeyInvalidAndQuotaExhaustedCanBeAMarker() {
        assertThrows(IllegalStateException.class,
            () -> PlatformKeyFailure.of(Provider.HELIUS, ResponseClass.RATE_LIMITED, 429, "x", null));
        assertThrows(IllegalStateException.class,
            () -> PlatformKeyFailure.of(Provider.HELIUS, ResponseClass.OTHER, 500, "x", null));
    }
}
