package io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v4;

import com.univocity.parsers.common.DataValidationException;
import io.everytrade.server.model.TransactionType;
import io.everytrade.server.plugin.impl.everytrade.parser.exception.DataIgnoredException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import io.everytrade.server.model.Currency;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.ExchangeBean;
import lombok.Data;

import static io.everytrade.server.model.TransactionType.BUY;
import static io.everytrade.server.model.TransactionType.DEPOSIT;
import static io.everytrade.server.model.TransactionType.EARNING;
import static io.everytrade.server.model.TransactionType.REBATE;
import static io.everytrade.server.model.TransactionType.REWARD;
import static io.everytrade.server.model.TransactionType.SELL;
import static io.everytrade.server.model.TransactionType.WITHDRAWAL;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v4.BinanceOperationTypeV4.OPERATION_TYPE_BINANCE_CONVERT;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v4.BinanceOperationTypeV4.OPERATION_TYPE_BUY;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v4.BinanceOperationTypeV4.OPERATION_TYPE_CARD_CASHBACK;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v4.BinanceOperationTypeV4.OPERATION_TYPE_COMMISSION_REBATE;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v4.BinanceOperationTypeV4.OPERATION_TYPE_DEPOSIT;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v4.BinanceOperationTypeV4.OPERATION_TYPE_DISTRIBUTION;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v4.BinanceOperationTypeV4.OPERATION_TYPE_FEE;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v4.BinanceOperationTypeV4.OPERATION_TYPE_FIAT_DEPOSIT;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v4.BinanceOperationTypeV4.OPERATION_TYPE_FIAT_WITHDRAWAL;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v4.BinanceOperationTypeV4.OPERATION_TYPE_LARGE_OTC_TRADING;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v4.BinanceOperationTypeV4.OPERATION_TYPE_SELL;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v4.BinanceOperationTypeV4.OPERATION_TYPE_SIMPLE_EARN_FLEXIBLE_INTEREST;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v4.BinanceOperationTypeV4.OPERATION_TYPE_SMALL_ASSETS_EXCHANGE_BNB;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v4.BinanceOperationTypeV4.OPERATION_TYPE_TRANSACTION_RELATED;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v4.BinanceOperationTypeV4.OPERATION_TYPE_WITHDRAWAL;
import static java.math.BigDecimal.ZERO;

@Data
public class BinanceSortedGroupV4 {

    Object time;

    // beforeSum
    Map<Currency, List<BinanceBeanV4>> rowsDeposit = new HashMap<>();
    Map<Currency, List<BinanceBeanV4>> rowsWithdrawal = new HashMap<>();
    Map<Currency, List<BinanceBeanV4>> rowsFees = new HashMap<>();
    Map<Currency, List<BinanceBeanV4>> rowsBuySellRelated = new HashMap<>();
    Map<Currency, List<BinanceBeanV4>> rowsRewards = new HashMap<>();
    Map<Currency, List<BinanceBeanV4>> rowsRebate = new HashMap<>();
    Map<Currency, List<BinanceBeanV4>> rowsEarnings = new HashMap<>();

