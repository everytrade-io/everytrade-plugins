package io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v4;

import com.univocity.parsers.annotations.Format;
import com.univocity.parsers.annotations.Parsed;
import io.everytrade.server.model.Currency;
import io.everytrade.server.model.TransactionType;
import io.everytrade.server.plugin.api.parser.FeeRebateImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.ImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.TransactionCluster;
import io.everytrade.server.plugin.impl.everytrade.parser.ParserUtils;
import io.everytrade.server.plugin.impl.everytrade.parser.exception.DataIgnoredException;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.ExchangeBean;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.experimental.FieldDefaults;

import static io.everytrade.server.model.TransactionType.DEPOSIT;
import static io.everytrade.server.model.TransactionType.EARNING;
import static io.everytrade.server.model.TransactionType.REBATE;
import static io.everytrade.server.model.TransactionType.REWARD;
import static io.everytrade.server.model.TransactionType.STAKE;
import static io.everytrade.server.model.TransactionType.STAKING_REWARD;
import static io.everytrade.server.model.TransactionType.UNKNOWN;
import static io.everytrade.server.model.TransactionType.UNSTAKE;
import static io.everytrade.server.model.TransactionType.WITHDRAWAL;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v4.BinanceOperationTypeV4.OPERATION_TYPE_SAVING_DISTRIBUTION;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v4.BinanceOperationTypeV4.OPERATION_TYPE_SIMPLE_EARN_FLEXIBLE_REDEMPTION;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v4.BinanceOperationTypeV4.OPERATION_TYPE_SIMPLE_EARN_FLEXIBLE_SUBSCRIPTION;
import static java.util.Collections.emptyList;
import static lombok.AccessLevel.PRIVATE;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
@FieldDefaults(level = PRIVATE)
public class BinanceBeanV4 extends ExchangeBean {

    public static final String LD_COIN_CURRENCY_PREFIX = "LD";
    Instant date;
    String account;
    String userId;
    String originalOperation;
    BinanceOperationTypeV4 operationType;
    Currency coin;
    BigDecimal change;
    String remark;
    String originalCoin;
    boolean coinPrefix;

    int rowId;
    public List<Integer> usedIds = new ArrayList<>();

    boolean isInTransaction;
    boolean unsupportedRow;
    boolean ignoredRow;
    boolean failedRow;
    boolean ignoredFeeRow;
    boolean failedFeeRow;
    String message;
    boolean isMergedWithAnotherGroup;

    Currency marketBase;
    Currency marketQuote;
    TransactionType type;
    BigDecimal amountBase;
    BigDecimal amountQuote;
    BigDecimal fee;
    Currency feeCurrency;
    BigDecimal transactionPrice;

    public List<BinanceBeanV4> feeTransactions = new ArrayList<>();

    @Parsed(field = "User_ID")
    public void setUserId(String userId) {
        this.userId = userId;
    }

    @Parsed(field = "UTC_Time")
    @Format(formats = {"yyyy-MM-dd HH:mm:ss"}, options = {"locale=EN", "timezone=UTC"})
    public void setDate(@NonNull Date value) {
        date = value.toInstant();
    }

    public void setDate(Instant value) {
        date = value;
    }

    @Parsed(field = "Account")
    public void setAccount(String account) {
        account = account.toUpperCase();
        var supportedAccounts = BinanceSupportedOperations.SUPPORTED_ACCOUNT_TYPES;
        if (!supportedAccounts.contains(account)) {
            this.setUnsupportedRow(true);
            this.setMessage("Unsupported type of account " + account + "; ");
        }
        this.account = account;
    }

    @Parsed(field = "Operation")
    public void setOriginalOperation(String value) {
        value = value.toUpperCase();
        this.originalOperation = value;
        var supportedOperations = BinanceSupportedOperations.SUPPORTED_OPERATION_TYPES;
        if (!supportedOperations.contains(value)) {
            this.setUnsupportedRow(true);
            this.setMessage("Unsupported type of operation " + value);
        }
        if (BinanceSupportedOperations.WRITE_ORIGINAL_OPERATION_AS_NOTE.contains(value)) {
            setRemark(value);
        }
        try {
            this.operationType = BinanceOperationTypeV4.getEnum(value);
            this.type = BinanceSwitcher.operationTypeSwitcher(value);
        } catch (Exception ignore) {
        }
    }

    @Parsed(field = "Coin")
    public void setCoin(String coin) {
        try {
            if (coin.startsWith(LD_COIN_CURRENCY_PREFIX)
                && (originalOperation.equals(OPERATION_TYPE_SAVING_DISTRIBUTION.code)
                || originalOperation.equals(OPERATION_TYPE_SIMPLE_EARN_FLEXIBLE_SUBSCRIPTION.code)
                || originalOperation.equals(OPERATION_TYPE_SIMPLE_EARN_FLEXIBLE_REDEMPTION.code))) {
                this.originalCoin = coin;
                coin = coin.replaceFirst(LD_COIN_CURRENCY_PREFIX, "");
                coinPrefix = true;
            }
            this.coin = Currency.fromCode(coin);
        } catch (IllegalArgumentException e) {
            this.setUnsupportedRow(true);
            this.setMessage("Unsupported currency: " + coin + "; ");
        }
    }

