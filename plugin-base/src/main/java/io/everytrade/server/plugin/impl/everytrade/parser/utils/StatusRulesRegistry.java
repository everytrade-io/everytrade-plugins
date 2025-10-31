package io.everytrade.server.plugin.impl.everytrade.parser.utils;

import java.util.ArrayList;
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
        List<Predicate<String>> defaultRules = List.of(
            startsWith("COMPLETED"),
            contains("PAYMENT ARRIVED"),
            contains("ERROR (EXCHANGE PURCHASE)")
        );
        gb.put("default", defaultRules);

        List<Predicate<String>> profile1Rules = new ArrayList<>(List.of(
            startsWith("COMPLETED"),
            containsButNot("PAYMENT ARRIVED", "ON HOLD"),
            contains("ERROR (COINS UNCONFIRMED ON EXCHANGE)"),
            contains("ERROR (EXCHANGE SELL)"),
            contains("ERROR (NO ERROR)"),
            contains("ERROR (EXCHANGE WITHDRAWAL)"),
            contains("IN PROGRESS"),
            contains("ERROR (INVALID UNKNOWN ERROR)"),
            contains("ERROR (WITHDRAWAL PROBLEM)")
        ));
        gb.put("profile 1", profile1Rules);

        RULES.put("generalbytes", gb);
    }

    public static List<Predicate<String>> get(String exchangeId, String profileId) {
        var perProfile = RULES.get(exchangeId);
        if (perProfile == null) {
            return List.of();
        }
        return perProfile.getOrDefault(profileId, perProfile.getOrDefault("default", List.of()));
    }

    private static Predicate<String> containsButNot(String mustContain, String mustNotContain) {
        return s -> s.contains(mustContain) && !s.contains(mustNotContain);
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