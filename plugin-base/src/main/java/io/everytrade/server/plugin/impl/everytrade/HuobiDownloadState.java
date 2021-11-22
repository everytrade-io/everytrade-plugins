package io.everytrade.server.plugin.impl.everytrade;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class HuobiDownloadState {
    public static final Duration MAX_TRANSACTION_HISTORY_PERIOD = Duration.ofDays(179);
    private static final Pattern SPLIT_PATTERN = Pattern.compile("^([^:]*):([^:]*):([^:]*):([^:]*)$");
    private static final int MAX_LAST_TX_ID_LENGTH = 255;

    private LocalDate windowStart;
    private String lastContinuousTxId;
    private String firstTxIdAfterGap;
    private String lastTxIdAfterGap;
    private boolean end;


    private HuobiDownloadState(
        LocalDate windowStart,
        String lastContinuousTxId,
        String firstTxIdAfterGap,
        String lastTxIdAfterGap
    ) {
        if (windowStart.compareTo(LocalDate.now(ZoneOffset.UTC)) > 0) {
            throw new IllegalArgumentException("Window start can't be in future.");
        }
        this.windowStart = windowStart;
        this.lastContinuousTxId = lastContinuousTxId;
        this.firstTxIdAfterGap = firstTxIdAfterGap;
        this.lastTxIdAfterGap = lastTxIdAfterGap;
        this.end = false;
    }

    public Date getWindowStart() {
        return convert(windowStart);
    }

    public Date getWindowEnd() {
        return convert(windowStart.plusDays(1));
    }

    public void moveToNextWindow() {
        final boolean isTodayOrYesterday
            = windowStart.compareTo(LocalDate.now(ZoneOffset.UTC).minusDays(1)) >= 0;
        if (isTodayOrYesterday) {
            end = true;
        } else {
            windowStart = windowStart.plusDays(2);
            lastContinuousTxId = null;
            lastTxIdAfterGap = null;
            firstTxIdAfterGap = null;
        }
    }

    public String getLastContinuousTxId() {
        return lastContinuousTxId;
    }

    public String getFirstTxIdAfterGap() {
        return firstTxIdAfterGap;
    }

    public void setLastContinuousTxId(String lastContinuousTxId) {
        this.lastContinuousTxId = lastContinuousTxId;
    }

    public void setFirstTxIdAfterGap(String firstTxIdAfterGap) {
        this.firstTxIdAfterGap = firstTxIdAfterGap;
    }

    public void setLastTxIdAfterGap(String lastTxIdAfterGap) {
        this.lastTxIdAfterGap = lastTxIdAfterGap;
    }

    public boolean isEnd() {
        return end;
    }

    public void closeGap() {
        lastContinuousTxId = lastTxIdAfterGap;
        lastTxIdAfterGap = null;
        firstTxIdAfterGap = null;
    }

    public boolean isGap() {
        return firstTxIdAfterGap != null;
    }

    public boolean isFirstDownloadInWindow() {
        return lastContinuousTxId == null;
    }

    @Override
    public String toString() {
        return new StringBuilder()
            .append(windowStart.toString()).append(":")
            .append(lastContinuousTxId == null ? "" : lastContinuousTxId).append(":")
            .append(firstTxIdAfterGap == null ? "" : firstTxIdAfterGap).append(":")
            .append(lastTxIdAfterGap == null ? "" : lastTxIdAfterGap)
            .toString();
    }

    public static HuobiDownloadState parseFrom(String lastTransactionId) {
        if (lastTransactionId == null) {
            return new HuobiDownloadState(
                LocalDate.now(ZoneOffset.UTC).minusDays(MAX_TRANSACTION_HISTORY_PERIOD.toDays()),
                null,
                null,
                null
            );
        }

        Matcher matcher = SPLIT_PATTERN.matcher(lastTransactionId);
        if (!matcher.find()) {
            throw new IllegalArgumentException(
                String.format("Illegal value of lastTransactionId '%s'.", lastTransactionId)
            );
        }
        final String startDate = getGroupValueOrNull(matcher, 1);
        return new HuobiDownloadState(
            startDate == null
                ? LocalDate.now(ZoneOffset.UTC).minusDays(MAX_TRANSACTION_HISTORY_PERIOD.toDays())
                : LocalDate.parse(startDate),
            getGroupValueOrNull(matcher, 2),
            getGroupValueOrNull(matcher, 3),
            getGroupValueOrNull(matcher, 4)
        );
    }

    private static String getGroupValueOrNull(Matcher matcher, int group) {
        final String part = matcher.group(group);
        return part.isEmpty() ? null : part;
    }

    private Date convert(LocalDate dateToConvert) {
        return java.util.Date.from(dateToConvert.atStartOfDay()
            .atZone(ZoneOffset.UTC)
            .toInstant());
    }

    public static Map<String, HuobiDownloadState> deserializeState(String lastState) {
        if (lastState == null) {
            return new HashMap<>();
        } else {
            return Arrays.stream(lastState.split("\\|"))
                .map(entry -> entry.split("="))
                .collect(Collectors.toMap(entry -> entry[0], entry -> HuobiDownloadState.parseFrom(entry[1])));
        }
    }

    public static String serializeState(Map<String, HuobiDownloadState> state) {
        String result = state.keySet().stream()
            .map(key -> key + "=" + state.get(key).toString())
            .collect(Collectors.joining("|"));
        if (result.length() > MAX_LAST_TX_ID_LENGTH) {
            throw new IllegalStateException(String.format(
                "Last transaction ID's size '%d' is over limit.", result.length()
            ));
        }
        return result;
    }
}
