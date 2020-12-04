package io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean;

import com.univocity.parsers.annotations.Format;
import com.univocity.parsers.annotations.Headers;
import com.univocity.parsers.annotations.Parsed;
import com.univocity.parsers.annotations.Replace;
import io.everytrade.server.model.Currency;
import io.everytrade.server.model.TransactionType;
import io.everytrade.server.plugin.api.parser.ImportedTransactionBean;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.ExchangeBean;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Date;

@Headers(sequence = {"date","from_currency","from_amount","to_currency","to_amount"}, extract = true)
public class CoinsquareBeanV2 extends ExchangeBean {
    private Instant date;
    private Currency fromCurrenncy;
    private BigDecimal fromAmount;
    private Currency toCurrenncy;
    private BigDecimal toAmount;

    @Parsed(field = "date")
    @Format(formats = {"dd-MM-yy"}, options = {"locale=EN", "timezone=UTC"})
    public void setDate(Date value) {
        date = value.toInstant();
    }

    @Parsed(field = "from_currency")
    public void setFromCurrency(String value) {
        fromCurrenncy = Currency.valueOf(value);
    }

    @Parsed(field = "from_amount")
    @Replace(expression = IGNORED_CHARS_IN_NUMBER, replacement = "")
    public void setFromAmount(BigDecimal value)  {
        fromAmount = value;
    }

    @Parsed(field = "to_currency")
    public void setToCurrency(String value) {
        toCurrenncy = Currency.valueOf(value);
    }

    @Parsed(field = "to_amount")
    @Replace(expression = IGNORED_CHARS_IN_NUMBER, replacement = "")
    public void setToAmount(BigDecimal value)  {
        toAmount = value;
    }

    @Override
    public ImportedTransactionBean toImportedTransactionBean() {
        final TransactionType transactionType = detectTransactionType(fromCurrenncy, toCurrenncy);
        final boolean isBuy = transactionType.equals(TransactionType.BUY);

        final BigDecimal baseQuantity = isBuy ? toAmount.abs() : fromAmount.abs();
        final BigDecimal transactionPrice = isBuy ? fromAmount.abs() : toAmount.abs();
        return new ImportedTransactionBean(
            null,                               //uuid
            date,                                    //executed
            isBuy ? toCurrenncy : fromCurrenncy,     //base
            isBuy ? fromCurrenncy : toCurrenncy,     //quote
            transactionType,                         //action
            baseQuantity,                            //base quantity
            evalUnitPrice(transactionPrice, baseQuantity),   //unit price
            BigDecimal.ZERO                          //fee quote
        );
    }
}
