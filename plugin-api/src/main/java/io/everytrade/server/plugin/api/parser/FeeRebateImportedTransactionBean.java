package io.everytrade.server.plugin.api.parser;

import io.everytrade.server.model.Currency;
import io.everytrade.server.model.TransactionType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

public class FeeRebateImportedTransactionBean extends ImportedTransactionBean {

    private final BigDecimal feeRebate;
    private final Currency feeRebateCurrency;

    public FeeRebateImportedTransactionBean(
        String uid,
        Instant executed,
        Currency base,
        Currency quote,
        TransactionType action,
        BigDecimal feeRebate,
        Currency feeRebateCurrency
    ) {
        this(
            uid,
            executed,
            base,
            quote,
            action,
            feeRebate,
            feeRebateCurrency,
            null
        );
    }

    public FeeRebateImportedTransactionBean(
        String uid,
        Instant executed,
        Currency base,
        Currency quote,
        TransactionType action,
        BigDecimal feeRebate,
        Currency feeRebateCurrency,
        String note

    ) {
        super(uid, executed, base, quote, action, note);
        Objects.requireNonNull(this.feeRebate = feeRebate);
        Objects.requireNonNull(this.feeRebateCurrency = feeRebateCurrency);
    }

    public BigDecimal getFeeRebate() {
        return feeRebate;
    }

    @Override
    public String toString() {
        return "FeeRebateImportedTransactionBean{" +
            "uid='" + getUid() + '\'' +
            ", executed=" + getExecuted() +
            ", base=" + getBase() +
            ", quote=" + getQuote() +
            ", action=" + getAction() +
            ", feeRebate=" + feeRebate +
            ", feeRebateCurrency=" + feeRebateCurrency +
            '}';
    }

    public Currency getFeeRebateCurrency() {
        return feeRebateCurrency;
    }
}
