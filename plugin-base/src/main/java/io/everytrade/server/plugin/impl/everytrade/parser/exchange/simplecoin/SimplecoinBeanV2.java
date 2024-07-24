package io.everytrade.server.plugin.impl.everytrade.parser.exchange.simplecoin;

import com.univocity.parsers.annotations.Format;
import com.univocity.parsers.annotations.Parsed;
import io.everytrade.server.model.Currency;
import io.everytrade.server.model.TransactionType;
import io.everytrade.server.plugin.impl.everytrade.parser.exception.DataIgnoredException;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean.BaseClusterData;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.kuCoin.BaseTransactionMapper;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Date;

@EqualsAndHashCode(callSuper = true)
@ToString
@Data
public class SimplecoinBeanV2 extends BaseTransactionMapper implements Cloneable {

    private static final String DELIVERED = "delivered to homre";

    private Instant dateDone;
    private String orderId;
    private Currency currencyFrom;
    private Currency currencyTo;
    private BigDecimal amountFrom;
    private BigDecimal amountTo;
    private String finalStatus;
    private Instant fromTxDate;
    private Instant toTxDate;
    private String fromBankAccountNumber;
    private String fromTxAddress;

    private Currency baseCurrency;
    private Currency quoteCurrency;
    private BigDecimal baseAmount;
    private BigDecimal quoteAmount;
    private TransactionType transactionType;
    private boolean unsupportedRow;

    @Parsed(field = "Date Done")
    @Format(formats = {"yyyy-MM-dd HH:mm:ss"}, options = {"locale=EN", "timezone=UTC"})
    public void setDateDone(Date dateDone) {
        this.dateDone = dateDone.toInstant();
    }

    @Parsed(field = "Order Id")
    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    @Parsed(field = "Currency From")
    public void setCurrencyFrom(String currencyFrom) {
        this.currencyFrom = Currency.valueOf(currencyFrom);
    }

    @Parsed(field = "Currency To")
    public void setCurrencyTo(String currencyTo) {
        this.currencyTo = Currency.valueOf(currencyTo);
    }

    @Parsed(field = "Amount From")
    public void setAmountFrom(BigDecimal amountFrom) {
        this.amountFrom = amountFrom;
    }

    @Parsed(field = "Amount To")
    public void setAmountTo(BigDecimal amountTo) {
        this.amountTo = amountTo;
    }

    @Parsed(field = "Final Status")
    public void setFinalStatus(String finalStatus) {
        if (!DELIVERED.equalsIgnoreCase(finalStatus)) {
            unsupportedRow = true;
        }
        this.finalStatus = finalStatus;
    }

    @Parsed(field = "From Tx Date")
    @Format(formats = {"yyyy-MM-dd HH:mm:ss"}, options = {"locale=EN", "timezone=UTC"})
    public void setFromTxDate(Date fromTxDate) {
        if (fromTxDate != null) {
            this.fromTxDate = fromTxDate.toInstant();
        }
    }

    @Parsed(field = "To Tx Date")
    @Format(formats = {"yyyy-MM-dd HH:mm:ss"}, options = {"locale=EN", "timezone=UTC"})
    public void setToTxDate(Date toTxDate) {
        if (toTxDate != null) {
            this.toTxDate = toTxDate.toInstant();
        }
    }

    @Parsed(field = "From Bank Account Number")
    public void setFromBankAccountNumber(String fromBankAccountNumber) {
        this.fromBankAccountNumber = fromBankAccountNumber;
    }

    @Parsed(field = "From Tx Address")
    public void setFromTxAddress(String fromTxAddress) {
        this.fromTxAddress = fromTxAddress;
    }

    public Object clone() throws CloneNotSupportedException {
        return super.clone();
    }

    @Override
    protected TransactionType findTransactionType() {
        return null;
    }

    @Override
    protected BaseClusterData mapData() {
        if (unsupportedRow) {
            throw new DataIgnoredException("Transaction final status is cancelled.");
        }

        return BaseClusterData.builder()
            .transactionType(transactionType)
            .executed(dateDone)
            .base(baseCurrency)
            .quote(quoteCurrency)
            .volume(baseAmount)
            .unitPrice(evalUnitPrice(quoteAmount, baseAmount))
            .address(fromTxAddress != null ? fromTxAddress : fromBankAccountNumber)
            .build();
    }
}
