package io.everytrade.server.plugin.api.parser;

import io.everytrade.server.model.Currency;
import io.everytrade.server.model.TransactionType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

public class BuySellImportedTransactionBean extends ImportedTransactionBean {
    private final BigDecimal baseQuantity;
    private final BigDecimal unitPrice;

    public BuySellImportedTransactionBean(
        String uid,
        Instant executed,
        Currency base,
        Currency quote,
        TransactionType action,
        BigDecimal baseQuantity,
        BigDecimal unitPrice
    ) {
        this(
            uid,
            executed,
            base,
            quote,
            action,
            baseQuantity,
            unitPrice,
            null
        );
    }

    public BuySellImportedTransactionBean(
        String uid,
        Instant executed,
        Currency base,
        Currency quote,
        TransactionType action,
        BigDecimal baseQuantity,
        BigDecimal unitPrice,
        String note

    ) {
        super(
            uid,
            executed,
            base,
            quote,
            action,
            note
        );

        Objects.requireNonNull(this.baseQuantity = baseQuantity);
        this.unitPrice = unitPrice; // if is null it will be automatically added from the market in everytrade.
    }

    public BigDecimal getBaseQuantity() {
        return baseQuantity;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }


    @Override
    public String toString() {
        return "BuySellImportedTransactionBean{" +
            "uid='" + getUid() + '\'' +
            ", executed=" + getExecuted() +
            ", base=" + getBase() +
            ", quote=" + getQuote() +
            ", action=" + getAction() +
            ", baseQuantity=" + baseQuantity +
            ", unitPrice=" + unitPrice +
            '}';
    }
}
