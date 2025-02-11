package io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean;

import com.univocity.parsers.annotations.Parsed;
import com.univocity.parsers.common.DataValidationException;
import io.everytrade.server.model.Currency;
import io.everytrade.server.model.TransactionType;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.kuCoin.BaseTransactionMapper;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.Instant;

import static lombok.AccessLevel.PRIVATE;

@FieldDefaults(level = PRIVATE)
public class PocketAppBeanV1 extends BaseTransactionMapper {

    String type;
    Instant date;
    String reference;
    Currency priceCurrency;
    BigDecimal priceAmount;
    Currency costCurrency;
    BigDecimal costAmount;
    String feeCurrency;
    BigDecimal feeAmount;
    Currency valueCurrency;
    BigDecimal valueAmount;

    BigDecimal baseAmount;
    Currency baseCurrency;
    Currency quoteCurrency;
    BigDecimal unitPrice;

    @Parsed(field = "type")
    public void setType(String type) {
        this.type = type;
    }

    @Parsed(field = "date")
    public void setDate(String date) {
        this.date = Instant.parse(date);
    }

    @Parsed(field = "reference")
    public void setReference(String reference) {
        this.reference = reference;
    }

    @Parsed(field = "price.currency")
    public void setPriceCurrency(String priceCurrency) {
        if (priceCurrency != null) {
            this.priceCurrency = Currency.valueOf(priceCurrency);
        }
    }

    @Parsed(field = "price.amount")
    public void setPriceAmount(BigDecimal priceAmount) {
        this.priceAmount = priceAmount;
    }

    @Parsed(field = "cost.currency")
    public void setCostCurrency(Currency costCurrency) {
        this.costCurrency = costCurrency;
    }

    @Parsed(field = "cost.amount")
    public void setCostAmount(BigDecimal costAmount) {
        this.costAmount = costAmount;
    }

    @Parsed(field = "fee.currency")
    public void setFeeCurrency(String feeCurrency) {
        this.feeCurrency = feeCurrency;
    }

    @Parsed(field = "fee.amount")
    public void setFeeAmount(BigDecimal feeAmount) {
        this.feeAmount = feeAmount;
    }

    @Parsed(field = "value.currency")
    public void setValueCurrency(Currency valueCurrency) {
        this.valueCurrency = valueCurrency;
    }

    @Parsed(field = "value.amount")
    public void setValueAmount(BigDecimal valueAmount) {
        this.valueAmount = valueAmount;
    }

    @Override
    protected TransactionType findTransactionType() {
        return switch (type) {
            case "exchange" -> {
                if (valueCurrency.isFiat()) {
                    baseAmount = costAmount;
                    baseCurrency = costCurrency;
                    quoteCurrency = valueCurrency;
                    unitPrice = evalUnitPrice(costAmount, valueAmount);
                    yield TransactionType.SELL;
                } else {
                    baseAmount = valueAmount;
                    baseCurrency = valueCurrency;
                    quoteCurrency = costCurrency;
                    unitPrice = evalUnitPrice(valueAmount, costAmount);
                    yield TransactionType.BUY;
                }
            }
            case "deposit" -> {
                baseAmount = valueAmount;
                baseCurrency = valueCurrency;
                quoteCurrency = valueCurrency;
                yield TransactionType.DEPOSIT;
            }
            case "withdrawal" -> {
                baseAmount = valueAmount;
                baseCurrency = valueCurrency;
                quoteCurrency = valueCurrency;
                yield TransactionType.WITHDRAWAL;
            }
            default -> throw new DataValidationException("Invalid transaction type: " + type + ".");
        };
    }

    @Override
    protected BaseClusterData mapData() {
        return BaseClusterData.builder()
            .transactionType(findTransactionType())
            .executed(date)
            .base(baseCurrency)
            .quote(quoteCurrency)
            .unitPrice(unitPrice)
            .volume(baseAmount)
            .fee(feeCurrency)
            .feeAmount(feeAmount)
            .build();
    }
}
