package io.everytrade.server.parser.exchange;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.univocity.parsers.common.DataValidationException;
import io.everytrade.server.model.Currency;
import io.everytrade.server.model.TransactionType;
import io.everytrade.server.plugin.api.parser.BuySellImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.DepositWithdrawalImportedTransaction;
import io.everytrade.server.plugin.api.parser.FeeRebateImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.ImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.TransactionCluster;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;

import static io.everytrade.server.model.TransactionType.DEPOSIT;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.ExchangeBean.FEE_UID_PART;
import static lombok.AccessLevel.PRIVATE;

@Data
@NoArgsConstructor
@FieldDefaults(level = PRIVATE)
@JsonFormat(shape = JsonFormat.Shape.ARRAY)
@JsonPropertyOrder({
    "uid", "timestamp", "base", "quote", "action", "quantity", "volume", "fee", "feeCurrency", "rebate", "rebateCurrency",
    "addressFrom", "addressTo"
})
public class EveryTradeApiTransactionBean {

    String uid;
    Instant timestamp;
    String base;
    String quote;
    TransactionType action;
    BigDecimal quantity;
    BigDecimal volume;
    BigDecimal fee;
    String feeCurrency;
    BigDecimal rebate;
    String rebateCurrency;
    String addressFrom;
    String addressTo;

    public void setTimestamp(Date timestamp) {
        this.timestamp = timestamp.toInstant();
    }

    public void setAction(String action) {
        this.action = TransactionType.valueOf(action);
    }

    public TransactionCluster toTransactionCluster() {
        switch (action) {
            case BUY:
            case SELL:
                return createBuySellTransactionCluster();
            case DEPOSIT:
            case WITHDRAWAL:
                return createDepositOrWithdrawalTxCluster();
            case FEE:
                return new TransactionCluster(createFeeTransactionBean(true), List.of());
            case REBATE:
                return new TransactionCluster(createRebateTransactionBean(true), List.of());
            default:
                throw new IllegalStateException(String.format("Unsupported transaction type %s.", action));
        }
    }

    private TransactionCluster createDepositOrWithdrawalTxCluster() {
        if (quantity.compareTo(BigDecimal.ZERO) == 0) {
            throw new DataValidationException("Quantity can not be zero.");
        }
        var tx = new DepositWithdrawalImportedTransaction(
            uid,
            timestamp,
            Currency.fromCode(base),
            Currency.fromCode(quote),
            action,
            quantity,
            action == DEPOSIT ? addressFrom : addressTo
        );
        return new TransactionCluster(tx, getRelatedTxs());
    }

    private TransactionCluster createBuySellTransactionCluster() {
        if (quantity.compareTo(BigDecimal.ZERO) == 0) {
            throw new DataValidationException("Quantity can not be zero.");
        }

        var tx = new BuySellImportedTransactionBean(
            uid,
            timestamp,
            Currency.fromCode(base),
            Currency.fromCode(quote),
            action,
            quantity,
            volume.divide(quantity, 10, RoundingMode.HALF_UP)
        );
        return new TransactionCluster(tx, getRelatedTxs());
    }

    private List<ImportedTransactionBean> getRelatedTxs() {
        var related = new ArrayList<ImportedTransactionBean>();
        if (fee.compareTo(BigDecimal.ZERO) > 0) {
            related.add(createFeeTransactionBean(false));
        }

        if (rebate.compareTo(BigDecimal.ZERO) > 0) {
            related.add(createRebateTransactionBean(false));
        }
        return related;
    }

    private FeeRebateImportedTransactionBean createFeeTransactionBean(boolean unrelated) {
        var feeCurrencyIsBase = Objects.equals(feeCurrency, base);
        var feeCurrencyIsQuote = Objects.equals(feeCurrency, quote);

        if (!feeCurrencyIsBase && !feeCurrencyIsQuote) {
            throw new DataValidationException(
                String.format("Fee currency '%s' differs to base '%s' and to quote '%s'.", feeCurrency, base, quote)
            );
        }

        return new FeeRebateImportedTransactionBean(
            unrelated ? uid : uid + FEE_UID_PART,
            timestamp,
            Currency.fromCode(base),
            Currency.fromCode(quote),
            TransactionType.FEE,
            fee,
            Currency.fromCode(feeCurrency)
        );
    }

    private FeeRebateImportedTransactionBean createRebateTransactionBean(boolean unrelated) {
        var rebateCurrencyIsBase = Objects.equals(rebateCurrency, base);
        var rebateCurrencyIsQuote = Objects.equals(rebateCurrency, quote);
        if (!rebateCurrencyIsBase && !rebateCurrencyIsQuote) {
            throw new DataValidationException(
                String.format("Rebate currency '%s' differs to base '%s' and to quote '%s'.", rebateCurrency, base, quote)
            );
        }

        return new FeeRebateImportedTransactionBean(
            unrelated ? uid : uid + FEE_UID_PART,
            timestamp,
            Currency.fromCode(base),
            Currency.fromCode(quote),
            TransactionType.REBATE,
            rebate,
            Currency.fromCode(rebateCurrency)
        );
    }
}
