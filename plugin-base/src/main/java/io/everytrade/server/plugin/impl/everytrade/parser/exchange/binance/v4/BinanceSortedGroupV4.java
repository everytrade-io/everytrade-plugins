package io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v4;

import com.univocity.parsers.common.DataValidationException;
import io.everytrade.server.model.Currency;
import io.everytrade.server.model.TransactionType;
import io.everytrade.server.plugin.impl.everytrade.parser.exception.DataIgnoredException;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.ExchangeBean;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.binanceExceptions.BinanceValidateException;
import io.everytrade.server.plugin.impl.everytrade.rateprovider.CoinPaprikaRateProvider;
import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static io.everytrade.server.model.Currency.BNB;
import static io.everytrade.server.model.Currency.USD;
import static io.everytrade.server.model.Currency.USDT;
import static io.everytrade.server.model.TransactionType.BUY;
import static io.everytrade.server.model.TransactionType.DEPOSIT;
import static io.everytrade.server.model.TransactionType.EARNING;
import static io.everytrade.server.model.TransactionType.FEE;
import static io.everytrade.server.model.TransactionType.REBATE;
import static io.everytrade.server.model.TransactionType.REWARD;
import static io.everytrade.server.model.TransactionType.SELL;
import static io.everytrade.server.model.TransactionType.STAKE;
import static io.everytrade.server.model.TransactionType.UNSTAKE;
import static io.everytrade.server.model.TransactionType.WITHDRAWAL;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v4.BinanceBeanV4.BINANCE_CARD_SPENDING_CRYPTO;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v4.BinanceBeanV4.BINANCE_CARD_SPENDING_FIAT;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v4.BinanceOperationTypeV4.OPERATION_TYPE_BINANCE_CONVERT;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v4.BinanceOperationTypeV4.OPERATION_TYPE_BNB_VAULT_REWARDS;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v4.BinanceOperationTypeV4.OPERATION_TYPE_BUY;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v4.BinanceOperationTypeV4.OPERATION_TYPE_BUY_CRYPTO;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v4.BinanceOperationTypeV4.OPERATION_TYPE_C2C_TRANSFER;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v4.BinanceOperationTypeV4.OPERATION_TYPE_CARD_CASHBACK;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v4.BinanceOperationTypeV4.OPERATION_TYPE_CASHBACK_VOUCHER;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v4.BinanceOperationTypeV4.OPERATION_TYPE_COMMISSION_REBATE;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v4.BinanceOperationTypeV4.OPERATION_TYPE_CONVERT_FIAT_TO_CRYPTO_OCBS;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v4.BinanceOperationTypeV4.OPERATION_TYPE_DEPOSIT;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v4.BinanceOperationTypeV4.OPERATION_TYPE_DISTRIBUTION;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v4.BinanceOperationTypeV4.OPERATION_TYPE_ETH2_0_STAKING;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v4.BinanceOperationTypeV4.OPERATION_TYPE_FEE;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v4.BinanceOperationTypeV4.OPERATION_TYPE_FIAT_DEPOSIT;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v4.BinanceOperationTypeV4.OPERATION_TYPE_FIAT_WITHDRAW;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v4.BinanceOperationTypeV4.OPERATION_TYPE_FIAT_WITHDRAWAL;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v4.BinanceOperationTypeV4.OPERATION_TYPE_LARGE_OTC_TRADING;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v4.BinanceOperationTypeV4.OPERATION_TYPE_SAVING_DISTRIBUTION;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v4.BinanceOperationTypeV4.OPERATION_TYPE_SELL;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v4.BinanceOperationTypeV4.OPERATION_TYPE_SIMPLE_EARN_FLEXIBLE_INTEREST;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v4.BinanceOperationTypeV4.OPERATION_TYPE_SIMPLE_EARN_FLEXIBLE_REDEMPTION;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v4.BinanceOperationTypeV4.OPERATION_TYPE_SIMPLE_EARN_FLEXIBLE_SUBSCRIPTION;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v4.BinanceOperationTypeV4.OPERATION_TYPE_SIMPLE_EARN_LOCKED_REDEMPTION;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v4.BinanceOperationTypeV4.OPERATION_TYPE_SIMPLE_EARN_LOCKED_REWARDS;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v4.BinanceOperationTypeV4.OPERATION_TYPE_SMALL_ASSETS_EXCHANGE_BNB;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v4.BinanceOperationTypeV4.OPERATION_TYPE_STAKING_PURCHASE;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v4.BinanceOperationTypeV4.OPERATION_TYPE_STAKING_REDEMPTION;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v4.BinanceOperationTypeV4.OPERATION_TYPE_STAKING_REWARDS;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v4.BinanceOperationTypeV4.OPERATION_TYPE_TRANSACTION_BUY;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v4.BinanceOperationTypeV4.OPERATION_TYPE_TRANSACTION_FEE;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v4.BinanceOperationTypeV4.OPERATION_TYPE_TRANSACTION_RELATED;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v4.BinanceOperationTypeV4.OPERATION_TYPE_TRANSACTION_REVENUE;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v4.BinanceOperationTypeV4.OPERATION_TYPE_TRANSACTION_SOLD;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v4.BinanceOperationTypeV4.OPERATION_TYPE_TRANSACTION_SPEND;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v4.BinanceOperationTypeV4.OPERATION_TYPE_WITHDRAWAL;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v4.BinanceOperationTypeV4.SIMPLE_EARN_LOCKED_REDEMPTION_BINANCE_EARN;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v4.BinanceSupportedOperations.CREATE_ONE_ROW_TRANSACTION_WHEN_EXCEPTION;
import static java.math.BigDecimal.ZERO;

