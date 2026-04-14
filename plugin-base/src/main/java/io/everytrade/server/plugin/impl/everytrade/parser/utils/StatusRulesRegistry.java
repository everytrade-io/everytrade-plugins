package io.everytrade.server.plugin.impl.everytrade.parser.utils;

import io.everytrade.server.model.TransactionType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;

import static io.everytrade.server.model.TransactionType.BUY;
import static io.everytrade.server.model.TransactionType.SELL;

public final class StatusRulesRegistry {
    private StatusRulesRegistry() {
    }

    private static final Map<String, Map<String, List<BiPredicate<String, TransactionType>>>> RULES = new HashMap<>();

    static {
        Map<String, List<BiPredicate<String, TransactionType>>> gb = new HashMap<>();

        List<BiPredicate<String, TransactionType>> defaultRules = List.of(
            statusStartsWith("COMPLETED"),
            statusContains("PAYMENT ARRIVED"),
            statusContains("ERROR (EXCHANGE PURCHASE)")
        );
        gb.put("default", defaultRules);

        List<BiPredicate<String, TransactionType>> profile1Rules = new ArrayList<>(List.of(
            and(statusStartsWith("COMPLETED"), typeIs(BUY)),
            and(statusContains("IN PROGRESS"), typeIs(BUY)),
            and(statusContainsButNot("PAYMENT ARRIVED", "ON HOLD"), typeIs(SELL)),
            and(statusContains("ERROR (COINS UNCONFIRMED ON EXCHANGE)"), typeIs(SELL)),
            and(statusContains("ERROR (EXCHANGE SELL)"), typeIs(SELL)),
            and(statusContains("ERROR (EXCHANGE WITHDRAWAL)"), typeIs(BUY)),
            statusContains("ERROR (INVALID UNKNOWN ERROR)"),
            and(statusContains("ERROR (NO ERROR)"), typeIs(BUY)),
            and(statusContains("ERROR (WITHDRAWAL PROBLEM)"), typeIs(SELL))
        ));
        gb.put("profile 1", profile1Rules);

        RULES.put("generalbytes", gb);
    }

    public static List<BiPredicate<String, TransactionType>> get(String exchangeId, String profileId) {
        var perProfile = RULES.get(exchangeId);
        if (perProfile == null) {
            return List.of();
        }
        return perProfile.getOrDefault(profileId, perProfile.getOrDefault("default", List.of()));
    }

    private static BiPredicate<String, TransactionType> statusStartsWith(String s) {
        return (status, type) -> status != null && status.startsWith(s);
    }
    private static BiPredicate<String, TransactionType> statusContains(String s) {
        return (status, type) -> status != null && status.contains(s);
    }
    private static BiPredicate<String, TransactionType> statusContainsButNot(String yes, String no) {
        return (status, type) -> status != null && status.contains(yes) && !status.contains(no);
    }
    private static BiPredicate<String, TransactionType> typeIs(TransactionType t) {
        return (status, type) -> type == t;
    }
    private static BiPredicate<String, TransactionType> and(
        BiPredicate<String, TransactionType> a,
        BiPredicate<String, TransactionType> b) {
        return a.and(b);
    }
}