    // afterSum
    List<BinanceBeanV4> rowDeposit = new ArrayList<>();
    List<BinanceBeanV4> rowWithdrawal = new ArrayList<>();
    List<BinanceBeanV4> rowFees = new ArrayList<>();
    List<BinanceBeanV4> rowBuySellRelated = new ArrayList<>();
    List<BinanceBeanV4> rowReward = new ArrayList<>();
    List<BinanceBeanV4> rowRebate = new ArrayList<>();
    List<BinanceBeanV4> rowEarnings = new ArrayList<>();

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
        rowReward = sumRows(rowsRewards);
        rowRebate = sumRows(rowsRebate);
        rowEarnings = sumRows(rowsEarnings);
    }

    private List<BinanceBeanV4> sumRows(Map<Currency, List<BinanceBeanV4>> rows) {
        List<BinanceBeanV4> result = new ArrayList<>();
        if (rows.size() > 0) {
            var time = rows.values().stream().collect(Collectors.toList()).get(0).get(0).getDate();
            for (Map.Entry<Currency, List<BinanceBeanV4>> entry : rows.entrySet()) {
                if(!entry.getValue().get(0).getOperationType().isMultiRowType) {
                    result.addAll(entry.getValue());
                } else {
                    var newBean = new BinanceBeanV4();
                    var currency = entry.getKey();
                    var change = entry.getValue().stream().map(BinanceBeanV4::getChange).reduce(ZERO, BigDecimal::add);
                    var ids = entry.getValue().stream().map(BinanceBeanV4::getRowId).collect(Collectors.toList());
                    if (entry.getValue().size() > 0) {
                        newBean.setChange(change);
                        newBean.setCoin(currency);
                        newBean.setRowId(entry.getValue().get(0).getRowId());
                        newBean.setOriginalOperation(entry.getValue().get(0).getOriginalOperation());
                        newBean.usedIds.addAll(ids);
                        newBean.setDate(time);
                        result.add(newBean);
                    }
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

    private void validateReward() {
        if (rowReward.size() != 1) {
            throw new DataValidationException("Expected only one \"Reward - distribution\" row");
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
        int rewardNum = rowReward.size();
        int rebateNum = rowsRebate.size();
        int earningsNum = rowsEarnings.size();

        if (buySellNum > 0) {
            validateBuySell();
            createBuySellTxs();
        }

        if (depositNum > 0 || withdrawNum > 0) {
            createDepositWithdrawalTxs();
        }

        if (rewardNum > 0) {
            validateReward();
            createRewarsTxs();
        }

        if (rebateNum > 0) {
            createRebateTxs();
        }

        if (earningsNum > 0) {
            createEarningsTxs();
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
                bean.setFee(fee.getChange().abs());
                bean.setFeeCurrency(fee.getCoin());
                bean.setDate(fee.getDate());
                // bean.setOperation(); // Useless setter and creates error
                bean.setRowId(fee.getRowId());
                bean.setMessage(fee.getMessage());
                txs.feeTransactions.add(bean);
                txs.usedIds.addAll(fee.usedIds);
                txs.setMergedWithAnotherGroup(fee.isMergedWithAnotherGroup());
            }
        } catch (Exception e) {
            throw new DataValidationException("Fee not assigned to transaction;");
        }
    }

    public static String parseIds(List<Integer> ids) {
        String s = "";
        for (int id : ids) {
            s = s + " " + id + ";";
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
            String[] strings = {"Row id " + ids + " " + row.getOriginalOperation()};
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

    private void createRewarsTxs() {
        var txs = new BinanceBeanV4();
        var row = rowReward.get(0);
        txs.setRowNumber(row.getDate().getEpochSecond());
        String[] strings = {"Row id " + row.usedIds.toString() + " " + row.getOriginalOperation()};
        txs.setRowValues(strings);
        txs.usedIds.addAll(row.usedIds);
        txs.setAmountBase(row.getChange().abs());
        txs.setMarketBase(row.getCoin());
        txs.setDate(row.getDate());
        txs.setMergedWithAnotherGroup(row.isMergedWithAnotherGroup());
        txs.setType(REWARD);
        txs.setOriginalOperation(row.getOriginalOperation());
        createdTransactions.add(txs);
    }

    private void createRebateTxs() {
        for (BinanceBeanV4 bean : rowRebate) {
            var txs = new BinanceBeanV4();
            txs.setRowNumber(bean.getDate().getEpochSecond());
            txs.usedIds.add(bean.getRowId());
            txs.setAmountBase(bean.getChange().abs());
            txs.setMarketBase(bean.getCoin());
            txs.setDate(bean.getDate());
            txs.setMergedWithAnotherGroup(bean.isMergedWithAnotherGroup());
            txs.setType(REBATE);
            txs.setOriginalOperation(bean.getOriginalOperation());
            createdTransactions.add(txs);
        }
    }

    private void createEarningsTxs() {
        for (BinanceBeanV4 bean : rowEarnings) {
            var txs = new BinanceBeanV4();
            txs.setRowNumber(bean.getDate().getEpochSecond());
            txs.usedIds.add(bean.getRowId());
            txs.setAmountBase(bean.getChange().abs());
            txs.setMarketBase(bean.getCoin());
            txs.setDate(bean.getDate());
            txs.setMergedWithAnotherGroup(bean.isMergedWithAnotherGroup());
            txs.setType(EARNING);
            txs.setOriginalOperation(bean.getOriginalOperation());
            createdTransactions.add(txs);
        }
    }

    private boolean isConvert(BinanceBeanV4 stRow, BinanceBeanV4 ndRow) {
        return !stRow.getCoin().isFiat() && !ndRow.getCoin().isFiat();
    }
    private boolean isRelatedTransaction(BinanceBeanV4 stRow, BinanceBeanV4 ndRow) {
        return stRow.getOriginalOperation().equalsIgnoreCase(OPERATION_TYPE_TRANSACTION_RELATED.code)
            && ndRow.getOriginalOperation().equalsIgnoreCase(OPERATION_TYPE_TRANSACTION_RELATED.code);
    }

    private TransactionType detectTransactionType(BinanceBeanV4 stRow, BinanceBeanV4 ndRow, boolean convert ) {
        if(convert) {
            return BUY;
        } else {
            var isBuy = rowBuySellRelated.stream().anyMatch(row -> row.getOriginalOperation().equalsIgnoreCase(OPERATION_TYPE_BUY.code));
            var isSell = rowBuySellRelated.stream().anyMatch(row -> row.getOriginalOperation().equalsIgnoreCase(OPERATION_TYPE_SELL.code));
            if(isBuy) {
                return BUY;
            } else if (isSell) {
                return SELL;
            } else if ((stRow.getCoin().isFiat() && stRow.getChange().compareTo(ZERO) > 0) || (ndRow.getCoin().isFiat() && ndRow.getChange().compareTo(ZERO) > 0)) {
                return SELL;
            } else {
                return BUY;
            }
        }
    }

    private void createBuySellTxs() {
        var stRow = rowBuySellRelated.get(0);
        var ndRow = rowBuySellRelated.get(1);
        BinanceBeanV4 baseRow;
        BinanceBeanV4 quoteRow;

        boolean convert = isConvert(stRow,ndRow);
        boolean relatedTransaction = isRelatedTransaction(stRow,ndRow);
        TransactionType type = detectTransactionType(stRow, ndRow, convert);

        if (convert && stRow.getChange().compareTo(ZERO) < 0) {
            baseRow = ndRow;
            quoteRow = stRow;
        } else if (stRow.getCoin().isFiat()) {
            baseRow = ndRow;
            quoteRow = stRow;
        } else {
            baseRow = stRow;
            quoteRow = ndRow;
        }

        var txsBuySell = new BinanceBeanV4();
        txsBuySell.setDate(baseRow.getDate());
        txsBuySell.usedIds.addAll(baseRow.usedIds);
        txsBuySell.usedIds.addAll(quoteRow.usedIds);
        txsBuySell.setMergedWithAnotherGroup(baseRow.isMergedWithAnotherGroup());
        txsBuySell.setRowNumber(baseRow.getDate().getEpochSecond());
        String ids = parseIds(txsBuySell.usedIds);
        String[] strings = {"Row id " + ids, " " + stRow.getOriginalOperation()};
        txsBuySell.setRowValues(strings);
        txsBuySell.setMarketBase(baseRow.getCoin());
        txsBuySell.setAmountBase(baseRow.getChange().abs());
        txsBuySell.setType(type);
        txsBuySell.setRemark(baseRow.getRemark());
        if(relatedTransaction) txsBuySell.setRemark(baseRow.getOriginalOperation().toUpperCase());
        txsBuySell.setMarketQuote(quoteRow.getCoin());
        txsBuySell.setAmountQuote(quoteRow.getChange().abs());
        ExchangeBean.validateCurrencyPair(txsBuySell.getMarketBase(), txsBuySell.getMarketQuote());
        createdTransactions.add(txsBuySell);
    }

    private void addRow(BinanceBeanV4 row) {
        if (row.getOriginalOperation().equals(OPERATION_TYPE_FEE.code)) {
            if (rowsFees.containsKey(row.getCoin())) {
                rowsFees.get(row.getCoin()).add(row);
            } else {
                List<BinanceBeanV4> newList = new ArrayList<>();
                newList.add(row);
                rowsFees.put(row.getCoin(), newList);
            }
        } else if (List.of(OPERATION_TYPE_DEPOSIT.code, OPERATION_TYPE_FIAT_DEPOSIT.code).contains(row.getOriginalOperation())) {
            if (rowsDeposit.containsKey(row.getCoin())) {
                rowsDeposit.get(row.getCoin()).add(row);
            } else {
                List<BinanceBeanV4> newList = new ArrayList<>();
                newList.add(row);
                rowsDeposit.put(row.getCoin(), newList);
            }
        } else if (List.of(OPERATION_TYPE_WITHDRAWAL.code, OPERATION_TYPE_FIAT_WITHDRAWAL.code).contains(row.getOriginalOperation())) {
            if (rowsWithdrawal.containsKey(row.getCoin())) {
                rowsWithdrawal.get(row.getCoin()).add(row);
            } else {
                List<BinanceBeanV4> newList = new ArrayList<>();
                newList.add(row);
                rowsWithdrawal.put(row.getCoin(), newList);
            }
        } else if (
            row.getOriginalOperation().equals(OPERATION_TYPE_BUY.code) ||
                row.getOriginalOperation().equals(OPERATION_TYPE_SELL.code) ||
                row.getOriginalOperation().equals(OPERATION_TYPE_TRANSACTION_RELATED.code) ||
                row.getOriginalOperation().equals(OPERATION_TYPE_LARGE_OTC_TRADING.code) ||
                row.getOriginalOperation().equals(OPERATION_TYPE_SMALL_ASSETS_EXCHANGE_BNB.code) ||
                row.getOriginalOperation().equals(OPERATION_TYPE_BINANCE_CONVERT.code)
        ) {
            if (rowsBuySellRelated.containsKey(row.getCoin())) {
                rowsBuySellRelated.get(row.getCoin()).add(row);
            } else {
                List<BinanceBeanV4> newList = new ArrayList<>();
                newList.add(row);
                rowsBuySellRelated.put(row.getCoin(), newList);
            }
        } else if (row.getOriginalOperation().equals(OPERATION_TYPE_DISTRIBUTION.code)) {
            if (rowsRewards.containsKey(row.getCoin())) {
                rowsRewards.get(row.getCoin()).add(row);
            } else {
                List<BinanceBeanV4> newList = new ArrayList<>();
                newList.add(row);
                rowsRewards.put(row.getCoin(), newList);
            }
        } else if (row.getOriginalOperation().equals(OPERATION_TYPE_CARD_CASHBACK.code)
            || row.getOriginalOperation().equals(OPERATION_TYPE_COMMISSION_REBATE.code)) {
            if (rowsRebate.containsKey(row.getCoin())) {
                rowsRebate.get(row.getCoin()).add(row);
            } else {
                List<BinanceBeanV4> newList = new ArrayList<>();
                newList.add(row);
                rowsRebate.put(row.getCoin(), newList);
            }
        } else if (row.getOriginalOperation().equals(OPERATION_TYPE_SIMPLE_EARN_FLEXIBLE_INTEREST.code)) {
            if (rowsEarnings.containsKey(row.getCoin())) {
                rowsEarnings.get(row.getCoin()).add(row);
            } else {
                List<BinanceBeanV4> newList = new ArrayList<>();
                newList.add(row);
                rowsEarnings.put(row.getCoin(), newList);
            }
        } else {
            throw new DataIgnoredException("Row " + row.getRowId() + " cannot be added due to wrong operation. ");
        }
    }
}