@Data
public class BinanceSortedGroupV4 {

    Object time;
    public static final Currency ACCOUNT_CURRENCY = USDT;

    // beforeSum
    Map<Currency, List<BinanceBeanV4>> rowsDeposit = new HashMap<>();
    Map<Currency, List<BinanceBeanV4>> rowsWithdrawal = new HashMap<>();
    Map<Currency, List<BinanceBeanV4>> rowsFees = new HashMap<>();
    Map<Currency, List<BinanceBeanV4>> rowsBuySellRelated = new HashMap<>();
    Map<Currency, List<BinanceBeanV4>> rowsRewards = new HashMap<>();
    Map<Currency, List<BinanceBeanV4>> rowsRebate = new HashMap<>();
    Map<Currency, List<BinanceBeanV4>> rowsEarnings = new HashMap<>();
    Map<Currency, List<BinanceBeanV4>> rowsStakings = new HashMap<>();
    Map<Currency, List<BinanceBeanV4>> rowsStakingETH2_0 = new HashMap<>();

    // afterSum
    List<BinanceBeanV4> rowDeposit = new ArrayList<>();
    List<BinanceBeanV4> rowWithdrawal = new ArrayList<>();
    List<BinanceBeanV4> rowFees = new ArrayList<>();
    List<BinanceBeanV4> rowBuySellRelated = new ArrayList<>();
    List<BinanceBeanV4> rowReward = new ArrayList<>();
    List<BinanceBeanV4> rowRebate = new ArrayList<>();
    List<BinanceBeanV4> rowEarnings = new ArrayList<>();
    List<BinanceBeanV4> rowStakings = new ArrayList<>();
    List<BinanceBeanV4> smallAssetExchange = new ArrayList<>();
    List<BinanceBeanV4> rowStakingETH2_0 = new ArrayList<>();

    public List<BinanceBeanV4> createdTransactions = new ArrayList<>();

    public void sortGroup(List<BinanceBeanV4> group) {
        try {
            int groupSize = group.size();
            for (BinanceBeanV4 row : group) {
                addRow(row, groupSize);
            }
            sumAllRows();
            createTransactionsFromMultiRowData();
        } catch (BinanceValidateException e) {
            throw new DataValidationException(e.getMessage());
        }catch (Exception ignore) {
            if (group.get(0).isMergedWithAnotherGroup()) {
                throw new DataValidationException(ignore.getMessage());
            } else {
                int i = 0;
                for (BinanceBeanV4 row : group) {
                    if (!CREATE_ONE_ROW_TRANSACTION_WHEN_EXCEPTION.contains(row.getOperationType())) {
                        i++;
                    }
                }
                if (group.size() == i) {
                    createTransactionFromOneRowData(group);
                } else {
                    throw new DataValidationException(ignore.getMessage());
                }
            }
        }
    }

    private void sumAllRows() {
        rowDeposit = sumRows(rowsDeposit);
        rowWithdrawal = sumRows(rowsWithdrawal);
        rowFees = sumRows(rowsFees);
        rowBuySellRelated = sumRows(rowsBuySellRelated);
        rowReward = sumRows(rowsRewards);
        rowRebate = sumRows(rowsRebate);
        rowEarnings = sumRows(rowsEarnings);
        rowStakings = sumRows(rowsStakings);
        rowStakingETH2_0 = sumRows(rowsStakingETH2_0);
    }

