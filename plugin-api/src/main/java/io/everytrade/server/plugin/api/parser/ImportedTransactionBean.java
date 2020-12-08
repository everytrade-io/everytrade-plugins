package io.everytrade.server.plugin.api.parser;

import io.everytrade.server.model.Currency;
import io.everytrade.server.model.TransactionType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Objects;

public abstract class ImportedTransactionBean {
    private final Logger log = LoggerFactory.getLogger(this.getClass());

    private final String uid;
    private final Instant executed;
    private final Currency base;
    private final Currency quote;
    private final TransactionType action;
    private final Instant imported = Instant.now();

    public ImportedTransactionBean(
        String uid,
        Instant executed,
        Currency base,
        Currency quote,
        TransactionType action
    ) {
        this.uid = uid; //TODO: fix NULL uids with synthetic ones, otherwise API import might fail
        Objects.requireNonNull(this.executed = executed);
        Objects.requireNonNull(this.base = base);
        Objects.requireNonNull(this.quote = quote);
        Objects.requireNonNull(this.action = action);
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
            '}';
    }
}
