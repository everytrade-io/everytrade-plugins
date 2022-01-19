package io.everytrade.server.plugin.api.parser;

import io.everytrade.server.model.Currency;
import io.everytrade.server.model.TransactionType;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.Instant;

import static lombok.AccessLevel.PRIVATE;

@Getter
@FieldDefaults(makeFinal = true, level= PRIVATE)
public class DepositWithdrawalImportedTransaction extends ImportedTransactionBean {

    BigDecimal volume;
    String address;

    public DepositWithdrawalImportedTransaction(
        String uid,
        Instant executed,
        Currency base,
        Currency quote,
        TransactionType action,
        BigDecimal volume,
        String address
    ) {
        this(uid, executed, base, quote, action, volume, null, address);
    }

    public DepositWithdrawalImportedTransaction(
        String uid,
        Instant executed,
        Currency base,
        Currency quote,
        TransactionType action,
        BigDecimal volume,
        String note,
        String address
    ) {
        super(uid, executed, base, quote, action, note);
        this.volume = volume;
        this.address = address;
    }
}
