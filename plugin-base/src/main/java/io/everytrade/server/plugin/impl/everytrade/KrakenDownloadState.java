package io.everytrade.server.plugin.impl.everytrade;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

import static lombok.AccessLevel.PRIVATE;
import static org.apache.commons.lang.StringUtils.isEmpty;

@Data
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = PRIVATE)
public class KrakenDownloadState {

    private static final int MAX_LENGTH_DOWNLOADED_TXUID = 255;
    private static final String SERIALIZATION_SEPARATOR = ":";

    String tradeLastContinuousTxUid;
    String tradeFirstTxUidAfterGap;
    String tradeLastTxUidAfterGap;
    Long depositFromTimestamp;
    Long withdrawalFromTimestamp;
    Long stakeLastTimestamp;
    String spendReceiveFromUnixTimestamp;

    public String serialize() {
        final String result = new StringBuilder()
            .append(tradeLastContinuousTxUid == null ? "" : tradeLastContinuousTxUid).append(SERIALIZATION_SEPARATOR)
            .append(tradeFirstTxUidAfterGap == null ? "" : tradeFirstTxUidAfterGap).append(SERIALIZATION_SEPARATOR)
            .append(tradeLastTxUidAfterGap == null ? "" : tradeLastTxUidAfterGap).append(SERIALIZATION_SEPARATOR)
            .append(depositFromTimestamp == null ? "" :  depositFromTimestamp).append(SERIALIZATION_SEPARATOR)
            .append(withdrawalFromTimestamp == null ? "" :  withdrawalFromTimestamp).append(SERIALIZATION_SEPARATOR)
            .append(stakeLastTimestamp == null ? "" : stakeLastTimestamp).append(SERIALIZATION_SEPARATOR)
            .append(spendReceiveFromUnixTimestamp == null ? "" : spendReceiveFromUnixTimestamp)
            .toString();

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

    public static KrakenDownloadState deserialize(String downloadState) {
        if (downloadState == null) {
            return new KrakenDownloadState();
        }

        String[] splitValues = downloadState.split(SERIALIZATION_SEPARATOR);

        String depositTimestamp = getGroupValueOrNull(splitValues, 4);
        String withdrawalTimestamp = getGroupValueOrNull(splitValues, 5);
        String stakeLastTimestamp = getGroupValueOrNull(splitValues, 6);
        String spendReceiveUnixTimestamp = getGroupValueOrNull(splitValues, 7);
        return new KrakenDownloadState(
            getGroupValueOrNull(splitValues, 1),
            getGroupValueOrNull(splitValues, 2),
            getGroupValueOrNull(splitValues, 3),
            depositTimestamp == null ? null : Long.valueOf(depositTimestamp),
            withdrawalTimestamp == null ? null : Long.valueOf(withdrawalTimestamp),
            stakeLastTimestamp == null ? null : Long.valueOf(stakeLastTimestamp),
            spendReceiveUnixTimestamp == null ? null : spendReceiveUnixTimestamp
        );
    }

    public boolean hasEmptyState() {
        return tradeLastContinuousTxUid == null &&
            tradeFirstTxUidAfterGap == null &&
            tradeLastTxUidAfterGap == null &&
            depositFromTimestamp == null &&
            withdrawalFromTimestamp == null &&
            stakeLastTimestamp == null &&
            spendReceiveFromUnixTimestamp == null;
    }

    private static String getGroupValueOrNull(String[] values, int group) {
        if (values.length < group) {
            return null;
        }
        var val = values[group - 1];
        return isEmpty(val) ? null : val;
    }
}
