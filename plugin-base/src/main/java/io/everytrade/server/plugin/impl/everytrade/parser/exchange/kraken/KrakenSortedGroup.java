package io.everytrade.server.plugin.impl.everytrade.parser.exchange.kraken;

import com.univocity.parsers.common.DataValidationException;
import io.everytrade.server.model.TransactionType;
import io.everytrade.server.plugin.impl.everytrade.parser.exception.DataIgnoredException;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.ExchangeBean;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean.KrakenBeanV2;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

import static io.everytrade.server.model.TransactionType.BUY;
import static io.everytrade.server.model.TransactionType.SELL;
import static io.everytrade.server.model.TransactionType.DEPOSIT;
import static io.everytrade.server.model.TransactionType.WITHDRAWAL;
import static io.everytrade.server.model.TransactionType.UNKNOWN;
import static java.math.BigDecimal.ZERO;

@Data
public class KrakenSortedGroup {

    Object refId;
    List<KrakenBeanV2> rowsDeposit = new ArrayList<>();
    List<KrakenBeanV2> rowsWithdrawal = new ArrayList<>();
    List<KrakenBeanV2> rowsTrades = new ArrayList<>();

    // buy/sell
    KrakenBeanV2 rowBase;
    KrakenBeanV2 rowQuote;

    public KrakenBeanV2 createdTransaction = new KrakenBeanV2();

    public void sortGroup(List<KrakenBeanV2> group) {
        // nejdrive udelam map , kde hash bude currency
        for (KrakenBeanV2 row : group) {
            addRow(row);
        }
        var transactionType = findTransactionType();
        createTransactions(transactionType);
    }

    private TransactionType findTransactionType() {
        // buy or sell
        if (rowsTrades.size() == 2 && rowsDeposit.size() == 0 && rowsWithdrawal.size() == 0) {
            var stTradeRow = rowsTrades.get(0);
            var ndTradeRow = rowsTrades.get(1);
            // Buy
            if ((stTradeRow.getFee().compareTo(ZERO) == 0 && stTradeRow.getAmount().compareTo(ZERO) > 0)
                || (ndTradeRow.getFee().compareTo(ZERO) == 0 && ndTradeRow.getAmount().compareTo(ZERO) > 0)) {
                return BUY;
            }
            // Sell
            if ((stTradeRow.getFee().compareTo(ZERO) == 0 && stTradeRow.getAmount().compareTo(ZERO) < 0)
                || (ndTradeRow.getFee().compareTo(ZERO) == 0 && ndTradeRow.getAmount().compareTo(ZERO) < 0)) {
                return SELL;
            }
        }
        // Deposit
        if (rowsTrades.size() == 0 && rowsDeposit.size() == 1 && rowsWithdrawal.size() == 0) {
            return DEPOSIT;
        }
        // Withdrawal
        if (rowsTrades.size() == 0 && rowsDeposit.size() == 0 && rowsWithdrawal.size() == 1) {
            return WITHDRAWAL;
        }
        return UNKNOWN;
    }

    private void validateBuySell() {
        if (rowBase.getAsset().equals(rowQuote.getAsset())) {
            throw new DataValidationException("Wrong number of currencies");
        }
        // one of them must be plus and second minus
        if ((rowBase.getAmount().compareTo(ZERO) > 0 && rowQuote.getAmount().compareTo(ZERO) > 0)
            || (rowBase.getAmount().compareTo(ZERO) < 0 && rowQuote.getAmount().compareTo(ZERO) < 0)) {
            throw new DataValidationException("Wrong amount value");
        }
        ExchangeBean.validateCurrencyPair(rowBase.getAsset(), rowQuote.getAsset());
    }