    private List<BinanceBeanV4> sumRows(Map<Currency, List<BinanceBeanV4>> rows) {
        List<BinanceBeanV4> result = new ArrayList<>();
        if (!rows.isEmpty()) {
            var time = rows.values().stream()
                .flatMap(List::stream)
                .map(BinanceBeanV4::getDate)
                .filter(Objects::nonNull)
                .findFirst();
            for (Map.Entry<Currency, List<BinanceBeanV4>> entry : rows.entrySet()) {
                if(!entry.getValue().get(0).getOperationType().isMultiRowType) {
                    entry.getValue().get(0).usedIds.add(entry.getValue().get(0).getRowId());
                    result.addAll(entry.getValue());
                } else {
                    var newBean = new BinanceBeanV4();
                    var currency = entry.getKey();
                    var change = entry.getValue().stream().map(BinanceBeanV4::getChange).reduce(ZERO, BigDecimal::add);
                    if(change.compareTo(ZERO) == 0 && entry.getValue().get(0).isMergedWithAnotherGroup()) {
                        throw new DataValidationException(String.format("Rows with currency %s cannot be sum.", currency.code()));
                    }
                    var ids = entry.getValue().stream().map(BinanceBeanV4::getRowId).collect(Collectors.toList());
                    if (entry.getValue().size() > 0) {
                        newBean.setChange(change);
                        newBean.setCoin(currency);
                        newBean.setRowId(entry.getValue().get(0).getRowId());
                        newBean.setOriginalOperation(entry.getValue().get(0).getOriginalOperation());
                        newBean.usedIds.addAll(ids);
                        newBean.setDate(time.orElse(null));
                        result.add(newBean);
                    }
                }

            }
        }
        return result;
    }

