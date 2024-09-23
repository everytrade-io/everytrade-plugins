package io.everytrade.server.plugin.impl.everytrade.parser.exchange;

import com.univocity.parsers.common.DataValidationException;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v4.BinanceBeanV4;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v4.BinanceSortedGroupV4;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import static io.everytrade.server.model.TransactionType.AIRDROP;
import static io.everytrade.server.model.TransactionType.DEPOSIT;
import static io.everytrade.server.model.TransactionType.EARNING;
import static io.everytrade.server.model.TransactionType.FEE;
import static io.everytrade.server.model.TransactionType.REBATE;
import static io.everytrade.server.model.TransactionType.REWARD;
import static io.everytrade.server.model.TransactionType.STAKE;
import static io.everytrade.server.model.TransactionType.STAKING_REWARD;
import static io.everytrade.server.model.TransactionType.UNSTAKE;
import static io.everytrade.server.model.TransactionType.WITHDRAWAL;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v4.BinanceOperationTypeV4.OPERATION_TYPE_BINANCE_CARD_SPENDING;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v4.BinanceOperationTypeV4.OPERATION_TYPE_BUY_CRYPTO;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v4.BinanceOperationTypeV4.OPERATION_TYPE_CASHBACK_VOUCHER;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v4.BinanceOperationTypeV4.OPERATION_TYPE_ETH2_0_STAKING_REWARDS;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v4.BinanceOperationTypeV4.OPERATION_TYPE_SAVING_DISTRIBUTION;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v4.BinanceOperationTypeV4.OPERATION_TYPE_SIMPLE_EARN_FLEXIBLE_REDEMPTION;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v4.BinanceOperationTypeV4.OPERATION_TYPE_SIMPLE_EARN_FLEXIBLE_SUBSCRIPTION;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v4.BinanceOperationTypeV4.OPERATION_TYPE_SIMPLE_EARN_LOCKED_SUBSCRIPTION;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v4.BinanceOperationTypeV4.OPERATION_TYPE_SMALL_ASSETS_EXCHANGE_BNB;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v4.BinanceOperationTypeV4.OPERATION_TYPE_TRANSACTION_REVENUE;
import static java.math.BigDecimal.ZERO;
import static java.util.stream.Collectors.groupingBy;

