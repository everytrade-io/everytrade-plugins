package io.everytrade.server.plugin.api.parser;

import io.everytrade.server.model.Currency;
import io.everytrade.server.model.TransactionType;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.Instant;

import static lombok.AccessLevel.PRIVATE;

@Getter
@ToString
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
    /**
     * Quote volume exactly as supplied by the source (CSV VOLUME_QUOTE, user input); null when the source gave
     * only a unit price. Kept separately because the derived unit price is rounded to a fixed scale, so
     * re-multiplying it can never reproduce the original total (ETS-5080).
     */
    BigDecimal quoteVolume;

    // hand-written replacement of the former @AllArgsConstructor — the signature must stay stable for existing callers
    public ImportedTransactionBean(String uid, Instant executed, Currency base, Currency quote, TransactionType action,
                                   BigDecimal volume, BigDecimal unitPrice, String note, String address, String labels,
                                   String partner, String reference) {
        this(uid, executed, base, quote, action, volume, unitPrice, note, address, labels, partner, reference, null);
    }

    public ImportedTransactionBean(String uid, Instant executed, Currency base, Currency quote, TransactionType action,
                                   BigDecimal volume, BigDecimal unitPrice, String note, String address, String labels,
                                   String partner, String reference, BigDecimal quoteVolume) {
        this.uid = uid;
        this.executed = executed;
        this.base = base;
        this.quote = quote;
        this.action = action;
        this.volume = volume;
        this.unitPrice = unitPrice;
        this.note = note;
        this.address = address;
        this.labels = labels;
        this.partner = partner;
        this.reference = reference;
        this.quoteVolume = quoteVolume;
    }

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
