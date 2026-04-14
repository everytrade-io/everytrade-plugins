package io.everytrade.server.plugin.impl.everytrade.parser.exchange.okx;

import com.univocity.parsers.annotations.Format;
import com.univocity.parsers.annotations.Parsed;
import io.everytrade.server.model.Currency;
import io.everytrade.server.model.TransactionType;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean.BaseClusterData;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.kuCoin.BaseTransactionMapper;
import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Date;

import static io.everytrade.server.model.TransactionType.DEPOSIT;
import static io.everytrade.server.model.TransactionType.WITHDRAWAL;
import static lombok.AccessLevel.PRIVATE;

@EqualsAndHashCode(callSuper = true)
@FieldDefaults(level = PRIVATE)
@Data
public class OkxBeanWdrlDepV2 extends BaseTransactionMapper {
    String id;
    Instant time;
    String type;
    BigDecimal amount;
    BigDecimal beforeBalance;
    BigDecimal afterBalance;
    Currency symbol;

    TransactionType transactionType;

    @Parsed(field = "id")
    public void setId(String id) {
        this.id = id;
    }

    @Parsed(field = "Time")
    @Format(formats = {"yyyy-MM-dd HH:mm:ss"}, options = {"locale=EN", "timezone=UTC"})
    public void setTime(Date time) {
        this.time = time.toInstant();
    }

    @Parsed(field = "Type")
    public void setType(String type) {
        this.type = type;
    }

    @Parsed(field = "Amount")
    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    @Parsed(field = "Before Balance")
    public void setBeforeBalance(BigDecimal beforeBalance) {
        this.beforeBalance = beforeBalance;
    }

    @Parsed(field = "After Balance")
    public void setAfterBalance(BigDecimal afterBalance) {
        this.afterBalance = afterBalance;
    }

    @Parsed(field = "Symbol")
    public void setSymbol(String symbol) {
        this.symbol = Currency.fromCode(symbol);
    }

    @Override
    protected TransactionType findTransactionType() {
        return switch (type.toLowerCase()) {
            case "deposit" -> DEPOSIT;
            case "withdrawal" -> WITHDRAWAL;
            default -> throw new IllegalArgumentException("Unknown transaction type: " + type);
        };
    }

    @Override
    protected BaseClusterData mapData() {
        return BaseClusterData.builder()
            .transactionType(findTransactionType())
            .uid(id)
            .executed(time)
            .base(symbol)
            .quote(symbol)
            .volume(amount.abs())
            .build();
    }
}
