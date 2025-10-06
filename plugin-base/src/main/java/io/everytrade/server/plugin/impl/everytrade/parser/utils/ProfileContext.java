package io.everytrade.server.plugin.impl.everytrade.parser.utils;

public final class ProfileContext {
    private static final ThreadLocal<String> TL = new ThreadLocal<>();

    public static void set(String profile) {
        TL.set(profile == null ? "default" : profile);
    }

    public static String get() {
        return TL.get() == null ? "default" : TL.get();
    }

    public static void clear() {
        TL.remove();
    }
}