public class BinanceExchangeSpecificParserV4 extends DefaultUnivocityExchangeSpecificParser implements IExchangeSpecificParser,
    IMultiExchangeSpecificParser<BinanceBeanV4> {

    private static final long TRANSACTION_MERGE_TOLERANCE_MS = 1000;
    List<BinanceBeanV4> originalRows;
    List<BinanceBeanV4> unSupportedRows = new ArrayList<>();
    List<BinanceBeanV4> rowsWithOneRowTransactionType = new ArrayList<>();
    List<BinanceBeanV4> rowsWithMultipleRowTransactionType = new ArrayList<>();

    public BinanceExchangeSpecificParserV4(Class<? extends ExchangeBean> exchangeBean, String delimiter, boolean isRowInsideQuotes) {
        super(exchangeBean, delimiter);
    }

    @Override
    protected void correctFile(File file) {
        try {
            // Load the entire content of the file into a String
            String content = Files.lines(file.toPath(), StandardCharsets.UTF_8).collect(Collectors.joining("\n"));

            // Remove all double quotes from the content
            content = content.replace("\"", "");

            // rewrite the file with the new content
            Files.write(file.toPath(), content.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Method correct errors made by rows with quotes
     * e.g. ""2023-01-04 05:43:39;IOTX;IOTX;0.00006298""
     *
     * @param rows
     * @return
     */
    @Override
    protected String[] correctRow(String[] rows) {
        try {
            var arrayAsList = Arrays.asList(rows);
            String join = String.join("", arrayAsList).replace("null", "");
            if (rows[(rows.length - 1)].equals(join)) {
                return rows[(rows.length - 1)].split(delimiter);
            }
        } catch (Exception ignored) {
        }
        return rows;
    }

    /**
     * Method should solve exception where are many rows with operation "Small assets exchange BNB" done
     * in the same time
     *
     * @param mergedGroups
     * @return
     */
    Map<?, List<BinanceBeanV4>> splitExceptionGroups(Map<?, List<BinanceBeanV4>> mergedGroups) {
        Map<Instant, List<BinanceBeanV4>> result = new HashMap<>();
        final long timeIncrease = 1;
        try {
            for (Map.Entry<?, List<BinanceBeanV4>> entry : mergedGroups.entrySet()) {
                var rowsInGroup = entry.getValue();
                Instant key = (Instant) entry.getKey();
                List<BinanceBeanV4> smallAssetExchange = rowsInGroup.stream()
                    .filter(r -> r.getOriginalOperation().equalsIgnoreCase(OPERATION_TYPE_SMALL_ASSETS_EXCHANGE_BNB.code))
                    .collect(Collectors.toList());
                if (smallAssetExchange.size() == 0) {
                    result.put(key, rowsInGroup);
                } else if (smallAssetExchange.size() == rowsInGroup.size()) {
                    if (smallAssetExchange.size() % 2 != 0) {
                        rowsInGroup.stream().forEach(row -> {
                            row.setMessage(String.format("Wrong number of operation %s", OPERATION_TYPE_SMALL_ASSETS_EXCHANGE_BNB.code));
                            row.setUnsupportedRow(true);
                        });
                        result.put(key, rowsInGroup);
                    } else {
                        rowsInGroup = rowsInGroup.stream().sorted(Comparator.comparingInt(BinanceBeanV4::getRowId))
                            .collect(Collectors.toList());
                        int i = 0;
                        for (BinanceBeanV4 row : rowsInGroup) {
                            if (i % 2 == 0) {
                                List<BinanceBeanV4> smallGroup = new ArrayList<>();
                                smallGroup.add(row);
                                result.put(key, smallGroup);
                            } else {
                                var smallGroup = result.get(key);
                                smallGroup.add(row);
                                result.put(key, smallGroup);
                                key = key.plusMillis(timeIncrease);
                            }
                            i++;
                        }
                    }
                } else {
                    rowsInGroup.stream().forEach(row -> {
                        row.setMessage("Too many operation in the same time");
                        row.setUnsupportedRow(true);
                    });
                    result.put(key, rowsInGroup);
                }
            }
        } catch (Exception ignore) {
            return mergedGroups;
        }
        return result;
    }

    private void filterRowsByType(List<BinanceBeanV4> rows) {
        rows.stream().forEach(r -> {
            if (!r.isUnsupportedRow() && !r.getOperationType().isMultiRowType) {
                rowsWithOneRowTransactionType.add(r);
            } else {
                rowsWithMultipleRowTransactionType.add(r);
            }
        });
    }

    private List<BinanceBeanV4> prepareBeansForTransactions(List<BinanceBeanV4> rowsWithMultipleRowTransactionType,
                                                            List<BinanceBeanV4> rowsWithOneRowTransactionType) {
        List<BinanceBeanV4> result;
        List<BinanceBeanV4> oneRowTxs = prepareBeansForTransactionsFromOneRowTypes(rowsWithOneRowTransactionType);
        List<BinanceBeanV4> multiRowsTxs = prepareBeansForTransactionsFromMultiRows(rowsWithMultipleRowTransactionType);
        result = multiRowsTxs;
        result.addAll(oneRowTxs);
        return result;
    }

    private List<BinanceBeanV4> prepareBeansForTransactionsFromOneRowTypes(List<BinanceBeanV4> rows) {
        List<BinanceBeanV4> result = new ArrayList<>();
        for (BinanceBeanV4 row : rows) {
            if (!row.getOperationType().isMultiRowType) { // test yes
                if (List.of(OPERATION_TYPE_SIMPLE_EARN_FLEXIBLE_SUBSCRIPTION.code, OPERATION_TYPE_SAVING_DISTRIBUTION.code,
                    OPERATION_TYPE_SIMPLE_EARN_FLEXIBLE_REDEMPTION.code).contains(row.getOriginalOperation())) {
                    row.setNote(row.getOriginalOperation().toUpperCase());
                    if (row.getChange().compareTo(ZERO) < 0) {
                        row.setType(WITHDRAWAL);
                    } else {
                        row.setType(DEPOSIT);
                    }
                }
                if (OPERATION_TYPE_SIMPLE_EARN_LOCKED_SUBSCRIPTION.code.equals(row.getOriginalOperation())) {
                    if (row.getChange().compareTo(ZERO) < 0) {
                        row.setType(STAKE);
                    } else {
                        row.setType(UNSTAKE);
                    }
                }
                if (REBATE.equals(row.getType()) || REBATE.equals(OPERATION_TYPE_CASHBACK_VOUCHER)) {
                    row = BinanceSortedGroupV4.createRebateTxs(row);
                    result.add(row);
                } else if (OPERATION_TYPE_BINANCE_CARD_SPENDING.equals(row.getOperationType())) {
                    List<BinanceBeanV4> createdTx = BinanceSortedGroupV4.createBinanceCardSpendingTxs(row);
                    result.addAll(createdTx);
                } else if (EARNING.equals(row.getType())) {
                    row = BinanceSortedGroupV4.createEarningsTxs(row);
                    result.add(row);
                } else if (FEE.equals(row.getType())){
                    row = BinanceSortedGroupV4.createFeeTxs(row);
                    result.add(row);
                } else if (AIRDROP.equals(row.getType())) {
                    row = BinanceSortedGroupV4.createAirdropTxs(row);
                    result.add(row);
                } else if (STAKING_REWARD.equals(row.getType()) || UNSTAKE.equals(row.getType()) || STAKE.equals(row.getType())) {
                    List<BinanceBeanV4> createdTx = BinanceSortedGroupV4.createStakingsTxs(row);
                    result.addAll(createdTx);
                    if (row.getOperationType().equals(OPERATION_TYPE_ETH2_0_STAKING_REWARDS)) {
                        var rowStake = cloneRewardToStake(row);
                        result.add(rowStake);
                    }
                } else if (DEPOSIT.equals(row.getType()) || WITHDRAWAL.equals(row.getType())) {
                    row = BinanceSortedGroupV4.createDepositWithdrawalTxs(row);
                    result.add(row);
                } else if (REWARD.equals(row.getType())) {
                    row = BinanceSortedGroupV4.createRewardsTxs(row);
                    result.add(row);
                } else {
                    row.setUnsupportedRow(true);
                    row.setMessage("Cannot define transaction");
                    unSupportedRows.add(row);
                }
            }
        }
        return result;
    }
    private BinanceBeanV4 cloneRewardToStake(BinanceBeanV4 row) {
        try {
            BinanceBeanV4 clone = (BinanceBeanV4) row.clone();
            clone.setType(STAKE);
            clone.setDate(row.getDate().plusSeconds(1));
            return clone;
        } catch (CloneNotSupportedException ignore) {
            row.setNote("ETH 2.0 STAKE not added");
        }
        return row;
    }

    private List<BinanceBeanV4> prepareBeansForTransactionsFromMultiRows(List<BinanceBeanV4> rows) {
        var groupedRowsByTime = createGroupsFromRows(rowsWithMultipleRowTransactionType);
        Map<Instant, List<BinanceBeanV4>> sortedGroupsByDate = new TreeMap<>(groupedRowsByTime);
        // clean groups of rows from unsupported rubbish
        var cleanGroups = removeGroupsWithUnsupportedRows(sortedGroupsByDate);
        // merging rows nearly in the same time
        var mergedGroups = mergeGroupsInTimeWithinTolerance((Map<Instant, List<BinanceBeanV4>>) cleanGroups);
        // creating transaction
        return createTransactionFromGroupOfRows(mergedGroups);
    }

    public List<? extends ExchangeBean> convertMultipleRowsToTransactions(List<BinanceBeanV4> rows) {
        List<BinanceBeanV4> result;
        this.originalRows = rows;
        filterRowsByType(rows);
        List<BinanceBeanV4> beans = prepareBeansForTransactions(rowsWithMultipleRowTransactionType, rowsWithOneRowTransactionType);
        result = beans;
        unSupportedRows.stream().forEach(r -> {
            r.setRowNumber((long) r.getRowId());
        });
        result.addAll(unSupportedRows);
        return beans;
    }

    @Override
    public Map<?, List<BinanceBeanV4>> removeGroupsWithUnsupportedRows(Map<?, List<BinanceBeanV4>> rowGroups) {
        Map<Object, List<BinanceBeanV4>> result = new HashMap<>();
        for (Map.Entry<?, List<BinanceBeanV4>> entry : rowGroups.entrySet()) {
            var rowsInGroup = entry.getValue();
            List<BinanceBeanV4> unSupportedRow = rowsInGroup.stream()
                .filter(r -> r.isUnsupportedRow() == true)
                .collect(Collectors.toList());
            var isOneOrMoreUnsupportedRows =
                !unSupportedRow.isEmpty();
            if (!isOneOrMoreUnsupportedRows) {
                result.put(entry.getKey(), entry.getValue());
            } else {
                var mess = unSupportedRow.get(0).getMessage();
                var ids = rowsInGroup.stream().map(r -> r.getRowId()).collect(Collectors.toList());
                var s = BinanceSortedGroupV4.parseIds(ids);
                setRowsAsUnsupported(rowsInGroup, "One or more rows in group " + "\"rows:" + s + "\" is unsupported;" + mess);
            }
        }
        return result;
    }

    private void setRowsAsUnsupported(List<BinanceBeanV4> rowsInGroup, String message) {
        rowsInGroup.forEach(r -> {
            r.setMessage(message);
            r.setUnsupportedRow(true);
        });
        unSupportedRows.addAll(rowsInGroup);
    }

    private Map<Instant, List<BinanceBeanV4>> mergeGroupsInTimeWithinTolerance(Map<Instant, List<BinanceBeanV4>> groups) {
        Map<Instant, List<BinanceBeanV4>> result = new HashMap<>();
        Map<Instant, List<BinanceBeanV4>> sortedMap = groups.entrySet()
            .stream()
            .sorted(Comparator.comparing(Map.Entry::getKey))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                (oldValue, newValue) -> oldValue, LinkedHashMap::new));

        Instant previousKey = Instant.EPOCH;
        List<BinanceBeanV4> previousValues = new ArrayList<>();
        for (Map.Entry<Instant, List<BinanceBeanV4>> entry : sortedMap.entrySet()) {
            var currentKey = entry.getKey();
            var currentValues = entry.getValue();
            if (currentValues.stream().anyMatch(op -> op.getOperationType().equals(OPERATION_TYPE_TRANSACTION_REVENUE))){
                result.put(currentKey, currentValues);
                continue;
            } else if ((currentKey.minusMillis(TRANSACTION_MERGE_TOLERANCE_MS).equals(previousKey)
                || currentKey.minusMillis(TRANSACTION_MERGE_TOLERANCE_MS).isBefore(previousKey))) {

                if (currentValues.get(0).getCoin().isFiat()
                    && previousValues.get(0).getCoin().isFiat()
                    && currentValues.get(0).getOperationType().equals(OPERATION_TYPE_BUY_CRYPTO)
                ) {
                    previousValues = currentValues;
                    previousKey = currentKey;
                    previousValues.forEach(r -> r.setDate((Instant) null));
                    continue;
                }
                List<BinanceBeanV4> all = currentValues;
                all.addAll(previousValues);
                all.forEach(r -> {
                    r.setMergedWithAnotherGroup(true);
                });
                result.put(previousKey, all);
                currentKey = previousKey;
                currentValues = all;
            } else {
                result.put(currentKey, currentValues);
            }
            previousKey = currentKey;
            previousValues = currentValues;
        }
        return result;
    }

    @Override
    public List<BinanceBeanV4> createTransactionFromGroupOfRows(Map<?, List<BinanceBeanV4>> groups) {
        List<BinanceBeanV4> result = new ArrayList<>();
        for (Map.Entry<?, List<BinanceBeanV4>> entry : groups.entrySet()) {
            var sortedGroup = new BinanceSortedGroupV4();
            sortedGroup.setTime(entry.getKey());
            var rows = entry.getValue();
            try {
                sortedGroup.sortGroup(rows);
                result.addAll(sortedGroup.createdTransactions);
            } catch (DataValidationException e) {
                try {
                    if (rows.get(0).isMergedWithAnotherGroup()) {
                        rows.stream().forEach(r -> r.setMergedWithAnotherGroup(false));
                        Map<?, List<BinanceBeanV4>> groupsBeforeMerge = createGroupsFromRows(rows);
                        List<BinanceBeanV4> anotherResult = createTransactionFromGroupOfRows(groupsBeforeMerge);
                        result.addAll(anotherResult);
                    } else {
                        throw new DataValidationException(e.getMessage());
                    }
                } catch (DataValidationException ex) {
                    var eMess = e.getMessage();
                    var ids = rows.stream().map(r -> r.getRowId()).collect(Collectors.toList());
                    var s = BinanceSortedGroupV4.parseIds(ids);
                    setRowsAsUnsupported(rows, "One or more rows in group " + "( rows: " + s + ") is unsupported;" + " " + eMess);
                }
            }
        }
        return result;
    }

    @Override
    public Map<Instant, List<BinanceBeanV4>> createGroupsFromRows(List<BinanceBeanV4> rows) {
        return rows.stream().collect(groupingBy(BinanceBeanV4::getDate));
    }

}
