package io.everytrade.server.plugin.api.parser;

import io.everytrade.server.model.SupportedExchange;

import java.io.File;

public interface ICsvParser {
    String getId();
    ParseResult parse(File file);
    SupportedExchange detectExchange(File file);
}