    private void validateDepositWithdrawal(TransactionType type) {
        if (type.equals(DEPOSIT)) {
            if (rowsDeposit.size() != 1) {
                throw new DataValidationException("Wrong deposit data;");
            }
            var row = rowsDeposit.get(0);
            if (row.getAmount().compareTo(ZERO) < 0) {
                throw new DataValidationException("Incorrect deposit amount value;");
            }
        }
        if (type.equals(WITHDRAWAL)) {
            if (rowsWithdrawal.size() != 1) {
                throw new DataValidationException("Wrong withdrawal data;");
            }
            var row = rowsWithdrawal.get(0);
            if (row.getAmount().compareTo(ZERO) > 0) {
                throw new DataValidationException("Incorrect withdrawal amount value;");
            }
        }
    }

    public void createTransactions(TransactionType type) {
        if (type.isBuyOrSell()) {
            // set base and quote row
            if (rowsTrades.get(0).getFee().compareTo(ZERO) == 0
                && rowsTrades.get(1).getFee().compareTo(ZERO) != 0) {
                rowBase = rowsTrades.get(0);
                rowQuote = rowsTrades.get(1);
            } else {
                rowQuote = rowsTrades.get(0);
                rowBase = rowsTrades.get(1);
            }
            validateBuySell();
            createBuySellTxs(type);
        }
        if (type.isDepositOrWithdrawal()) {
            validateDepositWithdrawal(type);
            createDepositWithdrawalTxs(type);
        }
    }

    public static String parseIds(List<Integer> ids) {
        String s = "";
        for (int id : ids) {
            s = s + " " + id + "; ";
        }
        return s;
    }

    private void createDepositWithdrawalTxs(TransactionType type) {
        if (!rowsDeposit.isEmpty()) {
            mapDepositWithdrawalTxs(rowsDeposit.get(0), type);
        } else {
            mapDepositWithdrawalTxs(rowsWithdrawal.get(0), type);
        }
    }

    private void mapDepositWithdrawalTxs(KrakenBeanV2 row, TransactionType type) {
        createdTransaction.setTxsType(type);
        createdTransaction.setRefid(row.getRefid());
        createdTransaction.setRowId(row.getRowId());
        createdTransaction.setAsset(row.getAsset());
        createdTransaction.setAmount(row.getAmount());
        createdTransaction.setFeeAmount(row.getFee());
        createdTransaction.setFeeCurrency(row.getAsset());
        createdTransaction.setRowNumber(row.getTime().getEpochSecond());
        createdTransaction.setTxid(row.getTxid());
        createdTransaction.usedIds.add(row.getRowId());
        createdTransaction.setTime(row.getTime());
        createdTransaction.setRowValues();
    }

    private void createBuySellTxs(TransactionType type) {
        // mapping;
        createdTransaction.setMarketBase(rowBase.getAsset());
        createdTransaction.setMarketQuote(rowQuote.getAsset());
        createdTransaction.setAmountBase(rowBase.getAmount());
        createdTransaction.setAmountQuote(rowQuote.getAmount());
        createdTransaction.setFeeCurrency(rowQuote.getAsset());
        createdTransaction.setFeeAmount(rowQuote.getFee());
        createdTransaction.setTime(rowBase.getTime());
        createdTransaction.setRowNumber(rowBase.getTime().getEpochSecond());
        createdTransaction.setTxsType(type);
        createdTransaction.setTxid(rowBase.getTxid() + " " + rowQuote.getTxid());
        createdTransaction.usedIds.add(rowBase.getRowId());
        createdTransaction.usedIds.add(rowQuote.getRowId());
        createdTransaction.setRowValues();
    }

    private void addRow(KrakenBeanV2 row) {
        if (row.getType().equals(KrakenConstants.TYPE_TRADE.code)) {
            rowsTrades.add(row);
        } else if (row.getType().equals(KrakenConstants.TYPE_DEPOSIT.code)) {
            rowsDeposit.add(row);
        } else if (row.getType().equals(KrakenConstants.TYPE_WITHDRAWAL.code)) {
            rowsWithdrawal.add(row);
        } else {
            throw new DataIgnoredException("Row " + row.getRowId() + "cannot be added due to wrong operation;");
        }
    }
}
