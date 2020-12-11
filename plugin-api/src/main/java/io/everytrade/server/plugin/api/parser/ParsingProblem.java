package io.everytrade.server.plugin.api.parser;

public class ParsingProblem {
    private final String row;
    private final String message;
    private final PrarsingProblemType prarsingProblemType;

    public ParsingProblem(String row, String message, PrarsingProblemType prarsingProblemType) {
        this.row = row;
        this.message = message;
        this.prarsingProblemType = prarsingProblemType;
    }

    public String getRow() {
        return row;
    }

    public String getMessage() {
        return message;
    }

    public PrarsingProblemType getPrarsingProblemType() {
        return prarsingProblemType;
    }

    @Override
    public String toString() {
        return "ParsingProblem{" +
            "row='" + row + '\'' +
            ", message='" + message + '\'' +
            ", prarsingProblemType=" + prarsingProblemType +
            '}';
    }
}
