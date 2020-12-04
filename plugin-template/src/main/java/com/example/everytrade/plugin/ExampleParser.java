package com.example.everytrade.plugin;

import io.everytrade.server.model.SupportedExchange;
import io.everytrade.server.plugin.api.IPlugin;
import io.everytrade.server.plugin.api.parser.ICsvParser;
import io.everytrade.server.plugin.api.parser.ParseResult;
import io.everytrade.server.plugin.api.parser.ParserDescriptor;

import java.io.File;
import java.util.Map;

public class ExampleParser implements ICsvParser {
    public static final String ID = ExamplePlugin.ID + IPlugin.PLUGIN_PATH_SEPARATOR + "exampleParser";

    public static final ParserDescriptor DESCRIPTOR = new ParserDescriptor(
        ID,
        Map.of("UID;DATE;SYMBOL;ACTION;QUANTY;PRICE;FEE", SupportedExchange.EVERYTRADE)
    );

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public ParseResult parse(File file, String header) {
        throw new UnsupportedOperationException("Implement me!");
    }
}
