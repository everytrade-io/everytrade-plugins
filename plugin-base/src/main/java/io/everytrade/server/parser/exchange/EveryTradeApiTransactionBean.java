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
import static io.everytrade.server.plugin.impl.everytrade.parser.ParserUtils.DECIMAL_DIGITS;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.ExchangeBean.FEE_UID_PART;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.ExchangeBean.REBATE_UID_PART;
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
    String addressFrom;
    String addressTo;
    String note;
    String labels;
    String partner;
    String reference;

    public void setTimestamp(Long timestamp) {
        this.timestamp = new Date(timestamp * 1000).toInstant();
    }

    public void setAction(String action) {
        this.action = TransactionType.valueOf(action);
    }

    public TransactionCluster toTransactionCluster() {
        return switch (action) {
            case BUY, SELL -> createBuySellTransactionCluster();
            case DEPOSIT, WITHDRAWAL -> createDepositOrWithdrawalTxCluster();
            case FEE -> new TransactionCluster(createFeeTransactionBean(true), List.of());
            case REBATE, STAKE, UNSTAKE, STAKING_REWARD, AIRDROP, EARNING, REWARD, FORK -> createOtherTransactionCluster();
            default -> throw new IllegalStateException(String.format("Unsupported transaction type %s.", action));
        };
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
            action == DEPOSIT ? addressFrom : addressTo,
            note,
            labels
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
            volume.divide(quantity, DECIMAL_DIGITS, RoundingMode.HALF_UP),
            note,
            null,
            labels,
            partner,
            reference
        );
        return new TransactionCluster(tx, getRelatedTxs());
    }

    private TransactionCluster createOtherTransactionCluster() {
        if (quantity.compareTo(ZERO) == 0) {
            throw new DataValidationException("Quantity can not be zero.");
        }
        Currency txCurrency;
        try {
             txCurrency = Currency.fromCode(this.base);
        } catch (Exception e) {
            try {
                txCurrency =  Currency.fromCode(this.quote);
            } catch (Exception ex) {
                throw new DataValidationException(String.format("Cannot find currency for transaction type %s. ", action));
            }
        }
        var tx = new ImportedTransactionBean(
            uid,
            timestamp,
            txCurrency,
            txCurrency,
            action,
            quantity,
            volume == null ? null : volume.divide(quantity, DECIMAL_DIGITS, RoundingMode.HALF_UP),
            note,
            null,
            labels,
            partner,
            reference
        );
        return new TransactionCluster(tx, getRelatedTxs());
    }

    private List<ImportedTransactionBean> getRelatedTxs() {
        var related = new ArrayList<ImportedTransactionBean>();
        if (fee != null && fee.compareTo(ZERO) > 0) {
            related.add(createFeeTransactionBean(false));
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
            Currency.fromCode(feeCurrency),
            note,
            null,
            labels,
            partner,
            reference
        );
    }

}