    private void validateBuySell() {
        if (rowBuySellRelated.stream().map(BinanceBeanV4::getOperationType)
                .anyMatch(row -> row.equals(OPERATION_TYPE_TRANSACTION_SPEND) ||
                    row.equals(OPERATION_TYPE_TRANSACTION_REVENUE)) && rowBuySellRelated.size() != 2) {
            throw new BinanceValidateException("Wrong number of currencies");
        } else
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

    private void validateStakingETH2_0() {
        boolean containsETH = rowStakingETH2_0.stream()
            .anyMatch(row -> row.getCoin().equals(Currency.ETH));

        if (!containsETH) {
            throw new BinanceValidateException("No transaction contains ETH as a currency");
        }

        boolean hasPositive = rowStakingETH2_0.stream().anyMatch(row -> row.getChange().compareTo(BigDecimal.ZERO) > 0);
        boolean hasNegative = rowStakingETH2_0.stream().anyMatch(row -> row.getChange().compareTo(BigDecimal.ZERO) < 0);

        if (!(hasPositive && hasNegative)) {
            throw new BinanceValidateException("Transactions do not have both positive and negative amounts");
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

    private void validateSmallAssetExchangeTx() {
        if (smallAssetExchange.size() % 2 != 0) {
            throw new DataValidationException("Expected even-numbered \"Small asset exchange\" group of rows");
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

    private List<BinanceBeanV4> createFee(List<BinanceBeanV4> group) {
        List<BinanceBeanV4> result = new ArrayList<>();
        for(BinanceBeanV4 row : group) {
            if (row.getOperationType().equals(OPERATION_TYPE_FEE) || row.getOperationType().equals(OPERATION_TYPE_TRANSACTION_FEE)) {
                row.setInTransaction(true);
                row.setType(FEE);
                row.setFee(row.getChange().abs());
                row.setFeeCurrency(row.getCoin());
                row.feeTransactions.add(row);
                row.setMarketBase(row.getCoin());
                row.setAmountBase(row.getChange().abs());
                result.add(row);
            }
        }
        return result;
    }

    public void createTransactionFromOneRowData(List<BinanceBeanV4> group) {
        List<BinanceBeanV4> fees = createFee(group);
        for (BinanceBeanV4 row : group) {
            if (row.getOperationType().equals(OPERATION_TYPE_FEE) || row.getOperationType().equals(OPERATION_TYPE_TRANSACTION_FEE)) {
                // ignore;
            } else if (OPERATION_TYPE_SELL.equals(row.getOperationType()) || OPERATION_TYPE_BUY.equals(row.getOperationType())) {
                if (row.getCoin().isFiat()) {
                    if (row.getChange().compareTo(ZERO) >= 0) {
                        row.setType(DEPOSIT);
                    } else {
                        row.setType(WITHDRAWAL);
                    }
                } else {
                    if (row.getChange().compareTo(ZERO) >= 0) {
                        row.setType(BUY);
                        row.setMarketQuote(ACCOUNT_CURRENCY);
                    } else {
                        row.setMarketQuote(ACCOUNT_CURRENCY);
                        row.setType(SELL);
                    }
                }
                row.setAmountBase(row.getChange().abs());
                row.setMarketBase(row.getCoin());
                this.createdTransactions.add(row);
            }
        }
        if (createdTransactions.size() > 0) {
            createdTransactions.get(0).setFeeTransactions(fees);
        } else {
            createdTransactions.addAll(fees);
        }
    }


    public void createTransactionsFromMultiRowData() {
        int buySellNum = rowBuySellRelated.size();
        int depositNum = rowDeposit.size();
        int withdrawNum = rowWithdrawal.size();
        int feeNum = rowFees.size();
        int rewardNum = rowReward.size();
        int rebateNum = rowsRebate.size();
        int earningsNum = rowsEarnings.size();
        int stakingsNum = rowsStakings.size();
        int smallAssetExchangeNum = smallAssetExchange.size();
        int stakingETH2_0Num = rowsStakingETH2_0.size();

        if (buySellNum > 0) {
            validateBuySell();
            createBuySellTxs();
        }

        if (stakingETH2_0Num > 0) {
            validateStakingETH2_0();
            createStakingETH2_0Txs();
        }

        if (depositNum > 0 || withdrawNum > 0) {
            createDepositWithdrawalTxs();
        }

        if (rewardNum > 0) {
            validateReward();
            createRewardsTxs();
        }

        if (rebateNum > 0) {
            createRebateTxs();
        }

        if (earningsNum > 0) {
            createEarningsTxs();
        }

        if (stakingsNum > 0) {
            createStakingsTxs();
        }

        if (feeNum > 0) {
            validateFee();
            addFeeToTxs();
        }

        if (smallAssetExchangeNum > 0) {
            var listOfRows = smallAssetExchange;
            var sortedListByRowId = listOfRows.stream().sorted(Comparator.comparingInt(BinanceBeanV4::getRowId))
                .collect(Collectors.toList());
            try {
                validateSmallAssetExchangeTx();
                createSmallAssetExchangePairTx(sortedListByRowId);
            } catch (DataValidationException e) {
                createSmallAssetExchangeSingleTx(sortedListByRowId);
            }
            addFeeToTxs();
        }
        createdTransactions.stream().forEach(r -> {
            r.setInTransaction(true);
        });
    }
    private void smallAssetExchangeValidateCurrencyPair(BinanceBeanV4 baseRow, BinanceBeanV4 quoteRow) {
        if(!baseRow.getCoin().equals(BNB) && !quoteRow.getCoin().equals(BNB)) {
            throw new DataValidationException("Base or quote must be BNB currency");
        } else if (baseRow.getCoin().equals(BNB) && quoteRow.getCoin().equals(BNB)) {
            throw new DataValidationException("Base or quote cannot be BNB currency");
        }
    }

    private void createSmallAssetExchangePairTx(List<BinanceBeanV4> rows) {
        // create transaction by two rows
        List<BinanceBeanV4> result = new ArrayList<>();
        for(int i = 0; i < rows.size(); i = i + 2) {
            List<BinanceBeanV4> list = new ArrayList<>();
            BinanceBeanV4 baseRow = smallAssetExchange.get(i);
            list.add(baseRow);
            BinanceBeanV4 quoteRow = smallAssetExchange.get(i + 1);
            list.add(quoteRow);
            smallAssetExchangeValidateCurrencyPair(baseRow,quoteRow);
            result.add(createSmallAssetExchangeBuySellTx(list));
        }
        try {
            // validation buy transaction cluster;
            for(BinanceBeanV4 bean : result) {
                bean.toTransactionCluster();
            }
        } catch (Exception ex) {
            throw new DataValidationException("Use one row tx way");
        }
        createdTransactions.addAll(result);
    }

    //
    private void createSmallAssetExchangeSingleTx(List<BinanceBeanV4> rows) {
        for (BinanceBeanV4 row : rows) {
            if (row.getCoin().isFiat()) { // deposit or withdrawal
                if (row.getChange().compareTo(ZERO) >= 0) { //DEPOSIT
                    row.setType(DEPOSIT);
                } else {                        // WITHDRAWAL
                    row.setType(WITHDRAWAL);
                }
            } else { // buy or sell
                if (row.getChange().compareTo(ZERO) >= 0) { // BUY
                    row.setType(BUY);
                } else {
                    row.setType(SELL);
                }
                row.setMarketQuote(ACCOUNT_CURRENCY);
                row.setAmountQuote(null);
            }
            row.setMarketBase(row.getCoin());
            row.setAmountBase(row.getChange().abs());
            createdTransactions.add(row);
        }
    }

    private void addFeeToTxs() {
        try {
            var txs = createdTransactions.get(0);
            for (BinanceBeanV4 fee : rowFees) {
                fee.setInTransaction(true);
                var bean = new BinanceBeanV4();
                bean.setType(FEE);
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
            var txs = row;
            txs.setRowNumber(row.getDate().getEpochSecond());
            String[] strings = {"Row id " + ids + " " + row.getOriginalOperation()};
            txs.setRowValues(strings);
            txs.setAmountBase(row.getChange().abs());
            txs.setMarketBase(row.getCoin());
            if (row.getChange().compareTo(ZERO) > 0) {
                txs.setType(DEPOSIT);
            } else {
                txs.setType(WITHDRAWAL);
            }
            createdTransactions.add(txs);
        }
    }

    private void createRewardsTxs() {
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
        txs.setNote(row.getNote());
        txs.setOriginalOperation(row.getOriginalOperation());
        txs.setCoinPrefix(row.isCoinPrefix());
        createdTransactions.add(txs);
    }

    private void createStakingsTxs() {
        var txs = new BinanceBeanV4();
        var row = rowStakings.get(0);
        txs.setRowNumber(row.getDate().getEpochSecond());
        String[] strings = {"Row id " + row.usedIds.toString() + " " + row.getOriginalOperation()};
        txs.setRowValues(strings);
        txs.usedIds.addAll(row.usedIds);
        txs.setAmountBase(row.getChange().abs());
        txs.setMarketBase(row.getCoin());
        txs.setDate(row.getDate());
        txs.setMergedWithAnotherGroup(row.isMergedWithAnotherGroup());
        txs.setType(row.getType());
        txs.setOriginalOperation(row.getOriginalOperation());
        txs.setCoinPrefix(row.isCoinPrefix());
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
            txs.setNote(bean.getNote());
            txs.setOriginalOperation(bean.getOriginalOperation());
            txs.setCoinPrefix(bean.isCoinPrefix());
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
            txs.setCoinPrefix(bean.isCoinPrefix());
            createdTransactions.add(txs);
        }
    }

    public static BinanceBeanV4 createEarningsTxs(BinanceBeanV4 row) {
        row.setRowNumber(row.getDate().getEpochSecond());
        row.usedIds.add(row.getRowId());
        row.setAmountBase(row.getChange().abs());
        row.setMarketBase(row.getCoin());
        row.setType(EARNING);
        return row;
    }

    public static BinanceBeanV4 createFeeTxs(BinanceBeanV4 row) {
        row.setRowNumber(row.getDate().getEpochSecond());
        row.usedIds.add(row.getRowId());
        row.setMarketBase(row.getCoin());
        row.setFeeCurrency(row.getCoin());
        row.setFee(row.getChange().abs());
        row.setType(FEE);
        return row;
    }

    public static BinanceBeanV4 createRebateTxs(BinanceBeanV4 row) {
        row.setRowNumber(row.getDate().getEpochSecond());
        row.usedIds.add(row.getRowId());
        row.setAmountBase(row.getChange().abs());
        row.setMarketBase(row.getCoin());
        row.setType(REBATE);
        return row;
    }

    public static List<BinanceBeanV4> createStakingsTxs(BinanceBeanV4 row) {
        List<BinanceBeanV4> result = new ArrayList<>();
        row.setRowNumber(row.getDate().getEpochSecond());
        String[] strings = {"Row id " + row.usedIds.toString() + " " + row.getOriginalOperation()};
        row.setRowValues(strings);
        row.setAmountBase(row.getChange().abs());
        row.setMarketBase(row.getCoin());
        result.add(row);
        if (row.getOperationType().equals(OPERATION_TYPE_STAKING_REWARDS)) {
            try {
                BinanceBeanV4 clone = (BinanceBeanV4) row.clone();
                clone.setType(STAKE);
                clone.setDate(row.getDate().plusSeconds(1));
                result.add(clone);
            } catch (CloneNotSupportedException e) {
                throw new RuntimeException(e);
            }
        }
        return result;
    }

    public static List<BinanceBeanV4> createBinanceCardSpendingTxs(BinanceBeanV4 row) {

        List<BinanceBeanV4> result = new ArrayList<>();

        if (row.getCoin().isFiat()) {
            BinanceBeanV4 withdrawalBean = new BinanceBeanV4();
            withdrawalBean.setDate(row.getDate());
            withdrawalBean.setMarketBase(row.getCoin());
            withdrawalBean.setAmountBase(row.getChange().abs());
            withdrawalBean.setMarketQuote(row.getCoin());
            withdrawalBean.setAmountQuote(row.getChange().abs());
            withdrawalBean.setType(WITHDRAWAL);
            withdrawalBean.setNote(BINANCE_CARD_SPENDING_FIAT);
            result.add(withdrawalBean);
        } else {
            BinanceBeanV4 sellBean = new BinanceBeanV4();
            sellBean.setDate(row.getDate());
            sellBean.setMarketBase(row.getCoin());
            sellBean.setAmountBase(row.getChange().abs());
            sellBean.setMarketQuote(USD);
            sellBean.setType(SELL);
            sellBean.setNote(BINANCE_CARD_SPENDING_CRYPTO);
            result.add(sellBean);
        }
        return result;
    }

    public static BinanceBeanV4 createDepositWithdrawalTxs(BinanceBeanV4 row) {
        row.setRowNumber(row.getDate().getEpochSecond());
        String[] strings = {"Row id " + row.usedIds + " " + row.getOriginalOperation()};
        row.setRowValues(strings);
        row.setAmountBase(row.getChange().abs());
        row.setMarketBase(row.getCoin());
        if (row.getChange().compareTo(ZERO) > 0) {
            row.setType(DEPOSIT);
        } else {
            row.setType(WITHDRAWAL);
        }
        return row;
    }

    public static BinanceBeanV4 createRewardsTxs(BinanceBeanV4 row) {
        row.setRowNumber(row.getDate().getEpochSecond());
        String[] strings = {"Row id " + row.usedIds.toString() + " " + row.getOriginalOperation()};
        row.setRowValues(strings);
        row.setAmountBase(row.getChange().abs());
        row.setMarketBase(row.getCoin());
        row.setType(REWARD);
        return row;
    }

    public static BinanceBeanV4 createAirdropTxs(BinanceBeanV4 row) {
        row.setRowNumber(row.getDate().getEpochSecond());
        String[] strings = {"Row id " + row.usedIds.toString() + " " + row.getOriginalOperation()};
        row.setRowValues(strings);
        row.setAmountBase(row.getChange().abs());
        row.setMarketBase(row.getCoin());
        row.setType(TransactionType.AIRDROP);
        return row;
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
            } else if ((stRow.getCoin().isFiat() && stRow.getChange().compareTo(ZERO) > 0)
                || (ndRow.getCoin().isFiat() && ndRow.getChange().compareTo(ZERO) > 0)) {
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

        var txsBuySell = newBinanceBeanV4(stRow, baseRow, quoteRow, relatedTransaction);
        txsBuySell.setType(type);
        txsBuySell.setDate(baseRow.getDate());
        createdTransactions.add(txsBuySell);
    }

    private void createStakingETH2_0Txs() {
        BinanceBeanV4 ETHtx = rowStakingETH2_0.stream()
            .filter(row -> row.getCoin().equals(Currency.ETH))
            .findFirst()
            .orElse(null);

        BinanceBeanV4 secondCurrencyTx = rowStakingETH2_0.stream()
            .filter(row -> !row.getCoin().equals(Currency.ETH))
            .findFirst()
            .orElse(null);

        if (ETHtx != null && ETHtx.getChange().compareTo(ZERO) < 0) {
            if (secondCurrencyTx != null) {
                var buyBean = newBinanceBeanV4(ETHtx, secondCurrencyTx, ETHtx, false);
                buyBean.setType(BUY);
                buyBean.setDate(ETHtx.getDate().minusSeconds(1));
                createdTransactions.add(buyBean);

                var stakeBean = newBinanceBeanV4(ETHtx, secondCurrencyTx, secondCurrencyTx, false);
                stakeBean.setType(STAKE);
                stakeBean.setDate(ETHtx.getDate());
                createdTransactions.add(stakeBean);
            }
        } else if (ETHtx != null && ETHtx.getChange().compareTo(ZERO) > 0) {
            if (secondCurrencyTx != null) {
                var buyBean = newBinanceBeanV4(ETHtx, ETHtx, secondCurrencyTx, false);
                buyBean.setType(BUY);
                buyBean.setDate(ETHtx.getDate().plusSeconds(1));
                createdTransactions.add(buyBean);

                var unstakeBean = newBinanceBeanV4(ETHtx, secondCurrencyTx, secondCurrencyTx, false);
                unstakeBean.setType(UNSTAKE);
                unstakeBean.setDate(ETHtx.getDate());
                createdTransactions.add(unstakeBean);
            }

        }

    }

    private BinanceBeanV4 newBinanceBeanV4(BinanceBeanV4 stRow, BinanceBeanV4 baseRow, BinanceBeanV4 quoteRow, boolean relatedTransaction) {
        BinanceBeanV4 txsBuySell = new BinanceBeanV4(); // Create a new instance
        txsBuySell.usedIds.addAll(baseRow.usedIds);
        txsBuySell.usedIds.addAll(quoteRow.usedIds);
        txsBuySell.setMergedWithAnotherGroup(baseRow.isMergedWithAnotherGroup());
        txsBuySell.setRowNumber(baseRow.getDate().getEpochSecond());
        String ids = parseIds(txsBuySell.usedIds);
        String[] strings = {"Row id " + ids, " " + stRow.getOriginalOperation()};
        txsBuySell.setRowValues(strings);
        txsBuySell.setMarketBase(baseRow.getCoin());
        txsBuySell.setAmountBase(baseRow.getChange().abs());
        txsBuySell.setNote(baseRow.getNote());
        txsBuySell.setCoinPrefix(baseRow.isCoinPrefix());
        if (relatedTransaction) {
            txsBuySell.setNote(baseRow.getOriginalOperation().toUpperCase());
        }
        txsBuySell.setMarketQuote(quoteRow.getCoin());
        txsBuySell.setAmountQuote(quoteRow.getChange().abs());
        ExchangeBean.validateCurrencyPair(txsBuySell.getMarketBase(), txsBuySell.getMarketQuote());
        return txsBuySell;
    }


    private BinanceBeanV4 createSmallAssetExchangeBuySellTx(List<BinanceBeanV4> rows) {
        var stRow = rows.get(0);
        var ndRow = rows.get(1);
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
        txsBuySell.setNote(baseRow.getNote());
        if (relatedTransaction) {
            txsBuySell.setNote(baseRow.getOriginalOperation().toUpperCase());
        }
        txsBuySell.setMarketQuote(quoteRow.getCoin());
        txsBuySell.setAmountQuote(quoteRow.getChange().abs());
        ExchangeBean.validateCurrencyPair(txsBuySell.getMarketBase(), txsBuySell.getMarketQuote());
        return txsBuySell;
    }

    private void addRow(BinanceBeanV4 row, int groupSize)  {
        if (row.getOriginalOperation().equals(OPERATION_TYPE_FEE.code)
            || row.getOriginalOperation().equals(OPERATION_TYPE_TRANSACTION_FEE.code)) {
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
        } else if (OPERATION_TYPE_SIMPLE_EARN_LOCKED_REDEMPTION.equals(row.getOperationType())) {
            OPERATION_TYPE_SIMPLE_EARN_LOCKED_REDEMPTION.setMultiRowType(OPERATION_TYPE_SIMPLE_EARN_LOCKED_REDEMPTION.code, false);
            if (rowsDeposit.containsKey(row.getCoin())) {
                rowsDeposit.get(row.getCoin()).add(row);
            } else {
                List<BinanceBeanV4> newList = new ArrayList<>();
                newList.add(row);
                rowsDeposit.put(row.getCoin(), newList);
            }
            if (rowsWithdrawal.containsKey(row.getCoin())) {
                try {
                    var rowWithdrawal = (BinanceBeanV4) row.clone();
                    rowWithdrawal.setType(WITHDRAWAL);
                    rowWithdrawal.setNote(SIMPLE_EARN_LOCKED_REDEMPTION_BINANCE_EARN.code);
                    rowWithdrawal.setChange(rowWithdrawal.getChange().negate());
                    rowsWithdrawal.get(row.getCoin()).add(rowWithdrawal);
                } catch (CloneNotSupportedException e) {
                    throw new DataValidationException("Cannot make withdrawal from the line ");
                }
            } else {
                try {
                    var rowClone = (BinanceBeanV4) row.clone();
                    rowClone.setType(WITHDRAWAL);
                    rowClone.setNote(SIMPLE_EARN_LOCKED_REDEMPTION_BINANCE_EARN.code);
                    rowClone.setChange(rowClone.getChange().negate());
                    List<BinanceBeanV4> newList = new ArrayList<>();
                    newList.add(rowClone);
                    rowsWithdrawal.put(rowClone.getCoin(), newList);
                } catch (CloneNotSupportedException e) {
                    throw new DataValidationException("Cannot make withdrawal from the line ");
                }
            }
        } else if (List.of(OPERATION_TYPE_STAKING_PURCHASE.code, OPERATION_TYPE_STAKING_REWARDS.code,
            OPERATION_TYPE_STAKING_REDEMPTION.code).contains(row.getOriginalOperation())) {
            if (rowsStakings.containsKey(row.getCoin())) {
                rowsStakings.get(row.getCoin()).add(row);
            } else {
                List<BinanceBeanV4> newList = new ArrayList<>();
                newList.add(row);
                rowsStakings.put(row.getCoin(), newList);
            }
        } else if (List.of(OPERATION_TYPE_WITHDRAWAL.code, OPERATION_TYPE_FIAT_WITHDRAWAL.code,
                OPERATION_TYPE_FIAT_WITHDRAW.code, OPERATION_TYPE_C2C_TRANSFER.code)
            .contains(row.getOriginalOperation())) {
            if (rowsWithdrawal.containsKey(row.getCoin())) {
                rowsWithdrawal.get(row.getCoin()).add(row);
            } else {
                List<BinanceBeanV4> newList = new ArrayList<>();
                newList.add(row);
                rowsWithdrawal.put(row.getCoin(), newList);
            }
        } else if (List.of(OPERATION_TYPE_SIMPLE_EARN_FLEXIBLE_SUBSCRIPTION.code, OPERATION_TYPE_SAVING_DISTRIBUTION.code,
            OPERATION_TYPE_SIMPLE_EARN_FLEXIBLE_REDEMPTION.code).contains(row.getOriginalOperation())) {
            row.setNote(row.getOriginalOperation().toUpperCase());
            if (row.getChange().compareTo(ZERO) < 0) {
                row.setType(WITHDRAWAL);
                if (rowsWithdrawal.containsKey(row.getCoin())) {
                    rowsWithdrawal.get(row.getCoin()).add(row);
                } else {
                    List<BinanceBeanV4> newList = new ArrayList<>();
                    newList.add(row);
                    rowsWithdrawal.put(row.getCoin(), newList);
                }
            } else {
                row.setType(DEPOSIT);
                if (rowsDeposit.containsKey(row.getCoin())) {
                    rowsDeposit.get(row.getCoin()).add(row);
                } else {
                    List<BinanceBeanV4> newList = new ArrayList<>();
                    newList.add(row);
                    rowsDeposit.put(row.getCoin(), newList);
                }
            }
        } else if (row.getOriginalOperation().equals(OPERATION_TYPE_BUY_CRYPTO.code) && groupSize == 1) {
            row.setType(DEPOSIT);
            if (rowsDeposit.containsKey(row.getCoin())) {
                rowsDeposit.get(row.getCoin()).add(row);
            } else {
                List<BinanceBeanV4> newList = new ArrayList<>();
                newList.add(row);
                rowsDeposit.put(row.getCoin(), newList);
            }
        }else if (row.getOriginalOperation().equals(OPERATION_TYPE_BUY_CRYPTO.code) && groupSize > 1) {
            if (rowsBuySellRelated.containsKey(row.getCoin())) {
                rowsBuySellRelated.get(row.getCoin()).add(row);
            } else {
                List<BinanceBeanV4> newList = new ArrayList<>();
                newList.add(row);
                rowsBuySellRelated.put(row.getCoin(), newList);
            }
        } else if (row.getOriginalOperation().equals(OPERATION_TYPE_SMALL_ASSETS_EXCHANGE_BNB.code)) {
            row.setNote(row.getOriginalOperation().toUpperCase());
            smallAssetExchange.add(row);
        } else if (row.getOriginalOperation().equals(OPERATION_TYPE_ETH2_0_STAKING.code)){
            if (rowsStakingETH2_0.containsKey(row.getCoin())) {
                rowsStakingETH2_0.get(row.getCoin()).add(row);
            } else {
                List<BinanceBeanV4> newList = new ArrayList<>();
                newList.add(row);
                rowsStakingETH2_0.put(row.getCoin(), newList);
            }
        } else if (
            row.getOriginalOperation().equals(OPERATION_TYPE_BUY.code) ||
                row.getOriginalOperation().equals(OPERATION_TYPE_SELL.code) ||
                row.getOriginalOperation().equals(OPERATION_TYPE_TRANSACTION_RELATED.code) ||
                row.getOriginalOperation().equals(OPERATION_TYPE_LARGE_OTC_TRADING.code) ||
                row.getOriginalOperation().equals(OPERATION_TYPE_BINANCE_CONVERT.code) ||
                row.getOriginalOperation().equals(OPERATION_TYPE_TRANSACTION_BUY.code) ||
                row.getOriginalOperation().equals(OPERATION_TYPE_TRANSACTION_SPEND.code) ||
                row.getOriginalOperation().equals(OPERATION_TYPE_TRANSACTION_REVENUE.code) ||
                row.getOriginalOperation().equals(OPERATION_TYPE_TRANSACTION_SOLD.code) ||
                row.getOriginalOperation().equals(OPERATION_TYPE_CONVERT_FIAT_TO_CRYPTO_OCBS.code)
        ) {
            if (rowsBuySellRelated.containsKey(row.getCoin())) {
                rowsBuySellRelated.get(row.getCoin()).add(row);
            } else {
                List<BinanceBeanV4> newList = new ArrayList<>();
                newList.add(row);
                rowsBuySellRelated.put(row.getCoin(), newList);
            }
        } else if (row.getOriginalOperation().equals(OPERATION_TYPE_DISTRIBUTION.code)
            || row.getOriginalOperation().equals(OPERATION_TYPE_BNB_VAULT_REWARDS.code)) {
            if (rowsRewards.containsKey(row.getCoin())) {
                rowsRewards.get(row.getCoin()).add(row);
            } else {
                List<BinanceBeanV4> newList = new ArrayList<>();
                newList.add(row);
                rowsRewards.put(row.getCoin(), newList);
            }
        } else if (row.getOriginalOperation().equals(OPERATION_TYPE_CARD_CASHBACK.code)
            || row.getOriginalOperation().equals(OPERATION_TYPE_COMMISSION_REBATE.code)
            || row.getOriginalOperation().equals(OPERATION_TYPE_CASHBACK_VOUCHER.code)) {
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
