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

/**
 * One row of a DASE account-transaction export.
 * <p>
 * The export is a flat dump of the very objects the DASE API returns, column for column, so this bean does
 * NOT interpret anything: it carries the row and converts it to an {@link ApiAccountTxn}. The meaning - which
 * rows form a trade, which leg is base and which is quote, that a withdrawal is three rows of which only
 * {@code withdrawal_commit} is a real movement - is then decided by the same code the API connector uses
 * ({@code XChangeApiTransaction.tradeFillGroupToCluster} / {@code fundingOrSingleTxnToCluster}).
 * <p>
 * That reuse is the point. The alternative, a second interpretation written for CSV, would be free to drift
 * from the API one, and the same account imported both ways would then disagree - which is far worse than a
 * failed import, because nothing on screen would say which number is right.
 * <p>
 * After the grouping step the parser hands the framework beans of this class again, each either CARRYING a
 * finished cluster or carrying a failure to report; see the two factory methods.
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
    private String txId;
    private String fundingId;
    private String tradeId;

    /** Set on the beans the parser returns AFTER grouping; null on a raw row. */
    private TransactionCluster preparedCluster;

    /** Set instead of {@link #preparedCluster} when the grouping step rejected the rows. */
    private String failure;
    private boolean failureIsIgnore;

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

    public static DaseBeanV1 carrying(TransactionCluster cluster) {
        final DaseBeanV1 bean = new DaseBeanV1();
        bean.preparedCluster = cluster;
        return bean;
    }

    /**
     * The framework reads {@code getParsingProblems()} BEFORE the grouping step runs, so a problem found
     * during grouping cannot be reported through that list - it would simply be dropped. Throwing from
     * {@link #toTransactionCluster()} is the channel that does work, and it lands in the same error log the
     * customer downloads.
     */
    public static DaseBeanV1 failing(String rowText, String message, boolean ignored) {
        final DaseBeanV1 bean = new DaseBeanV1();
        bean.failure = message;
        bean.failureIsIgnore = ignored;
        bean.setRowValues(new String[]{rowText});
        return bean;
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

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}
