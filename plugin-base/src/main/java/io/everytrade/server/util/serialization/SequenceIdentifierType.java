package io.everytrade.server.util.serialization;

import lombok.Getter;

@Getter
public enum SequenceIdentifierType {
    STATUS("st"),
    START("s"),
    OFFSET("o"),
    END("e");

    String code;

    SequenceIdentifierType(String code) {
        this.code = code;
    }

    public static SequenceIdentifierType fromCode(String code) {
        for (SequenceIdentifierType identifierType : SequenceIdentifierType.values()) {
            if (identifierType.code.equals(code)) {
                return identifierType;
            }
        }
        throw new IllegalArgumentException("No matching SequenceIdentifierType found for code " + code);
    }

}

