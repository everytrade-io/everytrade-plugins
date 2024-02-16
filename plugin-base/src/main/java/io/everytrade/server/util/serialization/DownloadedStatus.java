package io.everytrade.server.util.serialization;

import lombok.Getter;

@Getter
public enum DownloadedStatus {
    ALL_DATA_DOWNLOADED("A"),
    PARTIAL_DATA_DOWNLOADED("P");

    String code;

    private DownloadedStatus(String code) {
        this.code = code;
    }

    public static DownloadedStatus fromCode(String code) {
        for (DownloadedStatus status : DownloadedStatus.values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("No matching SequenceIdentifierType found for code " + code);
    }

}

