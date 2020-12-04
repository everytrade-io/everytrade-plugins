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

@Headers(sequence = {
    "?Transaction id", "Date", "Type detail", "Currency amount", "Amount", "Currency price", "Price", "Currency fee",
    "Fee", "Status"}, extract = true)
public class CoinmateBeanV2 extends ExchangeBean {
    // auxiliary field for validation
    private Currency auxCurrencyFee;
    private String transactionId;
    private Instant date;
    private TransactionType typeDetail;
    private BigDecimal amount;
    private Currency currencyAmount;
    private BigDecimal price;
    private Currency currencyPrice;
    private BigDecimal fee;

    @Parsed(field = "?Transaction id")
    public void setTransactionId(String transactionId) {
        this.transactionId = transactionId;
    }

    @Parsed(field = "Date")
    @Format(formats = {"yyyy-MM-dd HH:mm:ss", "dd.MM.yyyy HH:mm"}, options = {"locale=US", "timezone=UTC"})
    public void setDate(Date date) {
        this.date = date.toInstant();
    }

    @Parsed(field = "Type detail")
    public void setTypeDetail(String typeDetail) {
        if ("BUY".equals(typeDetail) || "QUICK_BUY".equals(typeDetail)) {
            this.typeDetail = TransactionType.BUY;
        } else if ("SELL".equals(typeDetail) || "QUICK_SELL".equals(typeDetail)) {
            this.typeDetail = TransactionType.SELL;
        } else {
            throw new DataIgnoredException(UNSUPPORTED_TRANSACTION_TYPE.concat(typeDetail));
        }
    }

    @Parsed(field = "Amount", defaultNullRead = "0")
    public void setAmount(BigDecimal amount) {
        this.amount = amount.abs();
    }

    @Parsed(field = "Currency Amount")
    public void setCurrencyAmount(String curr) {
        currencyAmount = Currency.valueOf(curr);
    }

    @Parsed(field = "Price", defaultNullRead = "0")
    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    @Parsed(field = "Currency Price")
    public void setCurrencyPrice(String curr) {
        currencyPrice = Currency.valueOf(curr);
    }

    @Parsed(field = "Fee", defaultNullRead = "0")
    public void setFee(BigDecimal fee) {
        this.fee = fee;
    }

    @Parsed(field = "Currency Fee")
    public void setFeeCurrency(String curr) {
        auxCurrencyFee = Currency.valueOf(curr);
    }

    @Parsed(field = "Status")
    public void checkStatus(String status) {
        if (!"OK".equals(status)) {
            throw new DataIgnoredException(UNSUPPORTED_STATUS_TYPE.concat(status));
        }
    }

    @Override
    public ImportedTransactionBean toImportedTransactionBean() {
        validateCurrencyPair(currencyAmount, currencyPrice);
        if (!currencyPrice.equals(auxCurrencyFee)) {
            throw new DataValidationException(String.format("Price currecy(%s) and fee currency(%s) are different.",
                currencyPrice.name(), auxCurrencyFee.name()));
        }
        return new ImportedTransactionBean(
            transactionId,      //uuid
            date,               //executed
            currencyAmount,     //base
            currencyPrice,      //quote
            typeDetail,         //action
            amount,             //base quantity
            price,              //unit price
            fee                 //fee quote
        );
    }
}
