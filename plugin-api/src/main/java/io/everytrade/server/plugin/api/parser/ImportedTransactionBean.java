package io.everytrade.server.plugin.api.parser;

import io.everytrade.server.model.Currency;
import io.everytrade.server.model.TransactionType;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.Instant;

import static lombok.AccessLevel.PRIVATE;

@Getter
@ToString
@AllArgsConstructor
@FieldDefaults(level = PRIVATE, makeFinal = true)
public class ImportedTransactionBean {

    String uid;
    Instant executed;
    Currency base;
    Currency quote;
    TransactionType action;
    BigDecimal volume;
    BigDecimal unitPrice;
    Instant imported = Instant.now();
    String note;
    String address;
    String labels;
    String partner;
    String reference;

    public ImportedTransactionBean(String id, Instant executed, Currency base, Currency quote, TransactionType action, BigDecimal volume,
                                   BigDecimal unitPrice) {
        this(id, executed, base, quote, action, volume, unitPrice, null, null, null, null, null);
    }

    public ImportedTransactionBean(String id, Instant executed, Currency base, Currency quote, TransactionType action, BigDecimal volume,
                                   BigDecimal unitPrice, String note, String address) {
        this(id, executed, base, quote, action, volume, unitPrice, note, address, null, null, null);
    }

    public static ImportedTransactionBean createDepositWithdrawal(String id, Instant timestamp, Currency base, Currency quote,
                                                                  TransactionType type, BigDecimal amount, String address) {
        return new ImportedTransactionBean(id, timestamp, base, quote, type, amount, null, null, address, null, null, null);
    }

    public static ImportedTransactionBean createDepositWithdrawal(String id, Instant timestamp, Currency base, Currency quote,
                                                                  TransactionType type, BigDecimal amount, String address,
                                                                  String note, String labels) {
        return new ImportedTransactionBean(id, timestamp, base, quote, type, amount, null, note, address, labels, null, null);
    }

    public static ImportedTransactionBean createDepositWithdrawal(String id, Instant timestamp, Currency base, Currency quote,
                                                                  TransactionType type, BigDecimal amount, String address,
                                                                  String note, String labels, String partner, String reference) {
        return new ImportedTransactionBean(id, timestamp, base, quote, type, amount, null, note, address, labels, partner, reference);
    }

    public ImportedTransactionBean(String uid, Instant executed, Currency base, Currency quote, TransactionType action,
                                   BigDecimal volume, BigDecimal unitPrice, String note, String address, String labels) {
        this(uid, executed, base, quote, action, volume, unitPrice, note, address, labels, null, null);
    }
}
