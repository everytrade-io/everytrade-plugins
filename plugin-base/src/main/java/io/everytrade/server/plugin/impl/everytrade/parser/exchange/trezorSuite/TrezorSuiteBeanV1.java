package io.everytrade.server.plugin.impl.everytrade.parser.exchange.trezorSuite;

import com.univocity.parsers.annotations.Parsed;
import com.univocity.parsers.common.DataValidationException;
import io.everytrade.server.model.Currency;
import io.everytrade.server.model.TransactionType;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean.BaseClusterData;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.kuCoin.BaseTransactionMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.Instant;

@EqualsAndHashCode(callSuper = true)
@Data
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = lombok.AccessLevel.PRIVATE)
public class TrezorSuiteBeanV1 extends BaseTransactionMapper {

    String timestamp;
    String date;
    String time;
    String type;
    String transactionId;
    BigDecimal fee;
    String feeUnit;
    String address;
    String label;
    BigDecimal amount;
    Currency amountUnit;
    BigDecimal fiatEur;
    String other;

    Instant dateTime;
    Currency baseCurrency;
    Currency quoteCurrency;
    BigDecimal baseAmount;
    BigDecimal quoteAmount;
    TransactionType transactionType;

    public TrezorSuiteBeanV1(Instant dateTime,
                             Currency baseCurrency,
                             Currency quoteCurrency,
                             BigDecimal baseAmount,
                             BigDecimal quoteAmount,
                             String address,
                             String feeUnit,
                             BigDecimal fee,
                             String label,
                             TransactionType transactionType
    ) {
        this.dateTime = dateTime;
        this.baseCurrency = baseCurrency;
        this.quoteCurrency = quoteCurrency;
        this.baseAmount = baseAmount;
        this.quoteAmount = quoteAmount;
        this.address = address;
        this.feeUnit = feeUnit;
        this.fee = fee;
        this.label = label;
        this.transactionType = transactionType;
    }

    @Parsed(field = "Timestamp")
    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    @Parsed(field = "Date")
    public void setDate(String date) {
        this.date = date;
    }

    @Parsed(field = "Time")
    public void setTime(String time) {
        this.time = time;
    }

    @Parsed(field = "Type")
    public void setType(String type) {
        this.type = type;
    }

    @Parsed(field = "Transaction ID")
    public void setTransactionId(String transactionId) {
        this.transactionId = transactionId;
    }

    @Parsed(field = "Fee")
    public void setFee(BigDecimal fee) {
        this.fee = fee;
    }

    @Parsed(field = "Fee unit")
    public void setFeeUnit(String feeUnit) {
        this.feeUnit = feeUnit;
    }

    @Parsed(field = "Address")
    public void setAddress(String address) {
        this.address = address;
    }

    @Parsed(field = "Label")
    public void setLabel(String label) {
        this.label = label;
    }

    @Parsed(field = "Amount")
    public void setAmount(BigDecimal amount) {
        if (amount == null) {
            throw new DataValidationException("Amount cannot be null");
        }
        this.amount = amount;
    }

    @Parsed(field = "Amount unit")
    public void setAmountUnit(String amountUnit) {
        this.amountUnit = amountUnit != null ? Currency.fromCode(amountUnit) : null;
    }

    @Parsed(index = 11)
    public void setFiatEur(String fiatEur) {
        if (fiatEur != null) {
            try {
                this.fiatEur = new BigDecimal(fiatEur.replace(",", ""));
            } catch (NumberFormatException ignored) {
                // Ignore invalid number format
            }
        }
    }

    @Parsed(field = "Other")
    public void setOther(String other) {
        this.other = other;
    }

    @Override
    protected TransactionType findTransactionType() {
        return null;
    }

    @Override
    protected BaseClusterData mapData() {
        return BaseClusterData.builder()
            .transactionType(transactionType)
            .executed(dateTime)
            .base(baseCurrency)
            .quote(quoteCurrency)
            .volume(baseAmount)
            .fee(feeUnit)
            .feeAmount(fee)
            .address(address)
            .note(label)
            .build();
    }
}
