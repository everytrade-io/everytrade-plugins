package io.everytrade.server.plugin.impl.everytrade.parser.exchange;

import io.everytrade.server.plugin.api.parser.ParsingProblem;

import java.io.File;
import java.util.List;

public interface IExchangeSpecificParser {
    List<? extends ExchangeBean> parse(File inputFile);
    List<ParsingProblem> getParsingProblems();
}
