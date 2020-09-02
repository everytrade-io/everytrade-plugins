package io.everytrade.server.plugin.api.connector;

import io.everytrade.server.plugin.api.parser.ParseResult;

import java.util.Objects;

public class DownloadResult {
    private final ParseResult parseResult;
    private final String lastDownloadedTransactionId;

    public DownloadResult(ParseResult parseResult, String lastDownloadedTransactionId) {
        Objects.requireNonNull(this.parseResult = parseResult);
        this.lastDownloadedTransactionId = lastDownloadedTransactionId;
    }

    public ParseResult getParseResult() {
        return parseResult;
    }

    public String getLastDownloadedTransactionId() {
        return lastDownloadedTransactionId;
    }
}
