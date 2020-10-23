package io.everytrade.server.plugin.api.parser;

import io.everytrade.server.model.Currency;
import io.everytrade.server.model.TransactionType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

public class ImportedTransactionBean {
    private final Logger log = LoggerFactory.getLogger(this.getClass());

    private final String uid;
    private final Instant executed;
    private final Currency base;
    private final Currency quote;
    private final TransactionType action;
    private final BigDecimal baseQuantity;
    private final BigDecimal unitPrice;
    private final BigDecimal feeQuote;
    private final Instant imported = Instant.now();
    private final ImportDetail importDetail;

    public ImportedTransactionBean(
        String uid,
        Instant executed,
        Currency base,
        Currency quote,
        TransactionType action,
        BigDecimal baseQuantity,
        BigDecimal unitPrice,
        BigDecimal feeQuote
    ) {
        this(
            uid,
            executed,
            base,
            quote,
            action,
            baseQuantity,
            unitPrice,
            feeQuote,
            ImportDetail.noError()
        );
    }

    public ImportedTransactionBean(
        String uid,
        Instant executed,
        Currency base,
        Currency quote,
        TransactionType action,
        BigDecimal baseQuantity,
        BigDecimal unitPrice,
        BigDecimal feeQuote,
        ImportDetail importDetail
    ) {
        this.uid = uid; //TODO: fix NULL uids with synthetic ones, otherwise API import might fail
        Objects.requireNonNull(this.executed = executed);
        Objects.requireNonNull(this.base = base);
        Objects.requireNonNull(this.quote = quote);
        Objects.requireNonNull(this.action = action);
        Objects.requireNonNull(this.baseQuantity = baseQuantity);
        Objects.requireNonNull(this.unitPrice = unitPrice);
        this.feeQuote = feeQuote;
        Objects.requireNonNull(this.importDetail = importDetail);
    }

    public ImportDetail getImportDetail() {
        return importDetail;
    }

    public Logger getLog() {
        return log;
    }

    public String getUid() {
        return uid;
    }

    public Instant getExecuted() {
        return executed;
    }

    public Currency getBase() {
        return base;
    }

    public Currency getQuote() {
        return quote;
    }

    public TransactionType getAction() {
        return action;
    }

    public BigDecimal getBaseQuantity() {
        return baseQuantity;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public BigDecimal getFeeQuote() {
        return feeQuote;
    }

    public Instant getImported() {
        return imported;
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + "{" +
            "uid='" + uid + '\'' +
            ", executed=" + executed +
            ", base=" + base +
            ", quote=" + quote +
            ", action=" + action +
            ", baseQuantity=" + baseQuantity +
            ", unitPrice=" + unitPrice +
            ", feeQuote=" + feeQuote +
            '}';
    }
}
