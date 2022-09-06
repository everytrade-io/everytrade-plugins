package io.everytrade.server.parser.exchange;

import io.everytrade.server.plugin.api.parser.TransactionCluster;

public interface IXChangeApiTransaction {

    TransactionCluster toTransactionCluster();

    String getId();

    java.time.Instant getTimestamp();

    io.everytrade.server.model.TransactionType getType();

    io.everytrade.server.model.Currency getBase();

    io.everytrade.server.model.Currency getQuote();

    io.everytrade.server.model.Currency getFeeCurrency();

    java.math.BigDecimal getOriginalAmount();

    java.math.BigDecimal getPrice();

    java.math.BigDecimal getFeeAmount();

    String getAddress();

}
