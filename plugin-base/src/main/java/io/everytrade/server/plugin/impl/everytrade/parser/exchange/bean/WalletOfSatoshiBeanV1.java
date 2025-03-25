package io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean;

import com.univocity.parsers.annotations.Parsed;
import io.everytrade.server.model.Currency;
import io.everytrade.server.model.TransactionType;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.kuCoin.BaseTransactionMapper;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.Instant;

import static lombok.AccessLevel.PRIVATE;

@FieldDefaults(level = PRIVATE)
public class WalletOfSatoshiBeanV1 extends BaseTransactionMapper {

    Instant utcDate;
    String type;
    Currency currency;
    BigDecimal amount;
    BigDecimal fees;
    String address;
    String description;
    String pointOfSale;

    @Parsed(field = "utcDate")
    public void setUtcDate(String utcDate) {
        this.utcDate = Instant.parse(utcDate);
    }

    @Parsed(field = "type")
    public void setType(String type) {
        this.type = type;
    }

    @Parsed(field = "currency")
    public void setCurrency(String currency) {
        if (currency.equalsIgnoreCase("LIGHTNING")) {
            this.currency = Currency.BTC;
        } else {
            this.currency = Currency.fromCode(currency);
        }
    }

    @Parsed(field = "amount")
    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    @Parsed(field = "fees")
    public void setFees(BigDecimal fees) {
        this.fees = fees;
    }

    @Parsed(field = "address")
    public void setAddress(String address) {
        this.address = address;
    }

    @Parsed(field = "description")
    public void setDescription(String description) {
        this.description = description;
    }

    @Parsed(field = "pointOfSale")
    public void setPointOfSale(String pointOfSale) {
        this.pointOfSale = pointOfSale;
    }

    @Override
    protected TransactionType findTransactionType() {
        return switch (type.toUpperCase()) {
            case "DEBIT" -> TransactionType.DEPOSIT;
            case "CREDIT" -> TransactionType.WITHDRAWAL;
            default -> TransactionType.UNKNOWN;
        };
    }

    @Override
    protected BaseClusterData mapData() {
        return BaseClusterData.builder()
            .transactionType(findTransactionType())
            .executed(utcDate)
            .base(currency)
            .quote(currency)
            .volume(amount)
            .fee(currency.code())
            .feeAmount(fees)
            .address(address)
            .note(description)
            .build();
    }
}
