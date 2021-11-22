package io.everytrade.server.plugin.csv;


import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class CsvHeader {

    private static final String DEFAULT_SEPARATOR = ";";
    private static final String REGEX_PREFIX = "^";
    private static final String REGEX_POSTFIX = "$";
    private static final String QUOTE = "\"";
    private static final String MAGIC_MARK = "\uFEFF";

    private List<String> headerValues;
    private String separator;
    private boolean ordered = true; // if order of header values matters or not

    public CsvHeader(List<String> headerValues, String separator) {
        this.headerValues = headerValues;
        this.separator = separator;
    }

    public CsvHeader(List<String> headerValues, String separator, boolean ordered) {
        this.headerValues = headerValues;
        this.separator = separator;
        this.ordered = ordered;
    }

    public boolean matching(String headerLine) {
        if (headerLine == null || headerLine.isEmpty()) {
            return false;
        }
        List<String> vals = Arrays.asList(headerLine.split(separator));

        if (ordered) {
            return compareOrdered(vals);
        } else {
            return vals.stream().allMatch(val -> headerValues.stream().anyMatch(template -> compareValues(template, val)));
        }
    }

    private boolean compareOrdered(List<String> currentHeaderValues) {
        Iterator<String> currentIt = currentHeaderValues.iterator();
        Iterator<String> templateIt = headerValues.iterator();

        while (templateIt.hasNext()) {
            String template = templateIt.next();
            String current = null;
            while (currentIt.hasNext()) {
                current = currentIt.next();
                if (compareValues(template, current)) {
                    break; // template found move to next
                }
            }
            if (template != null && !compareValues(template, current)) {
                return false; // not found matching in given order
            }
        }
        return true; // all headers found in template
    }

    private boolean compareValues(String template, String value) {
        var templateValueCombinations = Stream.of(
            template,
            QUOTE + template + QUOTE,
            MAGIC_MARK + template,
            MAGIC_MARK + QUOTE + template + QUOTE
        );

        if (isHeaderTemplateRegex(template)) {
            return templateValueCombinations
                // move regex prefix to beginning and postfix to end of the string
                .map(t -> t.replace(REGEX_PREFIX, "").replace(REGEX_POSTFIX, ""))
                .map(t -> REGEX_PREFIX + t + REGEX_POSTFIX)
                .map(Pattern::compile)
                .anyMatch(pattern -> pattern.matcher(value).find());
        } else {
            return templateValueCombinations.anyMatch(it -> it.equalsIgnoreCase(value));
        }
    }

    private static boolean isHeaderTemplateRegex(String headerTemplate) {
        Objects.requireNonNull(headerTemplate);
        return (headerTemplate.startsWith("^") && headerTemplate.endsWith("$"));
    }

    public static CsvHeader of(String... headerValues) {
        return of(Arrays.asList(headerValues), DEFAULT_SEPARATOR);
    }

    public static CsvHeader of(List<String> headerValues) {
        return of(headerValues, DEFAULT_SEPARATOR);
    }

    public static CsvHeader of(List<String> headerValues, String separator) {
        return new CsvHeader(headerValues, separator);
    }

    public CsvHeader withSeparator(String separator) {
        return new CsvHeader(headerValues, separator, ordered);
    }
}
