package io.everytrade.server.plugin.impl.everytrade.parser.exchange;

import io.everytrade.server.plugin.impl.everytrade.parser.exchange.coinbank.CoinbankBeanV1;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.coinbank.CoinbankSortedGroupV1;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.coinbank.CoinbankStatus;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.coinbank.CoinbankOperationTypeV1.OPERATION_TYPE_BUY;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.coinbank.CoinbankOperationTypeV1.OPERATION_TYPE_DEPOSIT;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.coinbank.CoinbankOperationTypeV1.OPERATION_TYPE_SELL;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.coinbank.CoinbankOperationTypeV1.OPERATION_TYPE_WITHDRAWAL;

public class CoinbankExchangeSpecificParserV1 extends DefaultUnivocityExchangeSpecificParser implements IExchangeSpecificParser,
    IMultiExchangeSpecificParser<CoinbankBeanV1> {

    List<CoinbankBeanV1> originalRows;
    List<CoinbankBeanV1> unSupportedRows = new LinkedList<>();
    List<CoinbankBeanV1> rowsWithOneRowTransactionType = new LinkedList<>();
    List<CoinbankBeanV1> rowsWithMultipleRowTransactionType = new LinkedList<>();

    public CoinbankExchangeSpecificParserV1(Class<? extends ExchangeBean> exchangeBean, String delimiter) {
        super(exchangeBean, delimiter);
    }

    @Override
    public List<? extends ExchangeBean> convertMultipleRowsToTransactions(List<CoinbankBeanV1> rows) {
        List<CoinbankBeanV1> result;
        this.originalRows = rows;
        filterRowsByType(rows);
        List<CoinbankBeanV1> beans = prepareBeansForTransactions(rowsWithMultipleRowTransactionType, rowsWithOneRowTransactionType);
        result = beans;
        unSupportedRows.forEach(r -> r.setRowNumber(r.getRowId()));
        result.addAll(unSupportedRows);
        return beans;
    }

    private List<CoinbankBeanV1> prepareBeansForTransactions(List<CoinbankBeanV1> rowsWithMultipleRowTransactionType,
                                                             List<CoinbankBeanV1> rowsWithOneRowTransactionType) {
        List<CoinbankBeanV1> result;
        List<CoinbankBeanV1> oneRowTxs = prepareBeansForTransactionsFromOneRowTypes(rowsWithOneRowTransactionType);
        List<CoinbankBeanV1> multiRowsTxs = prepareBeansForTransactionsFromMultiRows(rowsWithMultipleRowTransactionType);
        result = multiRowsTxs;
        result.addAll(oneRowTxs);
        return result;
    }

    private List<CoinbankBeanV1> prepareBeansForTransactionsFromOneRowTypes(List<CoinbankBeanV1> rows) {

        List<CoinbankBeanV1> result = new LinkedList<>();
        for (CoinbankBeanV1 row : rows) {
            if (!row.getOperationType().isMultiRowType) {
                if (row.getOperationType().equals(OPERATION_TYPE_BUY)) {
                    row = CoinbankSortedGroupV1.createBuyTx(row);
                    result.add(row);
                } else if (row.getOperationType().equals(OPERATION_TYPE_SELL)) {
                    row = CoinbankSortedGroupV1.createSellTx(row);
                    result.add(row);
                } else if (row.getOperationType().equals(OPERATION_TYPE_WITHDRAWAL)) {
                    row = CoinbankSortedGroupV1.createWithdrawalTx(row);
                    result.add(row);
                } else if (row.getOperationType().equals(OPERATION_TYPE_DEPOSIT)) {
                    row = CoinbankSortedGroupV1.createDepositTx(row);
                    result.add(row);
                }
            } else {
                row.setUnsupportedRow(true);
                row.setMessage("Cannot define transaction");
                unSupportedRows.add(row);
            }
        }
        return result;
    }

    private List<CoinbankBeanV1> prepareBeansForTransactionsFromMultiRows(List<CoinbankBeanV1> rowsWithMultipleRowTransactionType) {
        return new LinkedList<>();
    }

    private void filterRowsByType(List<CoinbankBeanV1> rows) {
        rows
            .forEach(r -> {
                if (r.getOperationType() == null) {
                    r.setUnsupportedRow(true);
                    r.setMessage("Cannot define transaction operationType is null");
                    unSupportedRows.add(r);
                } else if (r.getOperationType().equals(OPERATION_TYPE_WITHDRAWAL)) {
                    if (r.getStatusEnum().equals(CoinbankStatus.REALIZED)) {
                        rowsWithOneRowTransactionType.add(r);
                    } else {
                        r.setUnsupportedRow(true);
                        r.setMessage("Withdrawal transaction with status " + r.getStatusEnum() + " is ignored");
                        unSupportedRows.add(r);
                    }
                } else if (r.getOperationType().equals(OPERATION_TYPE_DEPOSIT)) {
                    if (r.getStatusEnum().equals(CoinbankStatus.PROCESSED_BY_OPERATOR)) {
                        rowsWithOneRowTransactionType.add(r);
                    } else {
                        r.setUnsupportedRow(true);
                        r.setMessage("Deposit transaction with status " + r.getStatusEnum() + " is ignored");
                        unSupportedRows.add(r);
                    }
                } else if (r.getOperationType().isMultiRowType) {
                    rowsWithMultipleRowTransactionType.add(r);
                } else {
                    rowsWithOneRowTransactionType.add(r);
                }
            });
    }

    @Override
    public Map<?, List<CoinbankBeanV1>> createGroupsFromRows(List<CoinbankBeanV1> rows) {
        return null;
    }

    @Override
    public Map<?, List<CoinbankBeanV1>> removeGroupsWithUnsupportedRows(Map<?, List<CoinbankBeanV1>> rowGroups) {
        return null;
    }

    @Override
    public List<CoinbankBeanV1> createTransactionFromGroupOfRows(Map<?, List<CoinbankBeanV1>> groups) {
        return null;
    }
}
