package io.everytrade.server.plugin.api.rateprovider;

/**
 * Order-dependent. Keep ordered from less- to more-cacheable.
 */
public enum CachingStrategy {
    DO_NOT_CACHE,
    SHORT_TERM,
    LONG_TERM
}
