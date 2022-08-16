package io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean;

import com.univocity.parsers.annotations.Format;
import com.univocity.parsers.annotations.Headers;
import com.univocity.parsers.annotations.Parsed;
import com.univocity.parsers.common.DataValidationException;
import io.everytrade.server.model.Currency;
import io.everytrade.server.model.TransactionType;
import io.everytrade.server.plugin.api.parser.ImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.TransactionCluster;
import io.everytrade.server.plugin.impl.everytrade.parser.exception.DataIgnoredException;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.ExchangeBean;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.Date;

@Headers(
    sequence = {"Order ID", "Created At", "From currency", "From Amount"
        , "To currency", "To Amount", "Status", "Status direction", "Final status"},
    extract = true
)
public class SimpleCoinBeanV1 extends ExchangeBean {
    private String orderId;
    private Instant createdAt;
    private Currency fromCurrency;
    private BigDecimal fromAmount;
    private Currency toCurrency;
    private BigDecimal toAmount;
    private String status;
    private String statusDirection;
    private String finalStatus;

    Currency base;
    Currency quote;
    BigDecimal baseAmount;
    BigDecimal quoteAmount;
    TransactionType type;

    @Parsed(field = "Order ID")
    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    @Parsed(field = "Created At")
    @Format(formats = {"MM/dd/yyyy HH:mm aa", "yyyy-MM-dd'T'HH:mm:ssXXX"}, options = {"locale=EN", "timezone=UTC"})
    public void setCreatedAt(Date value) {
        createdAt = value.toInstant();
    }

    @Parsed(field = "From currency")
    public void setFromCurrency(String value) {
        fromCurrency = Currency.fromCode(value);
    }

    @Parsed(field = "From Amount")
    public void setFromAmount(String value) {
        fromAmount = new BigDecimal(value);
    }

    @Parsed(field = "To currency")
    public void setToCurrency(String value) {
        toCurrency = Currency.fromCode(value);
    }

    @Parsed(field = "To Amount")
    public void setToAmount(String value) {
        toAmount = new BigDecimal(value);
    }

    @Parsed(field = "Status")
    public void setStatus(String value) {
        this.status = value;
    }

    @Parsed(field = "Status direction")
    public void setStatusDirection(String value) {
        this.statusDirection = value;
    }

    @Parsed(field = "Final status")
    public void setFinalStatus(String value) {
        this.finalStatus = value;
    }

    @Override
    public TransactionCluster toTransactionCluster() {
        if (!finalStatus.equals("Delivered")) {
            throw new DataIgnoredException(String.format("Invalid final status %s", finalStatus));
        }
        mapTransaction();
        findTransactionType();
        validateCurrencyPair(base, quote);

        return new TransactionCluster(
            new ImportedTransactionBean(
                null,
                createdAt,
                base,
                quote,
                type,
                baseAmount.abs(),
                evalUnitPrice(quoteAmount.abs(), baseAmount.abs())
            ),
            Collections.emptyList()
        );
    }

    private void mapTransaction() {
        if (!fromCurrency.isFiat()) {
            base = fromCurrency;
            quote = toCurrency;
            baseAmount = fromAmount;
            quoteAmount = toAmount;
        } else if (!toCurrency.isFiat()) {
            base = toCurrency;
            baseAmount = toAmount;
            quote = fromCurrency;
            quoteAmount = fromAmount;
        } else {
            throw new DataValidationException(String.format("Unable to select base and quote currency"));
        }
    }

    private void findTransactionType() {
        if (fromCurrency.isFiat() && !toCurrency.isFiat()) {
            type = TransactionType.BUY;
        } else if (!fromCurrency.isFiat() && toCurrency.isFiat()) {
            type = TransactionType.SELL;
        } else {
            throw new DataValidationException("Unable to find transaction type");
        }
    }
}
