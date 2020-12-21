package io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean;

import com.univocity.parsers.annotations.Format;
import com.univocity.parsers.annotations.Headers;
import com.univocity.parsers.annotations.Parsed;
import com.univocity.parsers.common.DataValidationException;
import io.everytrade.server.model.Currency;
import io.everytrade.server.model.TransactionType;
import io.everytrade.server.plugin.api.parser.BuySellImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.FeeRebateImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.ImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.TransactionCluster;
import io.everytrade.server.plugin.impl.everytrade.parser.ParserUtils;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.ExchangeBean;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.Date;
import java.util.List;

@Headers(sequence = {"DateTime", "Amount", "Value", "Rate", "Fee", "Sub Type"}, extract = true)
public class BitstampBeanV1 extends ExchangeBean {
    public static final String CURRENCY_EQUALITY_MESSAGE = "Value currency, rate currency and fee currency not equals.";
    private Instant dateTime;
    private Currency amountCurrency;
    private BigDecimal amountValue;
    private Currency valueCurrency;
    private BigDecimal value;
    private BigDecimal fee;
    private TransactionType subType;
    private Currency rateCurrency;
    private Currency feeCurrency;

    @Parsed(field = "Datetime")
    @Format(formats = {"MMM. dd, yyyy, hh:mm a"}, options = {"locale=US", "timezone=UTC"})
    public void setDate(Date date) {
        dateTime = date.toInstant();
    }

    @Parsed(field = "Amount")
    public void setAmount(String amount) {
        String[] amountParts = amount.split(" ");
        String mBase = amountParts[1];
        amountCurrency = Currency.valueOf(mBase);
        if (amountParts[0].isEmpty()) {
            throw new DataValidationException("BaseQuantity can not be null or empty.");
        }
        BigDecimal quantity = new BigDecimal(amountParts[0]);
        if (quantity.compareTo(BigDecimal.ZERO) == 0) {
            throw new DataValidationException("BaseQuantity can not be zero.");
        }
        amountValue = quantity;
    }

    @Parsed(field = "Value")
    public void setValue(String value) {
        String[] valueParts = value.split(" ");
        valueCurrency = Currency.valueOf(valueParts[1]);
        if (valueParts[0].isEmpty()) {
            this.value = BigDecimal.ZERO;
        } else {
            this.value = new BigDecimal(valueParts[0]);
        }
    }

    @Parsed(field = "Rate")
    public void setRate(String rate) {
        rateCurrency = Currency.valueOf(rate.split(" ")[1]);
    }

    @Parsed(field = "Fee")
    public void setFee(String fee) {
        String[] feeParts = fee.split(" ");
        if (feeParts[0].isEmpty()) {
            this.fee = BigDecimal.ZERO;
        } else {
            this.fee = new BigDecimal(feeParts[0]);
        }
        feeCurrency = Currency.valueOf(feeParts[1]);
    }

    @Parsed(field = "Sub Type")
    public void setSubType(String field) {
        subType = detectTransactionType(field);
    }

    @Override
    public TransactionCluster toTransactionCluster() {
        validateCurrencyPair(amountCurrency, valueCurrency);
        if (!valueCurrency.equals(rateCurrency) || !valueCurrency.equals(feeCurrency)) {
            throw new DataValidationException(CURRENCY_EQUALITY_MESSAGE);
        }
        List<ImportedTransactionBean> related;
        if (ParserUtils.equalsToZero(fee)) {
            related = Collections.emptyList();
        } else {
            related = List.of(
                new FeeRebateImportedTransactionBean(
                    null,
                    dateTime,
                    amountCurrency,
                    valueCurrency,
                    TransactionType.FEE,
                    fee,
                    feeCurrency

                )
            );
        }

        return new TransactionCluster(
            new BuySellImportedTransactionBean(
                null,          //uuid
                dateTime,           //executed
                amountCurrency,     //base
                valueCurrency,      //quote
                subType,            //action
                amountValue,        //base quantity
                evalUnitPrice(value, amountValue)   //unit price
            ),
            related
        );
    }
}

