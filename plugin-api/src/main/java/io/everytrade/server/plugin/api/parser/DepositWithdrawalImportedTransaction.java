package io.everytrade.server.plugin.api.parser;

import io.everytrade.server.model.Currency;
import io.everytrade.server.model.TransactionType;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
public class DepositWithdrawalImportedTransaction extends ImportedTransactionBean {

    private final BigDecimal volume;

    public DepositWithdrawalImportedTransaction(
        String uid,
        Instant executed,
        Currency base,
        Currency quote,
        TransactionType action,
        BigDecimal volume
    ) {
        this(
            uid,
            executed,
            base,
            quote,
            action,
            volume,
            null
        );
    }

    public DepositWithdrawalImportedTransaction(
        String uid,
        Instant executed,
        Currency base,
        Currency quote,
        TransactionType action,
        BigDecimal volume,
        String note
    ) {
        super(uid, executed, base, quote, action, note);
        this.volume = volume;
    }
}