    public void setCoin(Currency coin) {
        this.coin = coin;
    }

    @Parsed(field = "Change")
    public void setChange(String change) {
        try {
            this.change = new BigDecimal(change);
        } catch (NumberFormatException e) {
            this.setUnsupportedRow(true);
            this.setMessage("Wrong \"Change\" value: " + change + "; ");
        }
    }

    public void setChange(BigDecimal change) {
        this.change = change;
    }

    @Parsed(field = "Remark")
    public void setRemark(String remark) {
        this.remark = remark;
    }

    public void setNote(String remark) {
        this.remark = remark;
    }

    public void setRowId(int rowId) {
        String sub = (message == null) ? "" : message;
        message = "Row id " + rowId + ": " + sub;
        this.rowId = rowId;
    }

    public void setMessage(String message) {
        String sub = (this.message == null) ? "" : (this.message + "; ");
        this.message = sub + message;
    }

    public void setMergedWithAnotherGroup(boolean mergedWithAnotherGroup) {
        if (mergedWithAnotherGroup) {
            this.setMessage("Grouped with tolerance 1s ");
        }
        isMergedWithAnotherGroup = mergedWithAnotherGroup;
    }

    @Override
    public TransactionCluster toTransactionCluster() {
        final List<ImportedTransactionBean> related = new ArrayList<>();
        if (UNKNOWN.equals(type) || isUnsupportedRow()) {
            throw new DataIgnoredException(getMessage());
        }
        if (BinanceSupportedOperations.WRITE_ORIGINAL_OPERATION_AS_NOTE.contains(originalOperation)) {
            remark = originalOperation;
            if (isCoinPrefix()) {
                remark += ", " + originalCoin;
            }
        }
        if (feeTransactions.size() > 0) {
            for (BinanceBeanV4 fee : feeTransactions) {
                var feeTxs = new FeeRebateImportedTransactionBean(
                    null,
                    fee.getDate(),
                    fee.getFeeCurrency(),
                    fee.getFeeCurrency(),
                    TransactionType.FEE,
                    fee.getFee().abs(),
                    fee.getFeeCurrency()
                );
                related.add(feeTxs);
            }
        }

        if (REWARD.equals(type)) {
            return new TransactionCluster(
                new ImportedTransactionBean(
                    null,
                    date,
                    marketBase,
                    marketBase,
                    REWARD,
                    amountBase,
                    null,
                    remark,
                    null
                ),
                emptyList()
            );
        }

        if (REBATE.equals(type)) {
            return new TransactionCluster(
                new FeeRebateImportedTransactionBean(
                    null,
                    date,
                    marketBase,
                    marketBase,
                    REBATE,
                    amountBase,
                    marketBase,
                    remark
                ),
                emptyList()
            );
        }

        if (EARNING.equals(type)) {
            return new TransactionCluster(
                new ImportedTransactionBean(
                    null,
                    date,
                    marketBase,
                    marketBase,
                    EARNING,
                    amountBase,
                    null,
                    remark,
                    null,
                    null
                ),
                emptyList()
            );
        }

        if (STAKE.equals(type)) {
            return new TransactionCluster(
                new ImportedTransactionBean(
                    null,
                    date,
                    marketBase,
                    marketBase,
                    STAKE,
                    amountBase,
                    null,
                    remark,
                    null,
                    null
                ),
                emptyList()
            );
        }

        if (STAKING_REWARD.equals(type)) {
            return new TransactionCluster(
                new ImportedTransactionBean(
                    null,
                    date,
                    marketBase,
                    marketBase,
                    STAKING_REWARD,
                    amountBase,
                    null,
                    remark,
                    null,
                    null
                ),
                emptyList()
            );
        }

        if (UNSTAKE.equals(type)) {
            return new TransactionCluster(
                new ImportedTransactionBean(
                    null,
                    date,
                    marketBase,
                    marketBase,
                    UNSTAKE,
                    amountBase,
                    null,
                    remark,
                    null,
                    null
                ),
                emptyList()
            );
        }

        if (List.of(DEPOSIT, WITHDRAWAL).contains(this.type)) {
            TransactionCluster cluster = new TransactionCluster(
                ImportedTransactionBean.createDepositWithdrawal(
                    null,
                    date,
                    marketBase,
                    marketBase,
                    type,
                    amountBase,
                    null,
                    remark,
                    null
                ),
                related
            );
            return cluster;
        } else {
            validateCurrencyPair(marketBase, marketQuote);
            TransactionCluster cluster = new TransactionCluster(
                new ImportedTransactionBean(
                    null,
                    date,
                    marketBase,
                    marketQuote,
                    type,
                    amountBase.setScale(ParserUtils.DECIMAL_DIGITS, ParserUtils.ROUNDING_MODE),
                    (amountQuote != null) ? evalUnitPrice(amountQuote, amountBase) : null,
                    remark,
                    null
                ),
                related
            );
            return cluster;
        }
    }
}
