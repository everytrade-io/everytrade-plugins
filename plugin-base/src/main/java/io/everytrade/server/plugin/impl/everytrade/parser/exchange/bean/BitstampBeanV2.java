package io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean;

import com.univocity.parsers.annotations.Parsed;
import com.univocity.parsers.common.DataValidationException;
import io.everytrade.server.model.Currency;
import io.everytrade.server.model.TransactionType;
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
import java.util.List;

/**
 * Bitstamp "Transactions" export, new (2025) layout: 14 columns with SEPARATE currency columns, an
 * ISO-8601 {@code Datetime}, a spaceless {@code Subtype} and an {@code Order ID}. The old format
 * (inline currency such as {@code "1.0 LTC"}, {@code MMM. dd, yyyy} date, {@code Sub Type}) is handled
 * by {@link BitstampBeanV1}; both are registered so each file resolves to the matching parser.
 *
 * <p>Header:
 * {@code ID,Account,Type,Subtype,Datetime,Amount,Amount currency,Value,Value currency,Rate,Rate currency,
 * Fee,Fee currency,Order ID}
 */
public class BitstampBeanV2 extends ExchangeBean {
    public static final String CURRENCY_EQUALITY_MESSAGE = "Value currency and rate currency are not equal.";

    private String type;         // Market / Deposit / Withdrawal
    private String subtypeRaw;   // Buy / Sell / (empty for Deposit/Withdrawal)
    private Instant dateTime;
    private Currency amountCurrency;
    private BigDecimal amountValue;
    private Currency valueCurrency;
    private BigDecimal value;
    private Currency rateCurrency;
    private BigDecimal fee = BigDecimal.ZERO;
    private Currency feeCurrency;

    @Parsed(field = "Type")
    public void setType(String field) {
        this.type = field;
    }

    @Parsed(field = "Subtype")
    public void setSubtype(String field) {
        this.subtypeRaw = field;
    }

    @Parsed(field = "Datetime")
    public void setDatetime(String field) {
        // ISO-8601, e.g. 2025-06-03T09:35:39Z
        this.dateTime = Instant.parse(field);
    }

    @Parsed(field = "Amount")
    public void setAmount(String field) {
        if (field == null || field.isBlank()) {
            throw new DataValidationException("BaseQuantity can not be null or empty.");
        }
        BigDecimal quantity = new BigDecimal(field);
        if (quantity.compareTo(BigDecimal.ZERO) == 0) {
            throw new DataValidationException("BaseQuantity can not be zero.");
        }
        this.amountValue = quantity;
    }

    @Parsed(field = "Amount currency")
    public void setAmountCurrency(String field) {
        this.amountCurrency = Currency.fromCode(field);
    }

    @Parsed(field = "Value")
    public void setValue(String field) {
        this.value = (field == null || field.isBlank()) ? null : new BigDecimal(field);
    }

    @Parsed(field = "Value currency")
    public void setValueCurrency(String field) {
        if (field != null && !field.isBlank()) {
            this.valueCurrency = Currency.fromCode(field);
        }
    }

    @Parsed(field = "Rate currency")
    public void setRateCurrency(String field) {
        if (field != null && !field.isBlank()) {
            this.rateCurrency = Currency.fromCode(field);
        }
    }

    @Parsed(field = "Fee")
    public void setFee(String field) {
        this.fee = (field == null || field.isBlank()) ? BigDecimal.ZERO : new BigDecimal(field);
    }

    @Parsed(field = "Fee currency")
    public void setFeeCurrency(String field) {
        if (field != null && !field.isBlank()) {
            this.feeCurrency = Currency.fromCode(field);
        }
    }

    @Override
    public TransactionCluster toTransactionCluster() {
        final TransactionType txType = resolveTransactionType();
        switch (txType) {
            case BUY:
            case SELL:
                return createBuySellTransactionCluster(txType);
            case DEPOSIT:
            case WITHDRAWAL:
                return createDepositOrWithdrawalTxCluster(txType);
            default:
                throw new DataIgnoredException(UNSUPPORTED_TRANSACTION_TYPE.concat(String.valueOf(txType)));
        }
    }

    private TransactionType resolveTransactionType() {
        if ("Deposit".equalsIgnoreCase(type) || "Withdrawal".equalsIgnoreCase(type)) {
            return detectTransactionType(type);
        }
        // Market row -> the direction lives in Subtype (Buy/Sell).
        if (subtypeRaw == null || subtypeRaw.isBlank()) {
            throw new DataIgnoredException(UNSUPPORTED_TRANSACTION_TYPE.concat(String.valueOf(type)));
        }
        return detectTransactionType(subtypeRaw);
    }

    private TransactionCluster createBuySellTransactionCluster(TransactionType txType) {
        validateCurrencyPair(amountCurrency, valueCurrency);
        if (rateCurrency != null && !valueCurrency.equals(rateCurrency)) {
            throw new DataValidationException(CURRENCY_EQUALITY_MESSAGE);
        }
        final List<ImportedTransactionBean> related;
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
            new ImportedTransactionBean(
                null,                               // uid
                dateTime,                           // executed
                amountCurrency,                     // base
                valueCurrency,                      // quote
                txType,                             // action
                amountValue,                        // base quantity
                evalUnitPrice(value, amountValue)   // unit price (Value / Amount, matching BitstampBeanV1)
            ),
            related
        );
    }

    private TransactionCluster createDepositOrWithdrawalTxCluster(TransactionType txType) {
        var tx = ImportedTransactionBean.createDepositWithdrawal(
            null,
            dateTime,
            amountCurrency,  // base
            valueCurrency,   // quote (null for deposits/withdrawals, as in BitstampBeanV1)
            txType,
            amountValue,
            null
        );
        return new TransactionCluster(tx, getRelatedFeeTransaction());
    }

    private List<ImportedTransactionBean> getRelatedFeeTransaction() {
        if (ParserUtils.equalsToZero(fee)) {
            return Collections.emptyList();
        }
        return List.of(
            new FeeRebateImportedTransactionBean(
                FEE_UID_PART,
                dateTime,
                feeCurrency,
                feeCurrency,
                TransactionType.FEE,
                fee.setScale(ParserUtils.DECIMAL_DIGITS, RoundingMode.HALF_UP),
                feeCurrency
            )
        );
    }
}
