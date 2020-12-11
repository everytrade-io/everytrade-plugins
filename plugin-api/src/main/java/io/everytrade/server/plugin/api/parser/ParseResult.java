package io.everytrade.server.plugin.api.parser;

import java.util.List;
import java.util.Objects;

public class ParseResult {
    private final List<TransactionCluster> transactionClusters;
    private final List<ParsingProblem> parsingProblems;

    public ParseResult(List<TransactionCluster> transactionClusters, List<ParsingProblem> parsingProblems) {
        Objects.requireNonNull(transactionClusters);
        Objects.requireNonNull(parsingProblems);
        this.transactionClusters = List.copyOf(transactionClusters);
        this.parsingProblems = List.copyOf(parsingProblems);
    }

    public List<TransactionCluster> getTransactionClusters() {
        return transactionClusters;
    }

    public List<ParsingProblem> getParsingProblems() {
        return parsingProblems;
    }
}
