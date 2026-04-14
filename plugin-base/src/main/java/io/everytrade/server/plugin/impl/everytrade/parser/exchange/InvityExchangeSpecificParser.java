package io.everytrade.server.plugin.impl.everytrade.parser.exchange;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.stream.Collectors;

/**
 * Base class for Invity Finance exchange-specific parsers.
 * Provides shared functionality like quote stripping.
 */
public abstract class InvityExchangeSpecificParser extends DefaultUnivocityExchangeSpecificParser {

    protected InvityExchangeSpecificParser(Class<? extends ExchangeBean> exchangeBean, String delimiter) {
        super(exchangeBean, delimiter);
    }

    /**
     * Strips all double-quote characters from a CSV file.
     * Used for preprocessing Invity CSV exports that have escaped quotes.
     */
    protected void stripQuotesFromFile(File file) {
        try {
            String content = Files.lines(file.toPath(), StandardCharsets.UTF_8)
                .collect(Collectors.joining("\n"));
            content = content.replace("\"", "");
            Files.writeString(file.toPath(), content);
        } catch (IOException e) {
            throw new RuntimeException("Failed to preprocess Invity CSV file", e);
        }
    }
}
