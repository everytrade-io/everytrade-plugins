package io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean;

import com.univocity.parsers.annotations.Parsed;
import com.univocity.parsers.common.DataValidationException;
import io.everytrade.server.model.Currency;
import io.everytrade.server.model.TransactionType;
import io.everytrade.server.plugin.impl.everytrade.parser.exception.DataIgnoredException;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.kuCoin.BaseTransactionMapper;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.Instant;

import static lombok.AccessLevel.PRIVATE;


@FieldDefaults(level = PRIVATE)
public class KvaPayBeanV1 extends BaseTransactionMapper {

    String id;
    Instant dateCreated;
    String type;
    BigDecimal amount;
    Currency symbol;
    BigDecimal destinationAmount;
    Currency destinationSymbol;
    BigDecimal exchangeRate;
    BigDecimal fee;
    String feeSymbol;
    String address;
    String network;
    String state;

    BigDecimal baseAmount;
    Currency baseCurrency;
    BigDecimal quoteAmount;
    Currency quoteCurrency;


    enum states {
        SUCCESS,
    }

    @Parsed(field = "ID")
    public void setId(String id) {
        this.id = id;
    }

    @Parsed(field = "Date Created")
    public void setDateCreated(String dateCreated) {
        this.dateCreated = Instant.parse(dateCreated);
    }

    @Parsed(field = "Type")
    public void setType(String type) {
        this.type = type;
    }

    @Parsed(field = "Amount")
    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    @Parsed(field = "Symbol")
    public void setSymbol(String symbol) {
        try {
            this.symbol = Currency.fromCode(symbol);
        } catch (Exception e) {
            throw new DataValidationException(UNSUPPORTED_CURRENCY_PAIR + symbol);
        }
    }

    @Parsed(field = "Destination Amount")
    public void setDestinationAmount(String destinationAmount) {
        this.destinationAmount = "-".equals(destinationAmount) ? null : new BigDecimal(destinationAmount);
    }

    @Parsed(field = "Destination Symbol")
    public void setDestinationSymbol(String destinationSymbol) {
        try {
            this.destinationSymbol = Currency.fromCode(destinationSymbol);
        } catch (Exception e) {
            if (type.equals("DEPOSIT") || type.equals("WITHDRAW")) {
                return;
            }
            throw new DataValidationException(UNSUPPORTED_CURRENCY_PAIR + destinationSymbol);
        }
    }

    @Parsed(field = "Exchange Rate")
    public void setExchangeRate(String exchangeRate) {
        this.exchangeRate = "-".equals(exchangeRate) ? null : new BigDecimal(exchangeRate);
    }

    @Parsed(field = "Fee")
    public void setFee(BigDecimal fee) {
        this.fee = fee;
    }

    @Parsed(field = "Fee Symbol")
    public void setFeeSymbol(String feeSymbol) {
        this.feeSymbol = feeSymbol;
    }

    @Parsed(field = "Address")
    public void setAddress(String address) {
        this.address = address;
    }

    @Parsed(field = "Network")
    public void setNetwork(String network) {
        this.network = network;
    }

    @Parsed(field = "State")
    public void setState(String state) {
        try {
            this.state = states.valueOf(state).name();
        } catch (IllegalArgumentException e) {
            throw new DataIgnoredException("Invalid state: " + state + ". Allowed: SUCCESS");
        }
    }

    @Override
    protected TransactionType findTransactionType() {
        return switch (type) {
            case "EXCHANGE" -> {
                if (symbol.isFiat()) {
                    baseCurrency = destinationSymbol;
                    baseAmount = destinationAmount;
                    quoteAmount = amount;
                    quoteCurrency = symbol;
                    exchangeRate = evalUnitPrice(destinationAmount, amount);
                    yield TransactionType.SELL;
                } else {
                    baseCurrency = symbol;
                    baseAmount = amount;
                    quoteCurrency = destinationSymbol;
                    quoteAmount = destinationAmount;
                    exchangeRate = evalUnitPrice(amount, destinationAmount);
                    yield TransactionType.BUY;
                }
            }
            case "DEPOSIT" -> {
                baseCurrency = symbol;
                baseAmount = amount;
                quoteCurrency = symbol;
                yield TransactionType.DEPOSIT;
            }
            case "WITHDRAW" -> {
                baseCurrency = symbol;
                baseAmount = amount;
                quoteCurrency = symbol;
                yield TransactionType.WITHDRAWAL;
            }
            default -> throw new DataIgnoredException("Invalid transaction type: " + type + ".");
        };
    }

    @Override
    protected BaseClusterData mapData() {
        return BaseClusterData.builder()
            .uid(id)
            .transactionType(findTransactionType())
            .executed(dateCreated)
            .base(baseCurrency)
            .quote(quoteCurrency)
            .unitPrice(exchangeRate)
            .volume(amount)
            .fee(feeSymbol)
            .feeAmount(fee)
            .address(address)
            .build();
    }
}
