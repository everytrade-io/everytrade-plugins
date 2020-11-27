package io.everytrade.server.plugin.impl.everytrade.parser.postprocessor;

public class ConversionParams {
    private String dateTimePattern;

    public String getDateTimePattern() {
        return dateTimePattern;
    }

    public ConversionParams setDateTimePattern(String dateTimePattern) {
        this.dateTimePattern = dateTimePattern;
        return this;
    }
}
