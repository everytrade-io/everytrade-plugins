package io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v4;

import com.univocity.parsers.common.DataValidationException;
import io.everytrade.server.model.TransactionType;
import io.everytrade.server.plugin.impl.everytrade.parser.exception.DataIgnoredException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import io.everytrade.server.model.Currency;
import lombok.Data;

import static io.everytrade.server.model.TransactionType.BUY;
import static io.everytrade.server.model.TransactionType.DEPOSIT;
import static io.everytrade.server.model.TransactionType.SELL;
import static io.everytrade.server.model.TransactionType.WITHDRAWAL;
import static java.math.BigDecimal.ZERO;

@Data
public class BinanceSortedGroupV4 {

    Instant time;

    // beforeSum
    Map<Currency, List<BinanceBeanV4>> rowsDeposit = new HashMap<>();
    Map<Currency, List<BinanceBeanV4>> rowsWithdrawal = new HashMap<>();
    Map<Currency, List<BinanceBeanV4>> rowsFees = new HashMap<>();
    Map<Currency, List<BinanceBeanV4>> rowsBuySellRelated = new HashMap<>();

    // afterSum
    List<BinanceBeanV4> rowDeposit = new ArrayList<>();
    List<BinanceBeanV4> rowWithdrawal = new ArrayList<>();
    List<BinanceBeanV4> rowFees = new ArrayList<>();
    List<BinanceBeanV4> rowBuySellRelated = new ArrayList<>();

    public List<BinanceBeanV4> createdTransactions = new ArrayList<>();

    public void sortGroup(List<BinanceBeanV4> group) {
        // nejdrive udelam map , kde hash bude currency
        for (BinanceBeanV4 row : group) {
            addRow(row);
        }
        sumAllRows();
        createTransactions();
    }

    private void sumAllRows() {
        rowDeposit = sumRows(rowsDeposit);
        rowWithdrawal = sumRows(rowsWithdrawal);
        rowFees = sumRows(rowsFees);
        rowBuySellRelated = sumRows(rowsBuySellRelated);
    }

    private List<BinanceBeanV4> sumRows(Map<Currency, List<BinanceBeanV4>> rows) {
        List<BinanceBeanV4> result = new ArrayList<>();
        if (rows.size() > 0) {
            var time = rows.values().stream().collect(Collectors.toList()).get(0).get(0).getDate();
            for (Map.Entry<Currency, List<BinanceBeanV4>> entry : rows.entrySet()) {
                var newBean = new BinanceBeanV4();
                var currency = entry.getKey();
                var change = entry.getValue().stream().map(BinanceBeanV4::getChange).reduce(ZERO, BigDecimal::add);
                var ids = entry.getValue().stream().map(BinanceBeanV4::getRowId).collect(Collectors.toList());
                if (entry.getValue().size() > 0) {
                    newBean.setChange(change);
                    newBean.setCoin(currency);
                    newBean.usedIds.addAll(ids);
                    newBean.setDate(time);
                    result.add(newBean);
                }
            }
        }
        return result;
    }

    private void validateBuySell() {
        if (rowBuySellRelated.size() != 2) {
            throw new DataValidationException("Wrong number of currencies");
        }
        // one of them must be plus and second minus
        var stValue = rowBuySellRelated.get(0).getChange();
        var ndValue = rowBuySellRelated.get(1).getChange();
        var minus = stValue.multiply(ndValue); // must be everytime minus
        if (minus.compareTo(ZERO) > 0) {
            throw new DataValidationException("Wrong change value");
        }
    }

    private void validateFee() {
        if (rowFees.size() > 0 && !(rowBuySellRelated.size() > 0 || rowDeposit.size() > 0 || rowWithdrawal.size() > 0)) {
            throw new DataValidationException("No txs for fee");
        }
    }

    private boolean isCrypto(List<BinanceBeanV4> rows) {
        boolean result = false;
        for (BinanceBeanV4 row : rows) {
            if (!row.getCoin().isFiat()) {
                result = true;
            }
        }
        return result;
    }

