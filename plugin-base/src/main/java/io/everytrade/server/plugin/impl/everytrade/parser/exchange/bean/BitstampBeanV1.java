package io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean;

import com.univocity.parsers.annotations.Format;
import com.univocity.parsers.annotations.Headers;
import com.univocity.parsers.annotations.Parsed;
import com.univocity.parsers.common.DataValidationException;
import io.everytrade.server.model.Currency;
import io.everytrade.server.model.TransactionType;
import io.everytrade.server.plugin.api.parser.BuySellImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.DepositWithdrawalImportedTransaction;
import io.everytrade.server.plugin.api.parser.FeeRebateImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.ImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.TransactionCluster;
import io.everytrade.server.plugin.impl.everytrade.parser.ParserUtils;
import io.everytrade.server.plugin.impl.everytrade.parser.exception.DataIgnoredException;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.ExchangeBean;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Collections;
import java.util.Date;
import java.util.List;

@Headers(sequence = {"Type", "DateTime", "Amount", "Value", "Rate", "Fee", "Sub Type"}, extract = true)
public class BitstampBeanV1 extends ExchangeBean {
    public static final String CURRENCY_EQUALITY_MESSAGE = "Value currency, rate currency and fee currency not equals.";
    private String type;
    private Instant dateTime;
    private Currency amountCurrency;
    private BigDecimal amountValue;
    private Currency valueCurrency;
    private BigDecimal value;
    private BigDecimal fee;
    private TransactionType subType;
    private Currency rateCurrency;
    private Currency feeCurrency;

    @Parsed(field = "Type")
    public void setType(String field) {
        if(!field.equals("Market")) {
            this.type = field;
        }
    }

    @Parsed(field = "Datetime")
    @Format(formats = {"MMM. dd, yyyy, hh:mm a"}, options = {"locale=US", "timezone=UTC"})
    public void setDate(Date date) {
        dateTime = date.toInstant();
    }

    @Parsed(field = "Amount")
    public void setAmount(String amount) {
        String[] amountParts = amount.split(" ");
        String mBase = amountParts[1];
        amountCurrency = Currency.fromCode(mBase);
        if (amountParts[0].isEmpty()) {
            throw new DataValidationException("BaseQuantity can not be null or empty.");
        }
        BigDecimal quantity = new BigDecimal(amountParts[0]);
        if (quantity.compareTo(BigDecimal.ZERO) == 0) {
            throw new DataValidationException("BaseQuantity can not be zero.");
        }
        amountValue = quantity;
    }

    @Parsed(field = "Value", defaultNullRead = "")
    public void setValue(String value) {
        String[] valueParts = value.split(" ");
        if (valueParts[0].equals(value)) {
            this.value = BigDecimal.ZERO;
        } else {
            this.value = new BigDecimal(valueParts[0]);
            valueCurrency = Currency.fromCode(valueParts[1]);
        }
    }

    @Parsed(field = "Rate", defaultNullRead = "")
    public void setRate(String rate) {
        if(!rate.equals("")) {
            rateCurrency = Currency.fromCode(rate.split(" ")[1]);
        }
    }

    @Parsed(field = "Fee", defaultNullRead = "")
    public void setFee(String fee) {
        String[] feeParts = fee.split(" ");
        if (feeParts[0].equals("")) {
            this.fee = BigDecimal.ZERO;
        } else {
            this.fee = new BigDecimal(feeParts[0]);
            feeCurrency = Currency.fromCode(feeParts[1]);
        }
    }

    @Parsed(field = "Sub Type", defaultNullRead = "")
    public void setSubType(String field) {
        if (!field.equals("")) {
            subType = detectTransactionType(field);
        } else if (type.equals("Deposit") || type.equals("Withdrawal")) {
            subType = detectTransactionType(type);
        } else {
            throw new DataIgnoredException(UNSUPPORTED_TRANSACTION_TYPE.concat(field));
        }
    }

    @Override
    public TransactionCluster toTransactionCluster() {
        switch (this.subType) {
            case BUY:
            case SELL:
                return createBuySellTransactionCluster();
            case DEPOSIT:
            case WITHDRAWAL:
                return createDepositOrWithdrawalTxCluster();
            default:
                throw new IllegalStateException(String.format("Unsupported transaction type %s.", subType.name()));
        }
    }

    private TransactionCluster createBuySellTransactionCluster(){
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
                    feeCurrency,
                    feeCurrency,
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

    private TransactionCluster createDepositOrWithdrawalTxCluster() {
        var tx = new DepositWithdrawalImportedTransaction(
            null,
            dateTime,
            amountCurrency, //base
            valueCurrency,  //quote
            subType,
            amountValue,
            null
        );
        return new TransactionCluster(tx, getRelatedFeeTransaction());
    }

    private List<ImportedTransactionBean> getRelatedFeeTransaction() {
        List<ImportedTransactionBean> related;
        if (ParserUtils.equalsToZero(fee)) {
            related = Collections.emptyList();
        } else {
            related = List.of(
                new FeeRebateImportedTransactionBean(
                    null + FEE_UID_PART,
                    dateTime,
                    amountCurrency,
                    valueCurrency,
                    TransactionType.FEE,
                    fee.setScale(ParserUtils.DECIMAL_DIGITS, RoundingMode.HALF_UP),
                    feeCurrency
                )
            );
        }
        return related;
    }

}

