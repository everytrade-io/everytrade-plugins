package io.everytrade.server.plugin.utils;

import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

public class HeaderTemplateFinder {
    private HeaderTemplateFinder() {
    }

    public static String findHeaderTemplate(String header, Set<String> headerTemplates) {
        Objects.requireNonNull(header);
        Objects.requireNonNull(headerTemplates);

        if (headerTemplates.contains(header)) {
            return header;
        }
        for (String headerTemplate : headerTemplates) {
            if (isHeaderTemplateRegex(headerTemplate)) {
                final Pattern compile = Pattern.compile(headerTemplate);
                if (compile.matcher(header).find()) {
                    return headerTemplate;
                }
            }
        }
        return null;
    }

    private static boolean isHeaderTemplateRegex(String headerTemplate) {
        Objects.requireNonNull(headerTemplate);
        return (headerTemplate.startsWith("^") && headerTemplate.endsWith("$"));
    }
}
