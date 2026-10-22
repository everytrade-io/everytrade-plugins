package io.everytrade.server.plugin.impl.everytrade;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A failure of OUR platform API key - not the user's input - in a connector that reads a provider with a key the host
 * supplies (SOL: Helius {@code helius.api.key}; ETH: Etherscan {@code etherscan.api.token}).
 * <p>
 * plugin-api is deliberately NOT changed (an older host must keep loading this plugin, and a new plugin-api type thrown
 * at an older host would end in NoClassDefFoundError). The classification is carried as a STABLE MARKER at the start of
 * the message of a plain {@link IOException} (exact class {@code java.io.IOException}) somewhere in the cause chain of
 * what the connector throws. The connector wraps it (an {@link IllegalStateException} "... download failed ..."), so
 * the host must walk the whole cause chain.
 * <p>
 * The contract the host matches - pinned by {@code PlatformKeyFailureTest}; rewording it is a breaking change:
 * <pre>
 *   message starts with  "Platform API key failure: provider=&lt;PROVIDER&gt; reason=&lt;REASON&gt;."
 *   regex                ^Platform API key failure: provider=(HELIUS|ETHERSCAN) reason=(KEY_INVALID|QUOTA_EXHAUSTED)\.
 * </pre>
 * The message never contains the key, the user's address or a provider body - only the provider, the reason, the
 * HTTP status and a fixed description. The provider's original exception stays attached as the cause.
 */
public final class PlatformKeyFailure {

    /** The fixed start of every marker message. */
    public static final String MARKER_PREFIX = "Platform API key failure: ";

    /** Matches a marker message; group 1 = {@link Provider}, group 2 = {@link Reason}. */
    public static final Pattern MARKER =
        Pattern.compile("^Platform API key failure: provider=(HELIUS|ETHERSCAN) reason=(KEY_INVALID|QUOTA_EXHAUSTED)\\.");

    /** The provider whose key failed. */
    public enum Provider {
        HELIUS,
        ETHERSCAN
    }

    /** What happened to the key; both are on our side and need a person, not a retry. */
    public enum Reason {
        /** The key is missing, wrong, revoked or blocked by the provider's access rules. */
        KEY_INVALID,
        /** The plan's quota (Helius monthly credits, Etherscan daily calls) is spent. */
        QUOTA_EXHAUSTED
    }

    /**
     * How a connector classifies a provider's answer. Only {@link #KEY_INVALID} and {@link #QUOTA_EXHAUSTED} become a
     * marker; {@link #RATE_LIMITED} is retried with backoff and, when the retries run out, fails like {@link #OTHER}.
     */
    public enum ResponseClass {
        KEY_INVALID,
        QUOTA_EXHAUSTED,
        RATE_LIMITED,
        OTHER;

        Reason reason() {
            if (this == KEY_INVALID) {
                return Reason.KEY_INVALID;
            }
            if (this == QUOTA_EXHAUSTED) {
                return Reason.QUOTA_EXHAUSTED;
            }
            throw new IllegalStateException(this + " is not a platform key failure");
        }

        public boolean isPlatformKeyFailure() {
            return this == KEY_INVALID || this == QUOTA_EXHAUSTED;
        }
    }

    private PlatformKeyFailure() {
    }

    /**
     * The marker exception. {@code description} must be a fixed text of ours - never a provider body, a key or an
     * address.
     */
    public static IOException of(Provider provider, ResponseClass responseClass, int httpStatus, String description,
                                 Throwable cause) {
        final String message = MARKER_PREFIX + "provider=" + provider + " reason=" + responseClass.reason() + ". "
            + description + " (HTTP " + httpStatus + "). The platform key is ours, not the user's.";
        return new IOException(message, cause);
    }

    /** The marker in {@code t}'s cause chain, if any (for tests and for a host that ships this class). */
    public static Matcher find(Throwable t) {
        for (Throwable c = t; c != null; c = c.getCause() == c ? null : c.getCause()) {
            if (c.getClass() == IOException.class && c.getMessage() != null) {
                final Matcher m = MARKER.matcher(c.getMessage());
                if (m.find()) {
                    return m;
                }
            }
        }
        return null;
    }
}
