package io.everytrade.server.parser.exchange;

import com.univocity.parsers.common.DataValidationException;
import io.everytrade.server.model.Currency;
import io.everytrade.server.model.CurrencyPair;
import io.everytrade.server.model.TransactionType;
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

import static io.everytrade.server.model.TransactionType.DEPOSIT;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.ExchangeBean.FEE_UID_PART;
import static java.math.BigDecimal.ZERO;
import static lombok.AccessLevel.PRIVATE;

@Data
@NoArgsConstructor
@FieldDefaults(level = PRIVATE)
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
        if (quantity.compareTo(ZERO) == 0) {
            throw new DataValidationException("Quantity can not be zero.");
        }
        var tx = ImportedTransactionBean.createDepositWithdrawal(
            uid,
            timestamp,
            Currency.fromCode(base),
            Currency.fromCode(base),
            action,
            quantity,
            action == DEPOSIT ? addressFrom : addressTo
        );
        return new TransactionCluster(tx, getRelatedTxs());
    }

    private TransactionCluster createBuySellTransactionCluster() {
        if (quantity.compareTo(ZERO) == 0) {
            throw new DataValidationException("Quantity can not be zero.");
        }

        // try to validate pair
        new CurrencyPair(base, quote);
        var tx = new ImportedTransactionBean(
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
        if (fee != null && fee.compareTo(ZERO) > 0) {
            related.add(createFeeTransactionBean(false));
        }

        if (rebate != null && rebate.compareTo(ZERO) > 0) {
            related.add(createRebateTransactionBean(false));
        }
        return related;
    }

    private FeeRebateImportedTransactionBean createFeeTransactionBean(boolean unrelated) {
        return new FeeRebateImportedTransactionBean(
            unrelated ? uid : uid + FEE_UID_PART,
            timestamp,
            Currency.fromCode(feeCurrency),
            Currency.fromCode(feeCurrency),
            TransactionType.FEE,
            fee,
            Currency.fromCode(feeCurrency)
        );
    }

    private FeeRebateImportedTransactionBean createRebateTransactionBean(boolean unrelated) {
        return new FeeRebateImportedTransactionBean(
            unrelated ? uid : uid + FEE_UID_PART,
            timestamp,
            Currency.fromCode(rebateCurrency),
            Currency.fromCode(rebateCurrency),
            TransactionType.REBATE,
            rebate,
            Currency.fromCode(rebateCurrency)
        );
    }
}
