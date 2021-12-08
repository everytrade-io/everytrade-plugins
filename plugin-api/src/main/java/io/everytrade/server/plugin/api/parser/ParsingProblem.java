package io.everytrade.server.plugin.api.parser;

import lombok.Value;

@Value
public class ParsingProblem {
    String row;
    String message;
    ParsingProblemType parsingProblemType;
}
