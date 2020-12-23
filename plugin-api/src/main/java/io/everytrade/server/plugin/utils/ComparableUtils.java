package io.everytrade.server.plugin.utils;

import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;

public class ComparableUtils {
    private ComparableUtils() {
    }

    public static <T extends Comparable<? super T>> T min(T c1, T c2) {
        if (c1 == null && c2 == null) {
            throw new IllegalArgumentException("Both comparables can't be null.");
        }
        if (c1 == null) {
            return c2;
        }
        if (c2 == null) {
            return c1;
        }
        return c1.compareTo(c2) <= 0 ? c1 : c2;
    }

    public static <T extends Comparable<? super T>> T max(T c1, T c2) {
        if (c1 == null && c2 == null) {
            throw new IllegalArgumentException("Both comparables can't be null.");
        }
        if (c1 == null) {
            return c2;
        }
        if (c2 == null) {
            return c1;
        }
        return c1.compareTo(c2) >= 0 ? c1 : c2;
    }

    public static <T extends Comparable<? super T>> T min(Collection<T> comparables) {
        return comparables.stream().filter(Objects::nonNull).min(T::compareTo).orElseThrow();
    }

    public static <T extends Comparable<? super T>> T max(Collection<T> comparables) {
        return comparables.stream().filter(Objects::nonNull).max(T::compareTo).orElseThrow();
    }

    @SafeVarargs
    public static <T extends Comparable<? super T>> T min(T... comparables) {
        return Arrays.stream(comparables).filter(Objects::nonNull).min(T::compareTo).orElseThrow();
    }

    @SafeVarargs
    public static <T extends Comparable<? super T>> T max(T... comparables) {
        return Arrays.stream(comparables).filter(Objects::nonNull).max(T::compareTo).orElseThrow();
    }

    @SafeVarargs
    public static <T extends Comparable<? super T>> T minOrDefault(T def, T... timestamps) {
        return Arrays.stream(timestamps).filter(Objects::nonNull).min(T::compareTo).orElse(def);
    }

    @SafeVarargs
    public static <T extends Comparable<? super T>> T maxOrDefault(T def, T... timestamps) {
        return Arrays.stream(timestamps).filter(Objects::nonNull).max(T::compareTo).orElse(def);
    }
}
