package io.everytrade.server.plugin.impl.everytrade.parser.utils;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;

public final class StatusRulesRegistry {
    private StatusRulesRegistry() {
    }

    private static final Map<String, Map<String, List<Predicate<String>>>> RULES = new HashMap<>();

    static {
        Map<String, List<Predicate<String>>> gb = new HashMap<>();
        gb.put("default", List.of(
            startsWith("COMPLETED"),
            contains("PAYMENT ARRIVED"),
            contains("ERROR (EXCHANGE PURCHASE)")
        ));
        gb.put("profile 2", List.of(
            startsWith("COMPLETED"),
            contains("PAYMENT ARRIVED"),
            contains("ERROR (EXCHANGE PURCHASE)"),
            contains("ERROR (EXCHANGE SELL)"),
            contains("ERROR (EXCHANGE WITHDRAWAL)")
        ));
        RULES.put("generalbytes", gb);
    }

    public static List<Predicate<String>> get(String exchangeId, String profileId) {
        var perProfile = RULES.get(exchangeId);
        if (perProfile == null) {
            return List.of();
        }
        return perProfile.getOrDefault(profileId, perProfile.getOrDefault("default", List.of()));
    }

    private static Predicate<String> startsWith(String prefix) {
        final String p = prefix.toLowerCase(Locale.ROOT);
        return s -> s != null && s.toLowerCase(Locale.ROOT).startsWith(p);
    }

    private static Predicate<String> contains(String needle) {
        final String n = needle.toLowerCase(Locale.ROOT);
        return s -> s != null && s.toLowerCase(Locale.ROOT).contains(n);
    }
}