    public void createTransactions() {
        int buySellNum = rowBuySellRelated.size();
        int depositNum = rowDeposit.size();
        int withdrawNum = rowWithdrawal.size();
        int feeNum = rowFees.size();

        if (buySellNum > 0) {
            validateBuySell();
            createBuySellTxs();
        }

        if (depositNum > 0 || withdrawNum > 0) {
            createDepositWithdrawalTxs();
        }

        if (feeNum > 0) {
            validateFee();
            addFeeToTxs();
        }
        createdTransactions.stream().forEach(r -> {
            r.setInTransaction(true);
        });
    }

    private void addFeeToTxs() {
        try {
            var txs = createdTransactions.get(0);
            for (BinanceBeanV4 fee : rowFees) {
                fee.setInTransaction(true);
                var bean = new BinanceBeanV4();
                bean.setType(TransactionType.FEE);
                bean.setFee(fee.getChange());
                bean.setFeeCurrency(fee.getCoin());
                bean.setDate(fee.getDate());
                bean.setOperation(fee.getOperation());
                bean.setRowId(fee.getRowId());
                bean.setMessage(fee.getMessage());
                txs.feeTransactions.add(bean);
                txs.usedIds.addAll(fee.usedIds);
                txs.setMergedWithAnotherGroup(fee.isMergedWithAnotherGroup());
            }
        } catch (Exception e) {
            throw new DataValidationException("Fee not assigned to transaction");
        }
    }

    public static String parseIds(List<Integer> ids) {
        String s = "";
        for (int id : ids) {
            s = s + " " + id + "; ";
        }
        return s;
    }

    private void createDepositWithdrawalTxs() {
        List<BinanceBeanV4> mergeDepositWithdrawal = new ArrayList<>();
        mergeDepositWithdrawal.addAll(rowDeposit);
        mergeDepositWithdrawal.addAll(rowWithdrawal);
        var idsList = mergeDepositWithdrawal.stream().map(BinanceBeanV4::getRowId).collect(Collectors.toList());
        var ids = parseIds(idsList);
        for (BinanceBeanV4 row : mergeDepositWithdrawal) {
            var txs = new BinanceBeanV4();
            txs.setRowNumber(row.getDate().getEpochSecond());
            String[] strings = {"Row id " + ids + " " + row.getOperation()};
            txs.setRowValues(strings);
            txs.usedIds.addAll(row.usedIds);
            txs.setAmountBase(row.getChange().abs());
            txs.setMarketBase(row.getCoin());
            txs.setDate(row.getDate());
            txs.setMergedWithAnotherGroup(row.isMergedWithAnotherGroup());
            if (row.getChange().compareTo(ZERO) > 0) {
                txs.setType(DEPOSIT);
            } else {
                txs.setType(WITHDRAWAL);
            }
            createdTransactions.add(txs);
        }
    }

