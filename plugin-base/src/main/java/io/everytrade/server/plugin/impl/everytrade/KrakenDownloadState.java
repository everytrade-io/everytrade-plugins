package io.everytrade.server.plugin.impl.everytrade;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static io.everytrade.server.plugin.impl.everytrade.ConnectorUtils.occurrenceCount;

public class KrakenDownloadState {
    public static final int MAX_LENGTH_DOWNLOADED_TXUID = 255;
    private String lastContinuousTxUid;
    private String firstTxUidAfterGap;
    private String lastTxUidAfterGap;

    private static final String TX_ID_SEPARATOR = ":";
    private static final Pattern SPLIT_PATTERN = Pattern.compile(
        String.format("^([^%s]*)%s([^%s]*)%s([^%s]*)$",
            TX_ID_SEPARATOR,
            TX_ID_SEPARATOR,
            TX_ID_SEPARATOR,
            TX_ID_SEPARATOR,
            TX_ID_SEPARATOR
        )
    );

    public KrakenDownloadState(
        String lastContinuousTxUid,
        String firstTxUidAfterGap,
        String lastTxUidAfterGap
    ) {
        this.lastContinuousTxUid = lastContinuousTxUid;
        this.firstTxUidAfterGap = firstTxUidAfterGap;
        this.lastTxUidAfterGap = lastTxUidAfterGap;
    }

    public String getLastContinuousTxUid() {
        return lastContinuousTxUid;
    }

    public String getFirstTxUidAfterGap() {
        return firstTxUidAfterGap;
    }

    public String getLastTxUidAfterGap() {
        return lastTxUidAfterGap;
    }

    public void setLastContinuousTxUid(String lastContinuousTxUid) {
        this.lastContinuousTxUid = lastContinuousTxUid;
    }

    public void setFirstTxUidAfterGap(String firstTxUidAfterGap) {
        this.firstTxUidAfterGap = firstTxUidAfterGap;
    }

    public void setLastTxUidAfterGap(String lastTxUidAfterGap) {
        this.lastTxUidAfterGap = lastTxUidAfterGap;
    }

    public String toLastDownloadedTxUid() {
        final String result = new StringBuilder()
            .append(lastContinuousTxUid == null ? "" : lastContinuousTxUid).append(TX_ID_SEPARATOR)
            .append(firstTxUidAfterGap == null ? "" : firstTxUidAfterGap).append(TX_ID_SEPARATOR)
            .append(lastTxUidAfterGap == null ? "" : lastTxUidAfterGap).toString();
        if (result.length() > MAX_LENGTH_DOWNLOADED_TXUID) {
            throw new IllegalStateException(
                String.format(
                    "Downloaded transaction UID is too long (%d > %d). ",
                    result.length(),
                    MAX_LENGTH_DOWNLOADED_TXUID
                )
            );
        }
        return result;
    }

    public static KrakenDownloadState parseFrom(String lastTransactionUid) {
        if (lastTransactionUid == null) {
            return new KrakenDownloadState(null, null, null);
        }

        Matcher matcher = SPLIT_PATTERN.matcher(lastTransactionUid);
        if (!matcher.find()) {
            throw new IllegalArgumentException(
                String.format("Illegal value of lastTransactionUid '%s'.", lastTransactionUid)
            );
        }

        return new KrakenDownloadState(
            getGroupValueOrNull(matcher, 1),
            getGroupValueOrNull(matcher, 2),
            getGroupValueOrNull(matcher, 3)
        );
    }

    private static String getGroupValueOrNull(Matcher matcher, int group) {
        final String part = matcher.group(group);
        return part.isEmpty() ? null : part;
    }
}
