package io.everytrade.server.plugin.impl.everytrade.parser.exchange.dase;

import com.univocity.parsers.annotations.Headers;
import com.univocity.parsers.annotations.Parsed;
import io.everytrade.server.plugin.api.parser.TransactionCluster;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.ExchangeBean;
import io.everytrade.server.plugin.impl.everytrade.parser.exception.DataIgnoredException;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.knowm.xchange.dase.dto.account.ApiAccountTxn;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

/**
 * One row of a DASE account-transaction export.
 * <p>
 * The export is a flat dump of the very objects the DASE API returns, column for column, so this bean does
 * NOT interpret anything: it carries the row and converts it to an {@link ApiAccountTxn}. The meaning - which
 * rows form a trade, which leg is base and which is quote, that a withdrawal is three rows of which only
 * {@code withdrawal_commit} / {@code withdrawal} is a real movement - is then decided by the same code the API
 * connector uses ({@code XChangeApiTransaction.tradeFillGroupToCluster} / {@code fundingOrSingleTxnToCluster}).
 * <p>
 * The one exception is the Basic (instant-buy) portfolio's {@code trade payment / fill / refund} rows: they are
 * joined by {@code Order ID}, a column {@link ApiAccountTxn} has no field for, so {@link DaseBasicOrders} groups
 * them straight from these beans and hands each order to {@code XChangeApiTransaction.daseOrderToCluster}.
 * <p>
 * That reuse is the point. The alternative, a second interpretation written for CSV, would be free to drift
 * from the API one, and the same account imported both ways would then disagree - which is far worse than a
 * failed import, because nothing on screen would say which number is right.
 * <p>
 * After the grouping step the parser hands the framework beans of this class again, each either CARRYING a
 * finished cluster or carrying a failure to report; see the two factory methods. Both kinds also carry the text of
 * the CSV rows they came from, with their record numbers (see {@link #describe(List)}), because the framework can
 * still reject a carried cluster (host-side validation) and then writes {@link #rowToString()} into the
 * customer's error log.
 */
@EqualsAndHashCode(callSuper = true)
@Data
@Headers(
    sequence = {"Date", "Type", "Amount", "Currency", "Order ID", "dase TX ID", "Description", "Funding ID", "Trade ID"},
    extract = true
)
public class DaseBeanV1 extends ExchangeBean {

    private Instant date;
    private String type;
    private BigDecimal amount;
    private String currency;
    /** Joins the legs of a Basic-portfolio order; also present on some funding rows, where it must be ignored. */
    private String orderId;
    private String txId;
    private String fundingId;
    private String tradeId;

    /** Set on the beans the parser returns AFTER grouping; null on a raw row. */
    private TransactionCluster preparedCluster;

    /** Set instead of {@link #preparedCluster} when the grouping step rejected the rows. */
    private String failure;
    private boolean failureIsIgnore;

    /** The source rows of a carrying / failing bean as the error log shows them; null on a raw row. */
    private String sourceText;

    /** At most this many rows are written into the error-log text of one group (a cell there holds 32 767 chars). */
    static final int MAX_ROWS_IN_TEXT = 5;

    @Parsed(field = "Date")
    public void setDate(String date) {
        this.date = (date == null || date.isBlank()) ? null : Instant.parse(date.trim());
    }

    @Parsed(field = "Type")
    public void setType(String type) {
        this.type = type == null ? null : type.trim();
    }

    @Parsed(field = "Amount")
    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    @Parsed(field = "Currency")
    public void setCurrency(String currency) {
        this.currency = currency == null ? null : currency.trim();
    }

    @Parsed(field = "Order ID")
    public void setOrderId(String orderId) {
        this.orderId = blankToNull(orderId);
    }

    @Parsed(field = "dase TX ID")
    public void setTxId(String txId) {
        this.txId = blankToNull(txId);
    }

    @Parsed(field = "Funding ID")
    public void setFundingId(String fundingId) {
        this.fundingId = blankToNull(fundingId);
    }

    @Parsed(field = "Trade ID")
    public void setTradeId(String tradeId) {
        this.tradeId = blankToNull(tradeId);
    }

