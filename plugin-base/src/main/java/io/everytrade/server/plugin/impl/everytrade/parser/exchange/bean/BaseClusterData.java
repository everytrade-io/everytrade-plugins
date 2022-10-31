package io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean;

import io.everytrade.server.model.Currency;
import io.everytrade.server.model.TransactionType;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;

@Builder
public class BaseClusterData {
    String uid;
    Instant executed;
    Currency base;
    Currency quote;
    TransactionType transactionType;
    BigDecimal volume; // sometimes called baseAmount;
    BigDecimal unitPrice; // usually count by base and quote amount
    BigDecimal feeAmount;
    String fee;
    Currency feeCurrency;
    BigDecimal rebateAmount;
    String rebate;
    Currency rebateCurrency;
    BigDecimal quoteAmount;
    String address;
    String label;
    String note;
}
