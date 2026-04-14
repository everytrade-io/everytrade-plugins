package io.everytrade.server.plugin.impl.everytrade.parser.exchange.okx;

import com.univocity.parsers.common.DataValidationException;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.DefaultUnivocityExchangeSpecificParser;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.ExchangeBean;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.IExchangeSpecificParser;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.IMultiExchangeSpecificParser;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.anycoin.AnycoinSortedGroupV1;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class OkxExchangeSpecificParser extends DefaultUnivocityExchangeSpecificParser implements IExchangeSpecificParser,
    IMultiExchangeSpecificParser<OkxBeanV2> {

    List<OkxBeanV2> originalRows;
    List<OkxBeanV2> unSupportedRows = new LinkedList<>();
    List<OkxBeanV2> rowsWithMultipleRowTransactionType = new LinkedList<>();

    public OkxExchangeSpecificParser(Class<? extends ExchangeBean> exchangeBean, String delimiter) {
        super(exchangeBean, delimiter);
    }

    @Override
    public List<? extends ExchangeBean> convertMultipleRowsToTransactions(List<OkxBeanV2> rows) {
        List<OkxBeanV2> result;
        this.originalRows = rows;
        filterRowsByType(rows);
        result = prepareBeansForTransactions(rowsWithMultipleRowTransactionType);
        unSupportedRows.forEach(r -> r.setRowNumber(r.getRowId()));
        result.addAll(unSupportedRows);
        return result;
    }

    private void filterRowsByType(List<OkxBeanV2> rows) {
        rows.forEach(r -> {
            if (r.getTradeType().equals("Spot")) {
                rowsWithMultipleRowTransactionType.add(r);
            } else {
                r.setUnsupportedRow(true);
                r.setMessage("Ignored trade type: " + r.getTradeType());
                unSupportedRows.add(r);
            }
        });
    }

    private List<OkxBeanV2> prepareBeansForTransactions(List<OkxBeanV2> rowsWithMultipleRowTransactionType) {
        List<OkxBeanV2> result;
        result = prepareBeansForTransactionsFromMultiRows(rowsWithMultipleRowTransactionType);
        return result;
    }

    private List<OkxBeanV2> prepareBeansForTransactionsFromMultiRows(List<OkxBeanV2> multiRows) {
        var groupsFromRows = createGroupsFromRows(multiRows);

        // creating transaction
        return createTransactionFromGroupOfRows(groupsFromRows);
    }

    @Override
    public Map<?, List<OkxBeanV2>> createGroupsFromRows(List<OkxBeanV2> rows) {
        return rows.stream().collect(Collectors.groupingBy(OkxBeanV2::getOrderId));
    }

    @Override
    public Map<?, List<OkxBeanV2>> removeGroupsWithUnsupportedRows(Map<?, List<OkxBeanV2>> rowGroups) {
        return null;
    }

    @Override
    public List<OkxBeanV2> createTransactionFromGroupOfRows(Map<?, List<OkxBeanV2>> groups) {
        List<OkxBeanV2> result = new ArrayList<>();
        for (Map.Entry<?, List<OkxBeanV2>> entry : groups.entrySet()) {
            var sortedGroup = new OkxSortedGroup();
            var group = entry.getValue();
            try {
                sortedGroup.sortGroup(group);
                result.addAll(sortedGroup.createdTransactions);
            } catch (DataValidationException ex) {
                var eMess = ex.getMessage();
                var ids = group.stream().map(ExchangeBean::getRowId).toList();
                var s = AnycoinSortedGroupV1.parseIds(ids);
                setRowsAsUnsupported(group, "One or more rows in group " + "( rows: " + s + ") is unsupported;" + " " + eMess);
            }
        }
        return result;
    }

    private void setRowsAsUnsupported(List<OkxBeanV2> rowsInGroup, String message) {
        rowsInGroup.forEach(r -> {
            r.setMessage(message);
            r.setUnsupportedRow(true);
        });
        unSupportedRows.addAll(rowsInGroup);
    }
}
