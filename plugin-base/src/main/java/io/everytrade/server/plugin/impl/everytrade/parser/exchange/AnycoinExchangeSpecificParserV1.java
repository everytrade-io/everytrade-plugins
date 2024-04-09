package io.everytrade.server.plugin.impl.everytrade.parser.exchange;

import io.everytrade.server.model.TransactionType;
import io.everytrade.server.plugin.impl.everytrade.parser.exception.DataIgnoredException;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.anycoin.AnycoinBeanV1;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.anycoin.AnycoinSortedGroupV1;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v4.BinanceBeanV4;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.anycoin.AnycoinOperationTypeV1.OPERATION_TYPE_TRADE_REFUND;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.anycoin.AnycoinSupportedOperations.UNSUPPORTED_OPERATION_TYPES;
import static java.util.stream.Collectors.groupingBy;

public class AnycoinExchangeSpecificParserV1 extends DefaultUnivocityExchangeSpecificParser implements IExchangeSpecificParser, IMultiExchangeSpecificParser<AnycoinBeanV1> {

    List<AnycoinBeanV1> originalRows;
    List<AnycoinBeanV1> unSupportedRows = new ArrayList<>();
    List<AnycoinBeanV1> rowsWithOneRowTransactionType = new ArrayList<>();
    List<AnycoinBeanV1> rowsWithMultipleRowTransactionType = new ArrayList<>();

    public AnycoinExchangeSpecificParserV1(Class<? extends ExchangeBean> exchangeBean, String delimiter) {
        super(exchangeBean, delimiter);
    }

    @Override
    public List<? extends ExchangeBean> convertMultipleRowsToTransactions(List<AnycoinBeanV1> rows) {
        List<AnycoinBeanV1> result;
        this.originalRows = rows;
        filterRowsByType(rows);
        List<AnycoinBeanV1> beans = prepareBeansForTransactions(rowsWithMultipleRowTransactionType, rowsWithOneRowTransactionType);
        result = beans;
        unSupportedRows.forEach(r -> r.setRowNumber(r.getRowId()));
        result.addAll(unSupportedRows);
        return beans;
    }

    private void filterRowsByType(List<AnycoinBeanV1> rows) {
        String orderId = rows.get(0).getOrderId();
        rows
                .forEach(r -> {
                    if (r.getOperationType().code.equals("WITHDRAWAL_BLOCK") || r.getOperationType().code.equals("WITHDRAWAL_UNBLOCK")){
                        unSupportedRows.add(r);
                    } else if (r.getOperationType().isMultiRowType) {
                        rowsWithMultipleRowTransactionType.add(r);
                    } else {
                        rowsWithOneRowTransactionType.add(r);
                    }
                });
    }

    private List<AnycoinBeanV1> prepareBeansForTransactions(List<AnycoinBeanV1> rowsWithMultipleRowTransactionType,
                                                            List<AnycoinBeanV1> rowsWithOneRowTransactionType) {
        List<AnycoinBeanV1> result;
        List<AnycoinBeanV1> oneRowTxs = prepareBeansForTransactionsFromOneRowTypes(rowsWithOneRowTransactionType);
        List<AnycoinBeanV1> multiRowsTxs = prepareBeansForTransactionsFromMultiRows(rowsWithMultipleRowTransactionType);
        result = multiRowsTxs;
        result.addAll(oneRowTxs);
        return result;
    }

    private List<AnycoinBeanV1> prepareBeansForTransactionsFromMultiRows(List<AnycoinBeanV1> multiRows) {
        var groupsFromRows = createGroupsFromRows(multiRows);

        var cleanGroups = removeGroupsWithUnsupportedRows(groupsFromRows);
        // creating transaction
        return createTransactionFromGroupOfRows(cleanGroups);
    }

    private List<AnycoinBeanV1> prepareBeansForTransactionsFromOneRowTypes(List<AnycoinBeanV1> singleRow) {
        List<AnycoinBeanV1> result = new ArrayList<>();
        AnycoinBeanV1 newBean = new AnycoinBeanV1();
        if (singleRow.size() < 1) {
            return result;
        }
        for (AnycoinBeanV1 beanV1 : singleRow){
            if (beanV1.getType().equalsIgnoreCase("deposit")) {
                newBean.setOperationType(beanV1.getOperationType());
                newBean.setDate(beanV1.getDate().toString());
                newBean.setMarketBase(beanV1.getCoin());
                newBean.setBaseAmount(beanV1.getAmount());
                newBean.setMarketQuote(beanV1.getCoin());
                newBean.setQuoteAmount(beanV1.getAmount());
                newBean.setTransactionType(TransactionType.DEPOSIT);
                newBean.setType(beanV1.getOperationType().code);
                result.add(newBean);
            } else if (beanV1.getType().equalsIgnoreCase("withdrawal")) {
                newBean.setOperationType(beanV1.getOperationType());
                newBean.setDate(beanV1.getDate().toString());
                newBean.setMarketBase(beanV1.getCoin());
                newBean.setBaseAmount(beanV1.getAmount());
                newBean.setMarketQuote(beanV1.getCoin());
                newBean.setQuoteAmount(beanV1.getAmount());
                newBean.setTransactionType(TransactionType.WITHDRAWAL);
                newBean.setType(beanV1.getOperationType().code);
                result.add(newBean);
            } else {
                unSupportedRows.add(beanV1);
            }
        }

        return result;
    }

    @Override
    public Map<String, List<AnycoinBeanV1>> createGroupsFromRows(List<AnycoinBeanV1> rows) {
        for (AnycoinBeanV1 row : rows) {
            if (row.getOrderId() == null) {
                row.setOrderId("0");
            }
        }
        return rows.stream().collect(groupingBy(AnycoinBeanV1::getOrderId));
    }

    @Override
    public Map<String, List<AnycoinBeanV1>> removeGroupsWithUnsupportedRows(Map<?, List<AnycoinBeanV1>> rowGroups) {
        Map<String, List<AnycoinBeanV1>> result = new LinkedHashMap<>();
        for (Map.Entry<?, List<AnycoinBeanV1>> entry : rowGroups.entrySet()) {
            var group = entry.getValue();
            group.forEach(r -> {
                if (OPERATION_TYPE_TRADE_REFUND.code.equals(r.getOperationType().code)) {
                    unSupportedRows.addAll(group);
                    result.clear();
                } else if (UNSUPPORTED_OPERATION_TYPES.contains(r.getOperationType().code)) {
                    unSupportedRows.add(r);
                } else {
                    result.put(r.getOrderId(), group);
                }
            });
        }
        return result;
    }

    @Override
    public List<AnycoinBeanV1> createTransactionFromGroupOfRows(Map<?, List<AnycoinBeanV1>> groups) {
        List<AnycoinBeanV1> result = new ArrayList<>();
        for (Map.Entry<?, List<AnycoinBeanV1>> entry : groups.entrySet()) {
            var sortedGroup = new AnycoinSortedGroupV1();
            var group = entry.getValue();
            sortedGroup.sortGroup(group);
            result.addAll(sortedGroup.createdTransactions);
        }
        return result;
    }
}