    private void createBuySellTxs() {
        var stRow = rowBuySellRelated.get(0);
        var ndRow = rowBuySellRelated.get(1);
        if(stRow.getChange().compareTo(ZERO) < 0
            && ndRow.getChange().compareTo(ZERO) > 0) {
            stRow =  rowBuySellRelated.get(1);
            ndRow = rowBuySellRelated.get(0);
        }
        var txsBuySell = new BinanceBeanV4();
        txsBuySell.setDate(stRow.getDate());
        txsBuySell.usedIds.addAll(stRow.usedIds);
        txsBuySell.usedIds.addAll(ndRow.usedIds);
        txsBuySell.setMergedWithAnotherGroup(stRow.isMergedWithAnotherGroup());
        txsBuySell.setRowNumber(stRow.getDate().getEpochSecond());
        String ids = parseIds(txsBuySell.usedIds);
        String[] strings = {"Row id " + ids, " " + stRow.getOperation()};
        txsBuySell.setRowValues(strings);
        if (isCrypto(rowBuySellRelated)) {
            if (!stRow.getCoin().isFiat()) {
                txsBuySell.setMarketBase(stRow.getCoin());
                txsBuySell.setAmountBase(stRow.getChange().abs());
                if (stRow.getChange().compareTo(ZERO) < 0) {
                    txsBuySell.setType(SELL);
                } else {
                    txsBuySell.setType(BUY);
                }
                txsBuySell.setMarketQuote(ndRow.getCoin());
                txsBuySell.setAmountQuote(ndRow.getChange().abs());
            } else {
                txsBuySell.setMarketBase(ndRow.getCoin());
                txsBuySell.setAmountBase(ndRow.getChange().abs());
                if (ndRow.getChange().compareTo(ZERO) < 0) {
                    txsBuySell.setType(SELL);
                } else {
                    txsBuySell.setType(BUY);
                }
                txsBuySell.setMarketQuote(stRow.getCoin());
                txsBuySell.setAmountQuote(stRow.getChange().abs());
            }
        } else {
            if (stRow.getChange().compareTo(ZERO) > 0) {
                txsBuySell.setMarketBase(stRow.getCoin());
                txsBuySell.setAmountBase(stRow.getChange().abs());
                txsBuySell.setType(BUY);
                txsBuySell.setMarketQuote(ndRow.getCoin());
                txsBuySell.setAmountQuote(ndRow.getChange());
            } else {
                txsBuySell.setMarketBase(ndRow.getCoin());
                txsBuySell.setAmountBase(ndRow.getChange().abs());
                txsBuySell.setType(BUY);
                txsBuySell.setMarketQuote(stRow.getCoin());
                txsBuySell.setAmountQuote(stRow.getChange());
            }
        }
        txsBuySell.checkCurrencyPairForRows();
        createdTransactions.add(txsBuySell);
    }

    private void addRow(BinanceBeanV4 row) {
        if (row.getOperation().equals(BinanceConstantsV4.OPERATION_TYPE_FEE.code)) {
            if (rowsFees.containsKey(row.getCoin())) {
                rowsFees.get(row.getCoin()).add(row);
            } else {
                List<BinanceBeanV4> newList = new ArrayList<>();
                newList.add(row);
                rowsFees.put(row.getCoin(), newList);
            }
        } else if (row.getOperation().equals(BinanceConstantsV4.OPERATION_TYPE_DEPOSIT.code)) {
            if (rowsDeposit.containsKey(row.getCoin())) {
                rowsDeposit.get(row.getCoin()).add(row);
            } else {
                List<BinanceBeanV4> newList = new ArrayList<>();
                newList.add(row);
                rowsDeposit.put(row.getCoin(), newList);
            }
        } else if (row.getOperation().equals(BinanceConstantsV4.OPERATION_TYPE_WITHDRAWAL.code)) {
            if (rowsWithdrawal.containsKey(row.getCoin())) {
                rowsWithdrawal.get(row.getCoin()).add(row);
            } else {
                List<BinanceBeanV4> newList = new ArrayList<>();
                newList.add(row);
                rowsWithdrawal.put(row.getCoin(), newList);
            }
        } else if (
            row.getOperation().equals(BinanceConstantsV4.OPERATION_TYPE_BUY.code) ||
                row.getOperation().equals(BinanceConstantsV4.OPERATION_TYPE_SELL.code) ||
                row.getOperation().equals(BinanceConstantsV4.OPERATION_TYPE_TRANSACTION_RELATED.code)
        ) {
            if (rowsBuySellRelated.containsKey(row.getCoin())) {
                rowsBuySellRelated.get(row.getCoin()).add(row);
            } else {
                List<BinanceBeanV4> newList = new ArrayList<>();
                newList.add(row);
                rowsBuySellRelated.put(row.getCoin(), newList);
            }
        } else {
            throw new DataIgnoredException("Row " + row.getRowId() + "cannot be added due to wrong operation. ");
        }
    }
}
