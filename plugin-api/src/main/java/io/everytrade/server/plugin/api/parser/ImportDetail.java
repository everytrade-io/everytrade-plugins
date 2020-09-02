package io.everytrade.server.plugin.api.parser;

public class ImportDetail {
    private final boolean isIgnoredFee;

    public ImportDetail(boolean isIgnoredFee) {
        this.isIgnoredFee = isIgnoredFee;
    }

    public boolean isIgnoredFee() {
        return isIgnoredFee;
    }

    public static ImportDetail noError() {
        return new ImportDetail(false);
    }
}
