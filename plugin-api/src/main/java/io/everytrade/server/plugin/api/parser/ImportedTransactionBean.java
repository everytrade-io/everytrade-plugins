package io.everytrade.server.plugin.api.parser;

import io.everytrade.server.model.Currency;
import io.everytrade.server.model.TransactionType;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import lombok.experimental.FieldDefaults;

import java.time.Instant;

import static lombok.AccessLevel.PRIVATE;

@Getter
@ToString
@RequiredArgsConstructor
@FieldDefaults(level = PRIVATE, makeFinal = true)
public abstract class ImportedTransactionBean {
    String uid;
    Instant executed;
    Currency base;
    Currency quote;
    TransactionType action;
    Instant imported = Instant.now();
    String note;
}
