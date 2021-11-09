package io.everytrade.server.parser.exchange;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.univocity.parsers.common.DataValidationException;
import io.everytrade.server.model.Currency;
import io.everytrade.server.model.CurrencyPair;
import io.everytrade.server.model.TransactionType;
import io.everytrade.server.plugin.api.parser.BuySellImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.FeeRebateImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.ImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.TransactionCluster;
import io.everytrade.server.plugin.impl.everytrade.parser.ParserUtils;
import io.everytrade.server.plugin.impl.everytrade.parser.exception.DataIgnoredException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.ExchangeBean.FEE_UID_PART;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.ExchangeBean.UNSUPPORTED_TRANSACTION_TYPE;
import static io.everytrade.server.plugin.impl.generalbytes.GbPlugin.parseGbCurrency;

@JsonFormat(shape = JsonFormat.Shape.ARRAY)
@JsonPropertyOrder(
    {
        "uid",
        "timestamp",
        "base",
        "quote",
        "action",
        "quantity",
        "volume",
        "expense",
        "expenseCurrency",
        "status"
    }
)
public class GbApiTransactionBean {

    private static final String WITHDRAW_ACTION = "WITHDRAW";

    private String uid;
    private Instant timestamp;
    private String base;
    private String quote;
    private String action;
    private BigDecimal quantity;
    private BigDecimal volume;
    private BigDecimal expense;
    private String expenseCurrency;
    private String status;

    public void setUid(String uid) {
        this.uid = uid;
    }

    public void setTimestamp(Date timestamp) {
        this.timestamp = timestamp.toInstant();
    }

    public void setBase(String symbol) {
        base = symbol;
    }

    public void setQuote(String symbol) {
        quote = symbol;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }

    public void setVolume(BigDecimal volume) {
        this.volume = volume;
    }

    public void setExpense(BigDecimal expense) {
        this.expense = expense;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public void setExpenseCurrency(String expenseCurrency) {
        this.expenseCurrency = expenseCurrency;
    }

    public String getUid() {
        return uid;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public String getBase() {
        return base;
    }

    public String getQuote() {
        return quote;
    }

    public String getAction() {
        return action;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public BigDecimal getVolume() {
        return volume;
    }

    public BigDecimal getExpense() {
        return expense;
    }

    public String getExpenseCurrency() {
        return expenseCurrency;
    }

    public String getStatus() {
        return status;
    }

    public TransactionCluster toTransactionCluster() {
        final Currency baseCurrency = parseGbCurrency(base);
        final Currency quoteCurrency = parseGbCurrency(quote);
        try {
            new CurrencyPair(baseCurrency, quoteCurrency);
        } catch (CurrencyPair.FiatCryptoCombinationException e) {
            throw new DataValidationException(e.getMessage());
        }
        final boolean isIgnoredFee = !(base.equals(expenseCurrency) || quote.equals(expenseCurrency));
        List<ImportedTransactionBean> related;
        if (ParserUtils.equalsToZero(expense) || isIgnoredFee) {
            related = Collections.emptyList();
        } else {
            related = List.of(new FeeRebateImportedTransactionBean(
                    uid + FEE_UID_PART,
                    timestamp,
                    baseCurrency,
                    quoteCurrency,
                    TransactionType.FEE,
                    expense,
                    Currency.fromCode(expenseCurrency),
                    getRemoteUid()
                )
            );
        }

        TransactionCluster cluster = new TransactionCluster(
            new BuySellImportedTransactionBean(
                uid,
                timestamp,
                baseCurrency,
                quoteCurrency,
                actionToTransactionType(),
                quantity,
                volume.divide(quantity, 10, RoundingMode.HALF_UP),
                getRemoteUid()
            ),
            related
        );
        if (isIgnoredFee) {
            cluster.setIgnoredFee(1, "Fee " + expenseCurrency + " currency is neither base or quote");
        }
        return cluster;
    }

    public boolean isImportable() {
        return (status == null || isImportable(status)) && isActionImportable();
    }

    public boolean isActionImportable() {
        return action != null && !WITHDRAW_ACTION.equals(action);
    }

    public static boolean isImportable(String status) {
        return status.startsWith("COMPLETED") ||
            status.contains("PAYMENT ARRIVED") ||
            status.contains("ERROR (EXCHANGE PURCHASE)");
    }

    public boolean isIgnored() {
        return !isActionImportable();
    }

    public String getIgnoreReason() {
        if (WITHDRAW_ACTION.equals(action)) {
            return WITHDRAW_ACTION + " TRANSATION";
        }
        return null;
    }

    @Override
    public String toString() {
        return "GbApiTransactionBean{" +
            "uid='" + uid + '\'' +
            ", timestamp=" + timestamp +
            ", base='" + base + '\'' +
            ", quote='" + quote + '\'' +
            ", action='" + action + '\'' +
            ", quantity=" + quantity +
            ", volume=" + volume +
            ", expense=" + expense +
            ", expenseCurrency=" + expenseCurrency +
            ", status=" + status +
            '}';
    }

    private String getRemoteUid() {
        if (uid == null) {
            return null;
        }
        final String[] split = uid.split("-");
        if (split.length != 2) {
            return null;
        }
        return split[1];
    }

    private TransactionType actionToTransactionType() {
        if ("SELL".equals(action)) {
            return TransactionType.BUY;
        } else if("BUY".equals(action)) {
            return TransactionType.SELL;
        } else {
            throw new DataIgnoredException(UNSUPPORTED_TRANSACTION_TYPE.concat(action));
        }
    }
}
