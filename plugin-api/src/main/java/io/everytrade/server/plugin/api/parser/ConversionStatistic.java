package io.everytrade.server.plugin.api.parser;

import java.util.List;
import java.util.Objects;

public class ConversionStatistic {
    //TODO: rename to ParsingProblems?
    private final List<RowError> errorRows;

    //TODO: Unify Errors? Why is ignored fee different than RowErrorType.IGNORED?
    private final int ignoredFeeTransactionCount;

    public ConversionStatistic(List<RowError> errorRows, int ignoredFeeTransactionCount) {
        Objects.requireNonNull(errorRows);
        this.errorRows = List.copyOf(errorRows);
        this.ignoredFeeTransactionCount = ignoredFeeTransactionCount;
    }

    public List<RowError> getErrorRows() {
        return errorRows;
    }

    public boolean isErrorRowsEmpty() {
        return errorRows.isEmpty();
    }

    public int getFailedRowsCount() {
        return (int) errorRows
            .stream()
            .filter(rowError -> RowErrorType.FAILED.equals(rowError.getType()))
            .count();
    }

    //TODO: remove? Why is this needed? It's used only in tests.
    public int getIgnoredRowsCount() {
        return (int) errorRows
            .stream()
            .filter(rowError -> RowErrorType.IGNORED.equals(rowError.getType()))
            .count();
    }

    public int getIgnoredFeeTransactionCount() {
        return ignoredFeeTransactionCount;
    }

    @Override
    public String toString() {
        return "ConversionStatistic{" +
            "errorRows=" + errorRows +
            ", ignoredFeeTransactionCount=" + ignoredFeeTransactionCount +
            '}';
    }
}
