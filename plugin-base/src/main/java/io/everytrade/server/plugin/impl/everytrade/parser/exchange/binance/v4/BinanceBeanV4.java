package io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v4;

import com.univocity.parsers.annotations.Format;
import com.univocity.parsers.annotations.Headers;
import com.univocity.parsers.annotations.Parsed;
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
import lombok.NonNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

@Headers(sequence = {"Operation", "UTC_Time", "Account", "Coin", "Change", "Remark", "User_ID"}, extract = true)
public class BinanceBeanV4 extends ExchangeBean {

    private Instant date;
    private String account;
    private String userId;
    private String operation;
    private Currency coin;
    private BigDecimal change;
    private String remark;

    private int rowId;
    public List<Integer> usedIds = new ArrayList<>();

    private boolean isInTransaction;
    private boolean unsupportedRow;
    private String message;
    private boolean isMergedWithAnotherGroup;

    private Currency marketBase;
    private Currency marketQuote;
    private TransactionType type;
    private BigDecimal amountBase;
    private BigDecimal amountQuote;
    private BigDecimal fee;
    private Currency feeCurrency;
    private BigDecimal transactionPrice;

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
        var supportedAccounts = BinanceSupportedOperations.supportedAccountTypes;
        if (!supportedAccounts.contains(account)) {
            this.setUnsupportedRow(true);
            this.setMessage("Unsupported type of account " + account + " ");
        }
        this.account = account;
    }

    @Parsed(field = "Operation")
    public void setOperation(String operation) {
        var supportedOperations = BinanceSupportedOperations.supportedOperationTypes;
        if (!supportedOperations.contains(operation)) {
            this.setUnsupportedRow(true);
            this.setMessage("Unsupported type of operation " + operation + " ");
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
            this.setMessage(e.getMessage());
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
            this.setMessage(e.getMessage());
        }
    }

    public void setChange(BigDecimal change) {
        this.change = change;
    }

    @Parsed(field = "Remark")
    public void setRemark(String remark) {
        this.remark = remark;
    }

    public Currency getCoin() {
        return coin;
    }

    public Instant getDate() {
        return date;
    }

    public String getAccount() {
        return account;
    }

    public Currency getMarketBase() {
        return marketBase;
    }

    public Currency getMarketQuote() {
        return marketQuote;
    }

    public String getOperation() {
        return operation;
    }

    public TransactionType getType() {
        return type;
    }

    public BigDecimal getAmountBase() {
        return amountBase;
    }

    public BigDecimal getFee() {
        return fee;
    }

    public Currency getFeeCurrency() {
        return feeCurrency;
    }

    public String getRemark() {
        return remark;
    }

    public BigDecimal getTransactionPrice() {
        return transactionPrice;
    }

    public void setTransactionPrice(BigDecimal transactionPrice) {
        this.transactionPrice = transactionPrice;
    }

    public void setMarketQuote(Currency marketQuote) {
        this.marketQuote = marketQuote;
    }

    public void setType(TransactionType type) {
        this.type = type;
    }

    public void setAmountBase(BigDecimal amountBase) {
        this.amountBase = amountBase;
    }

    public void setFee(BigDecimal fee) {
        this.fee = fee;
    }

    public void setFeeCurrency(Currency feeCurrency) {
        this.feeCurrency = feeCurrency;
    }

    public boolean isInTransaction() {
        return isInTransaction;
    }

    public void setInTransaction(boolean inTransaction) {
        this.isInTransaction = inTransaction;
    }

    public boolean isUnsupportedRow() {
        return unsupportedRow;
    }

    public void setUnsupportedRow(boolean unsupportedRow) {
        this.unsupportedRow = unsupportedRow;
    }

    public int getRowId() {
        return rowId;
    }

    public void setRowId(int rowId) {
        this.message = "Row id " + rowId;
        this.rowId = rowId;
    }

    public BigDecimal getChange() {
        return change;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = this.message + "; " + message;
    }

    public boolean isMergedWithAnotherGroup() {
        return isMergedWithAnotherGroup;
    }

    public void setMergedWithAnotherGroup(boolean mergedWithAnotherGroup) {
        if (mergedWithAnotherGroup) {
            this.setMessage("Grouped with tolerance 1s ");
        }
        isMergedWithAnotherGroup = mergedWithAnotherGroup;
    }

    public void setMarketBase(Currency marketBase) {
        this.marketBase = marketBase;
    }

    public BigDecimal getAmountQuote() {
        return amountQuote;
    }

    public void setAmountQuote(BigDecimal amountQuote) {
        this.amountQuote = amountQuote;
    }

    public void checkCurrencyPairForRows() {
        this.validateCurrencyPair(marketBase, marketQuote);
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

        if (List.of(TransactionType.DEPOSIT, TransactionType.WITHDRAWAL).contains(this.type)) {
            TransactionCluster cluster = new TransactionCluster(
                new DepositWithdrawalImportedTransaction(
                    usedIds.toString(),
                    date,
                    marketBase,
                    marketBase,
                    type,
                    amountBase,
                    remark,
                    null
                ),
                related
            );
            return cluster;
        } else {
            validateCurrencyPair(marketBase, marketQuote);
            TransactionCluster cluster = new TransactionCluster(
                new BuySellImportedTransactionBean(
                    usedIds.toString(),
                    date,
                    marketBase,
                    marketQuote,
                    type,
                    amountBase.setScale(ParserUtils.DECIMAL_DIGITS, ParserUtils.ROUNDING_MODE),
                    evalUnitPrice(amountQuote, amountBase),
                    remark
                ),
                related
            );
            return cluster;
        }
    }
}
