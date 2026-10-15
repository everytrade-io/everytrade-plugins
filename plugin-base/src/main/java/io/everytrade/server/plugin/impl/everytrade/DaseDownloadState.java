package io.everytrade.server.plugin.impl.everytrade;

import io.everytrade.server.util.serialization.DownloadState;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * What {@link DaseDownloader} remembers between sync runs (the host stores the serialized string as the connection's
 * {@code lastDownloadedTransactionUid} and hands it back on the next run).
 * <ul>
 *     <li>{@code newestId} - the forward watermark: the id of the newest ledger row already delivered. The next run
 *     starts at the top of the ledger and stops when it meets this id. Null until the first row is delivered.</li>
 *     <li>{@code gaps} - stretches of the ledger that a run had to leave unfetched because it hit its cap, newest
 *     first. Each is resumed with {@code before = gap.before} and ends at {@code gap.stopId} (exclusive), or at the
 *     end of the history when {@code stopId} is null - which is the backfill of a first history longer than one run.
 *     Normally empty.</li>
 * </ul>
 * Format: {@code DASE2|TOP=<id>|GAPS=<before>><stop>,<before>><stop>} with every id URL-encoded, so no id can break
 * the separators. Ids are DASE transaction ids, never compared by order.
 * <p>
 * Any other string is the {@code TRD=|DEP=<id>|WDR=<id>} state written by the downloader up to 5.2.306. It holds only
 * the OLDEST id that downloader fetched, never the newest one it imported, so this state cannot say where new rows
 * begin. Walking the ledger from the top would deliver the whole imported history again, and the host stores every
 * cluster it gets without de-duplicating ({@code ApiTxDownloader.saveTransactions}), so every earlier transaction
 * would be booked twice; any guessed stop point could skip rows without a trace. Such a state therefore reads as
 * {@link #isLegacyWithImportedRows() legacy} and the downloader refuses to run until the user re-synchronizes the
 * connection, which removes the container's transactions AND nulls the state on the host
 * ({@code ConnectionService.fullResyncTransactions}), after which the history is imported once. A legacy state
 * without any id ({@code TRD=|DEP=|WDR=}, written after a run that fetched nothing) means nothing was imported and
 * reads as an empty state.
 */
final class DaseDownloadState {
    private static final String PREFIX = "DASE2";
    private static final String SEP = "|";
    private static final String TOP = "TOP=";
    private static final String GAPS = "GAPS=";
    private static final String GAP_SEP = ",";
    private static final String STOP_SEP = ">";

    /** A stretch of the ledger not fetched yet: rows strictly older than {@code before}, down to {@code stopId}. */
    record Gap(String before, String stopId) {
        Gap {
            if (before == null || before.isBlank()) {
                throw new IllegalArgumentException("A DASE gap needs the id of the row it starts below");
            }
        }
    }

    private String newestId;
    private final List<Gap> gaps = new ArrayList<>();
    private boolean legacyWithImportedRows;

    static DaseDownloadState from(String serialized) {
        final DaseDownloadState s = new DaseDownloadState();
        if (serialized == null) {
            return s;
        }
        if (!serialized.startsWith(PREFIX + SEP)) {
            final DownloadState legacy = DownloadState.from(serialized);
            s.legacyWithImportedRows = legacy.getLastTradeId() != null || legacy.getLastDepositTs() != null
                || legacy.getLastWithdrawalTs() != null;
            return s;
        }
        for (String part : serialized.substring(PREFIX.length() + SEP.length()).split("\\" + SEP, -1)) {
            if (part.startsWith(TOP)) {
                s.newestId = decode(part.substring(TOP.length()));
            } else if (part.startsWith(GAPS) && part.length() > GAPS.length()) {
                for (String gap : part.substring(GAPS.length()).split(GAP_SEP, -1)) {
                    final int at = gap.indexOf(STOP_SEP);
                    if (at > 0) {
                        s.gaps.add(new Gap(decode(gap.substring(0, at)), decode(gap.substring(at + 1))));
                    }
                }
            }
        }
        return s;
    }

    String serialize() {
        final StringBuilder sb = new StringBuilder(PREFIX).append(SEP).append(TOP).append(encode(newestId))
            .append(SEP).append(GAPS);
        for (int i = 0; i < gaps.size(); i++) {
            if (i > 0) {
                sb.append(GAP_SEP);
            }
            sb.append(encode(gaps.get(i).before())).append(STOP_SEP).append(encode(gaps.get(i).stopId()));
        }
        return sb.toString();
    }

    /** A state of the downloader up to 5.2.306 that records rows it imported (see the class javadoc). */
    boolean isLegacyWithImportedRows() {
        return legacyWithImportedRows;
    }

    String getNewestId() {
        return newestId;
    }

    void setNewestId(String newestId) {
        this.newestId = newestId;
    }

    List<Gap> getGaps() {
        return List.copyOf(gaps);
    }

    void setGaps(List<Gap> newGaps) {
        gaps.clear();
        gaps.addAll(newGaps);
    }

    private static String encode(String id) {
        return id == null ? "" : URLEncoder.encode(id, StandardCharsets.UTF_8);
    }

    private static String decode(String value) {
        return value == null || value.isEmpty() ? null : URLDecoder.decode(value, StandardCharsets.UTF_8);
    }
}
