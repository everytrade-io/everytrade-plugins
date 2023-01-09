package io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v4;

import com.univocity.parsers.annotations.Format;
import com.univocity.parsers.annotations.Headers;
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

import static io.everytrade.server.model.TransactionType.REWARD;
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
@Headers(sequence = {"Operation", "UTC_Time", "Account", "Coin", "Change", "Remark", "User_ID"}, extract = true)
public class BinanceBeanV4 extends ExchangeBean {

    Instant date;
    String account;
    String userId;
    String operation;
    Currency coin;
    BigDecimal change;
    String remark;

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
        var supportedAccounts = BinanceSupportedOperations.SUPPORTED_ACCOUNT_TYPES;
        if (!supportedAccounts.contains(account)) {
            this.setUnsupportedRow(true);
            this.setMessage("Unsupported type of account " + account + "; ");
        }
        this.account = account;
    }

    @Parsed(field = "Operation")
    public void setOperation(String operation) {
        var supportedOperations = BinanceSupportedOperations.SUPPORTED_OPERATION_TYPES;
        if (!supportedOperations.contains(operation)) {
            this.setUnsupportedRow(true);
            this.setMessage("Unsupported type of operation " + operation);
        }
        this.operation = operation;
        this.type = BinanceSwitcher.operationTypeSwitcher(operation);
    }

    @Parsed(field = "Coin")
    public void setCoin(String coin) {
        try {
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
        if (TransactionType.UNKNOWN.equals(type) || isUnsupportedRow()) {
            throw new DataIgnoredException(getMessage());
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

        if (TransactionType.REWARD.equals(type)) {
            return new TransactionCluster(
                new ImportedTransactionBean(
                    usedIds.toString(),
                    date,
                    marketBase,
                    marketBase,
                    REWARD,
                    amountBase,
                    null
                ),
                emptyList()
            );
        }

        if (List.of(TransactionType.DEPOSIT, TransactionType.WITHDRAWAL).contains(this.type)) {
            TransactionCluster cluster = new TransactionCluster(
                ImportedTransactionBean.createDepositWithdrawal(
                    usedIds.toString(),
                    date,
                    marketBase,
                    marketBase,
                    type,
                    amountBase,
                    remark
                ),
                related
            );
            return cluster;
        } else {
            validateCurrencyPair(marketBase, marketQuote);
            TransactionCluster cluster = new TransactionCluster(
                new ImportedTransactionBean(
                    usedIds.toString(),
                    date,
                    marketBase,
                    marketQuote,
                    type,
                    amountBase.setScale(ParserUtils.DECIMAL_DIGITS, ParserUtils.ROUNDING_MODE),
                    evalUnitPrice(amountQuote, amountBase),
                    remark,
                    null
                ),
                related
            );
            return cluster;
        }
    }
}