    /**
     * {@code createdAt} is epoch MILLIS: the shared mapping reads it back with
     * {@code Instant.ofEpochMilli} (XChangeApiTransaction, trade and funding paths alike), so seconds here
     * would place every imported transaction in January 1970.
     */
    public ApiAccountTxn toApiAccountTxn() {
        return new ApiAccountTxn(
            txId,
            currency,
            type,
            amount,
            date == null ? 0L : date.toEpochMilli(),
            tradeId,
            fundingId
        );
    }

    /**
     * A bean carrying a finished cluster made from {@code sources}. The framework still validates the cluster and
     * may reject it; the problem it then writes names these rows.
     */
    public static DaseBeanV1 carrying(TransactionCluster cluster, List<DaseBeanV1> sources) {
        final DaseBeanV1 bean = fromRows(sources);
        bean.preparedCluster = cluster;
        return bean;
    }

    /**
     * The framework reads {@code getParsingProblems()} BEFORE the grouping step runs, so a problem found
     * during grouping cannot be reported through that list - it would simply be dropped. Throwing from
     * {@link #toTransactionCluster()} is the channel that does work, and it lands in the same error log the
     * customer downloads, with the rows listed as {@link #describe(List)} writes them.
     */
    public static DaseBeanV1 failingRows(List<DaseBeanV1> rows, String message, boolean ignored) {
        final DaseBeanV1 bean = fromRows(rows);
        bean.failure = message;
        bean.failureIsIgnore = ignored;
        return bean;
    }

    /**
     * The error-log text of a group of rows: each row as {@code line=<record number>, <values>}, joined by
     * {@code " | "}, at most {@value #MAX_ROWS_IN_TEXT} rows followed by {@code " ... and <n> more rows"}. The
     * record number is the univocity record ordinal (header = 1): it equals the physical CSV line only while no
     * blank line or quoted line break precedes the row; the label stays {@code line=} as for every exchange. The
     * cap matters: one rejected group can be thousands of rows, and the host writes the text into one spreadsheet
     * cell.
     */
    static String describe(List<DaseBeanV1> rows) {
        final String text = rows.stream()
            .limit(MAX_ROWS_IN_TEXT)
            .map(DaseBeanV1::rowToString)
            .collect(Collectors.joining(" | "));
        return rows.size() > MAX_ROWS_IN_TEXT
            ? text + " ... and " + (rows.size() - MAX_ROWS_IN_TEXT) + " more rows"
            : text;
    }

    private static DaseBeanV1 fromRows(List<DaseBeanV1> rows) {
        final DaseBeanV1 bean = new DaseBeanV1();
        bean.sourceText = describe(rows);
        // Non-null row values: the framework drops a failed bean whose getRowValues() is null without a trace.
        bean.setRowValues(new String[]{bean.sourceText});
        if (!rows.isEmpty()) {
            bean.setRowId(rows.get(0).getRowId());
            bean.setRowNumber(rows.get(0).getRowId());
        }
        return bean;
    }

    /** True for the three row types of a Basic-portfolio order, which are joined by Order ID. */
    public boolean isBasicOrderRow() {
        return DaseBasicOrders.isOrderRowType(type);
    }

    @Override
    public TransactionCluster toTransactionCluster() {
        if (failure != null) {
            if (failureIsIgnore) {
                throw new DataIgnoredException(failure);
            }
            throw new IllegalStateException(failure);
        }
        if (preparedCluster != null) {
            return preparedCluster;
        }
        // A raw row never reaches here: convertMultipleRowsToTransactions replaces every one of them. Saying so
        // out loud beats returning null, which the framework would silently drop.
        throw new DataIgnoredException("DASE row was not grouped into a transaction: " + rowToString());
    }

    @Override
    public List<String> getRowValues() {
        return rowValues;
    }

    /**
     * A raw row: {@code line=<n>, <values>} as for every exchange (the parser sets the row number to the univocity
     * record number first). A carrying / failing bean: the text of its source rows, as is - prefixing it again
     * would print {@code line=0, } in front of rows that already carry their own record numbers.
     */
    @Override
    public String rowToString() {
        return sourceText != null ? sourceText : super.rowToString();
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}
