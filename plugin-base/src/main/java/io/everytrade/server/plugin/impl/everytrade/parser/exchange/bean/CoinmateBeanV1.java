package io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean;

import com.univocity.parsers.annotations.Format;
import com.univocity.parsers.annotations.Headers;
import com.univocity.parsers.annotations.Parsed;
import com.univocity.parsers.common.DataValidationException;
import io.everytrade.server.model.Currency;
import io.everytrade.server.model.TransactionType;
import io.everytrade.server.plugin.api.parser.ImportedTransactionBean;
import io.everytrade.server.plugin.impl.everytrade.parser.exception.DataIgnoredException;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.ExchangeBean;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Date;

@Headers(sequence = {"ID", "Date", "Type", "Amount", "Amount Currency", "Price", "Price Currency", "Fee",
    "Fee Currency", "Status"}, extract = true)
public class CoinmateBeanV1 extends ExchangeBean {
    // auxiliary field for validation
    private Currency auxFeeCurrency;
    private String id;
    private Instant date;
    private TransactionType type;
    private BigDecimal amount;
    private Currency amountCurrency;
    private BigDecimal price;
    private Currency priceCurrency;
    private BigDecimal fee;

    @Parsed(field = "ID")
    public void setId(String id) {
        this.id = id;
    }

    @Parsed(field = "Date")
    @Format(formats = {"yyyy-MM-dd HH:mm:ss", "dd.MM.yyyy HH:mm"}, options = {"locale=US", "timezone=UTC"})
    public void setDate(Date date) {
        this.date = date.toInstant();
    }

    @Parsed(field = "Type")
    public void setType(String type) {
        if ("BUY".equals(type) || "QUICK_BUY".equals(type)) {
            this.type = TransactionType.BUY;
        } else if ("SELL".equals(type) || "QUICK_SELL".equals(type)) {
            this.type = TransactionType.SELL;
        } else {
            throw new DataIgnoredException(UNSUPPORTED_TRANSACTION_TYPE.concat(type));
        }
    }

    @Parsed(field = "Amount", defaultNullRead = "0")
    public void setAmount(BigDecimal amount) {
        this.amount = amount.abs();
    }

    @Parsed(field = "Amount Currency")
    public void setAmountCurrency(String curr) {
        amountCurrency = Currency.valueOf(curr);
    }

    @Parsed(field = "Price", defaultNullRead = "0")
    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    @Parsed(field = "Price Currency")
    public void setPriceCurrency(String curr) {
        priceCurrency = Currency.valueOf(curr);
    }

    @Parsed(field = "Fee", defaultNullRead = "0")
    public void setFee(BigDecimal fee) {
        this.fee = fee;
    }

    @Parsed(field = "Fee Currency")
    public void setFeeCurrency(String curr) {
        auxFeeCurrency = Currency.valueOf(curr);
    }

    @Parsed(field = "Status")
    public void checkStatus(String status) {
        if (!"OK".equals(status)) {
            throw new DataIgnoredException(UNSUPPORTED_STATUS_TYPE.concat(status));
        }
    }


    @Override
    public ImportedTransactionBean toTransactionCluster() {
        validateCurrencyPair(amountCurrency, priceCurrency);
        if (!priceCurrency.equals(auxFeeCurrency)) {
            throw new DataValidationException(String.format("Price currecy(%s) and fee currency(%s) are different.",
                priceCurrency.name(), auxFeeCurrency.name()));
        }
        return new ImportedTransactionBean(
            id,             //uuid
            date,           //executed
            amountCurrency, //base
            priceCurrency,  //quote
            type,           //action
            amount,         //base quantity
            price,          //unit price
            fee             //fee quote
        );
    }
}
