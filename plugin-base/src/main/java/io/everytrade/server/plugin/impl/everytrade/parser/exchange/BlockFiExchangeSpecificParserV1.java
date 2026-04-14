package io.everytrade.server.plugin.impl.everytrade.parser.exchange;

import com.univocity.parsers.common.DataValidationException;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v4.BinanceSortedGroupV4;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.blockFi.BlockFiBeanV1;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.blockFi.BlockFiSortedGroupV1;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.blockFi.BlockFiOperationTypeV1.OPERATION_TYPE_BONUS_PAYMENT;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.blockFi.BlockFiOperationTypeV1.OPERATION_TYPE_CRYPTO_TRANSFER;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.blockFi.BlockFiOperationTypeV1.OPERATION_TYPE_INTEREST_PAYMENT;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.blockFi.BlockFiOperationTypeV1.OPERATION_TYPE_REFERRAL_BONUS;

public class BlockFiExchangeSpecificParserV1 extends DefaultUnivocityExchangeSpecificParser implements IExchangeSpecificParser,
    IMultiExchangeSpecificParser<BlockFiBeanV1> {

    List<BlockFiBeanV1> originalRows;
    List<BlockFiBeanV1> unSupportedRows = new LinkedList<>();
    List<BlockFiBeanV1> rowsWithOneRowTransactionType = new LinkedList<>();
    List<BlockFiBeanV1> rowsWithMultipleRowTransactionType = new LinkedList<>();

    public BlockFiExchangeSpecificParserV1(Class<? extends ExchangeBean> exchangeBean, String delimiter) {
        super(exchangeBean, delimiter);
    }

    @Override
    public List<? extends ExchangeBean> convertMultipleRowsToTransactions(List<BlockFiBeanV1> rows) {
        List<BlockFiBeanV1> result;
        this.originalRows = rows;
        filterRowsByType(rows);
        List<BlockFiBeanV1> beans = prepareBeansForTransactions(rowsWithMultipleRowTransactionType, rowsWithOneRowTransactionType);
        result = beans;
        unSupportedRows.forEach(r -> r.setRowNumber(r.getRowId()));
        result.addAll(unSupportedRows);
        return beans;
    }

    private List<BlockFiBeanV1> prepareBeansForTransactions(List<BlockFiBeanV1> multiRow, List<BlockFiBeanV1> oneRow) {
        List<BlockFiBeanV1> result;
        List<BlockFiBeanV1> oneRowTxs = prepareBeansForTransactionsFromOneRowTypes(rowsWithOneRowTransactionType);
        List<BlockFiBeanV1> multiRowsTxs = prepareBeansForTransactionsFromMultiRows(rowsWithMultipleRowTransactionType);
        result = multiRowsTxs;
        result.addAll(oneRowTxs);
        return result;
    }

    private List<BlockFiBeanV1> prepareBeansForTransactionsFromMultiRows(List<BlockFiBeanV1> multiRows) {
        var groupedByTime = createGroupsFromRows(multiRows);
        return createTransactionFromGroupOfRows(groupedByTime);
    }

    private List<BlockFiBeanV1> prepareBeansForTransactionsFromOneRowTypes(List<BlockFiBeanV1> oneRows) {
        List<BlockFiBeanV1> result = new LinkedList<>();
        for (BlockFiBeanV1 row : oneRows) {
            try {
                if (!row.getOperationType().isMultiRowType) {
                    if (row.getOperationType().equals(OPERATION_TYPE_CRYPTO_TRANSFER)) {
                        row = BlockFiSortedGroupV1.createDepositWithdrawalTx(row);
                        result.add(row);
                    } else if (row.getOperationType().equals(OPERATION_TYPE_INTEREST_PAYMENT)) {
                        row = BlockFiSortedGroupV1.createEarnTx(row);
                        result.add(row);
                    } else if (row.getOperationType().equals(OPERATION_TYPE_REFERRAL_BONUS)
                        || row.getOperationType().equals(OPERATION_TYPE_BONUS_PAYMENT)) {
                        row = BlockFiSortedGroupV1.createRewardTx(row);
                        result.add(row);
                    }
                } else {
                    row.setUnsupportedRow(true);
                    row.setMessage("Cannot define transaction");
                    unSupportedRows.add(row);
                }
            } catch (Exception e) {
                row.setUnsupportedRow(true);
                row.setMessage(e.getMessage());
                unSupportedRows.add(row);
            }
        }
        return result;
    }

    private void filterRowsByType(List<BlockFiBeanV1> rows) {
        rows.forEach(r -> {
            if (r.getOperationType() == null) {
                unSupportedRows.add(r);
                r.setUnsupportedRow(true);
                r.setMessage("Unsupported operation type");
            } else if (r.getOperationType().isMultiRowType) {
                rowsWithMultipleRowTransactionType.add(r);
            } else {
                rowsWithOneRowTransactionType.add(r);
            }
        });
    }

    @Override
    public Map<?, List<BlockFiBeanV1>> createGroupsFromRows(List<BlockFiBeanV1> rows) {
        return rows.stream().collect(Collectors.groupingBy(BlockFiBeanV1::getConfirmedAt));
    }

    @Override
    public Map<?, List<BlockFiBeanV1>> removeGroupsWithUnsupportedRows(Map<?, List<BlockFiBeanV1>> rowGroups) {
        return null;
    }

    @Override
    public List<BlockFiBeanV1> createTransactionFromGroupOfRows(Map<?, List<BlockFiBeanV1>> groups) {
        List<BlockFiBeanV1> result = new ArrayList<>();
        for (Map.Entry<?, List<BlockFiBeanV1>> entry : groups.entrySet()) {
            var sortedGroup = new BlockFiSortedGroupV1();
            var rows = entry.getValue();
            try {
                sortedGroup.sortGroup(rows);
                result.addAll(sortedGroup.createdTransactions);
            } catch (DataValidationException e) {
                    var eMess = e.getMessage();
                    var ids = rows.stream().map(ExchangeBean::getRowId).toList();
                    var s = BinanceSortedGroupV4.parseIds(ids);
                    setRowsAsUnsupported(rows, "One or more rows in group " + "( rows: " + s + ") is unsupported;" + " " + eMess);
            }
        }
        return result;
    }

    private void setRowsAsUnsupported(List<BlockFiBeanV1> rowsInGroup, String message) {
        rowsInGroup.forEach(r -> {
            r.setMessage(message);
            r.setUnsupportedRow(true);
        });
        unSupportedRows.addAll(rowsInGroup);
    }
}
