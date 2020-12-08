package io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean;

import com.univocity.parsers.annotations.Headers;
import com.univocity.parsers.annotations.Parsed;
import com.univocity.parsers.annotations.Replace;
import com.univocity.parsers.common.DataValidationException;
import io.everytrade.server.model.Currency;
import io.everytrade.server.model.TransactionType;
import io.everytrade.server.plugin.api.parser.ImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.TransactionCluster;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.ExchangeBean;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME;

@Headers(sequence = {
    "Transaction Type", "Date", "Amount debited", "Debit Currency", "Amount Credited", "Credit Currency"
}, extract = true)
public class ShakePayBeanV1 extends ExchangeBean {
    private Instant date;
    private BigDecimal amountDebited;
    private Currency debitCurrency;
    private BigDecimal amountCredited;
    private Currency creditCurrency;

    @Parsed(field = "Transaction Type")
    public void checkTransactionType(String transactionType) {
        if (!"exchange".equals(transactionType)) {
            throw new DataValidationException(String.format("Unsupported transaction type %s.", transactionType));
        }
    }

    @Parsed(field = "Date")
    public void setDate(String dateTime) {
        if (dateTime.length() < 19) {
            throw new DataValidationException(
                String.format("Unknown dateTime format(%s), illegal length %d.", dateTime, dateTime.length())
            );
        }
        final String withoutOffset = dateTime.substring(0, 19);
        final LocalDateTime localDateTime = LocalDateTime.parse(withoutOffset, ISO_LOCAL_DATE_TIME);
        this.date = localDateTime.toInstant(ZoneOffset.UTC);
    }

    @Parsed(field = "Amount Debited")
    @Replace(expression = IGNORED_CHARS_IN_NUMBER, replacement = "")
    public void setAmountDebited(BigDecimal amountDebited) {
        this.amountDebited = amountDebited;
    }

    @Parsed(field = "Debit Currency")
    public void setDebitCurrency(String debitCurrency) {
        this.debitCurrency = Currency.valueOf(debitCurrency);
    }

    @Parsed(field = "Amount Credited")
    @Replace(expression = IGNORED_CHARS_IN_NUMBER, replacement = "")
    public void setAmountCredited(BigDecimal amountCredited) {
        this.amountCredited = amountCredited;
    }

    @Parsed(field = "Credit Currency")
    public void setCreditCurrency(String creditCurrency) {
        this.creditCurrency = Currency.valueOf(creditCurrency);
    }


    @Override
    public TransactionCluster toTransactionCluster() {
        //TODO: mcharvat - implement
        return null;
//        final TransactionType transactionType = detectTransactionType(debitCurrency, creditCurrency);
//        final boolean isBuy = transactionType.equals(TransactionType.BUY);
//        final BigDecimal baseQuantity = isBuy ? amountCredited.abs() : amountDebited.abs();
//        final BigDecimal transactionPrice = isBuy ? amountDebited.abs() : amountCredited.abs();
//        return new ImportedTransactionBean(
//            null,                                //uuid
//            date,                                     //executed
//            isBuy ? creditCurrency : debitCurrency,   //base
//            isBuy ? debitCurrency : creditCurrency,   //quote
//            transactionType,                          //action
//            baseQuantity,                             //baseQuantity
//            evalUnitPrice(transactionPrice, baseQuantity), //unitPrice
//            BigDecimal.ZERO                           //fee
//        );
    }
}
