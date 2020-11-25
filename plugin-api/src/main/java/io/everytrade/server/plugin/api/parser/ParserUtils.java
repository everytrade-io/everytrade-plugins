package io.everytrade.server.plugin.api.parser;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ParserUtils {
    public static final int DECIMAL_DIGITS = 10;

    public static Instant parse(String dateTimePattern, String dateTime) {
        final DateTimeFormatter dateTimeFormatter =
            DateTimeFormatter.ofPattern(dateTimePattern, Locale.US).withZone(ZoneOffset.UTC);

        return dateTimeFormatter.parse(dateTime, Instant::from);
    }

    public static int occurrenceCount(String input, String search) {
        int startIndex = 0;
        int index = 0;
        int counter = 0;
        while (index > -1 && startIndex < input.length()) {
            index = input.indexOf(search, startIndex);
            if (index > -1) {
                counter++;
            }
            startIndex = index + 1;
        }
        return counter;
    }


    public static List<String> splitInTwo(String input, String separator, int splitAtOccurrence) {
        List<String> parts = new ArrayList<>();
        int startIndex = 0;
        int index = 0;
        while (index > -1 && startIndex < input.length() && splitAtOccurrence > 0) {
            index = input.indexOf(separator, startIndex);
            if (--splitAtOccurrence == 0 && index > -1) {
                final String substringA = input.substring(0, index);
                final String substringB = input.substring(index + 1);
                if (!substringA.isEmpty()) {
                    parts.add(substringA);
                }
                if (!substringB.isEmpty()) {
                    parts.add(substringB);
                }
            }
            startIndex = index + 1;
        }
        return parts;
    }

    public static List<String> split(String input, String separator, boolean includeEmpty) {
        List<String> parts = new ArrayList<>();
        int startIndex = 0;
        int index = 0;
        String substring = "";
        while (index > -1 && startIndex < input.length()) {
            index = input.indexOf(separator, startIndex);
            if (index == -1) {
                substring = input.substring(startIndex);
            } else {
                substring = input.substring(startIndex, index);
            }
            if (includeEmpty || !substring.isEmpty()) {
                parts.add(substring);
            }
            startIndex = index + 1;
        }
        return parts;
    }
}
