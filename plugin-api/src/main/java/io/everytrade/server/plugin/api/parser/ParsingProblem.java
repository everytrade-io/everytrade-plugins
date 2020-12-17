package io.everytrade.server.plugin.api.parser;

public class ParsingProblem {
    private final String row;
    private final String message;
    private final ParsingProblemType parsingProblemType;

    public ParsingProblem(String row, String message, ParsingProblemType parsingProblemType) {
        this.row = row;
        this.message = message;
        this.parsingProblemType = parsingProblemType;
    }

    public String getRow() {
        return row;
    }

    public String getMessage() {
        return message;
    }

    public ParsingProblemType getPrarsingProblemType() {
        return parsingProblemType;
    }

    @Override
    public String toString() {
        return "ParsingProblem{" +
            "row='" + row + '\'' +
            ", message='" + message + '\'' +
            ", prarsingProblemType=" + parsingProblemType +
            '}';
    }
}
