package io.everytrade.server.plugin.api.parser;

import lombok.NonNull;
import lombok.Value;

import java.util.List;

@Value
public class ParseResult {
    @NonNull List<TransactionCluster> transactionClusters;
    @NonNull List<ParsingProblem> parsingProblems;
}
