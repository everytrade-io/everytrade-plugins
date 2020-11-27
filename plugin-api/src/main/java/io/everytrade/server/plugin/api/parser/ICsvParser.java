package io.everytrade.server.plugin.api.parser;

import java.io.File;

public interface ICsvParser {
    String getId();
    ParseResult parse(File file, String signature);
}

