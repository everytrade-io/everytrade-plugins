package io.everytrade.server.plugin.api.parser;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NonNull;
import lombok.Value;

import java.util.ArrayList;
import java.util.List;

@Value
@Builder
@AllArgsConstructor
public class ParseResult {

    @NonNull
    @Builder.Default
    List<TransactionCluster> transactionClusters = new ArrayList<>();

    @NonNull
    @Builder.Default
    List<ParsingProblem> parsingProblems = new ArrayList<>();
}
