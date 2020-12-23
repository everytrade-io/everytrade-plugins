package io.everytrade.server.plugin.api.parser;

import java.io.File;

public interface ICsvParser extends AutoCloseable {
    /**
     * Returns the parser's identifier. Should be unique among all the parent plugin's parsers.
     * @return parser ID
     */
    String getId();

    /**
     * Parse the specified file.
     * @param file CSV file to be parsed.
     * @param header CVS file's header that has used to identify this parser.
     * @return parsing result (parsed data and parsing process metadata like statistics, errors, etc.)
     */
    ParseResult parse(File file, String header);

    /**
     * {@inheritDoc}
     */
    default void close() {
    }
}

