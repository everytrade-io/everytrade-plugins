package io.everytrade.server.plugin.impl.everytrade.parser.exchange;

import com.univocity.parsers.common.DataValidationException;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.coibasePro.v2.CoinbaseProBeanV2;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.coibasePro.v2.CoinbaseProSortedGroup;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.groupingBy;

public class CoinBaseProExchangeSpecificParser extends DefaultUnivocityExchangeSpecificParser implements IExchangeSpecificParser,
    IMultiExchangeSpecificParser<CoinbaseProBeanV2> {

    public CoinBaseProExchangeSpecificParser(Class<? extends ExchangeBean> exchangeBean, String delimiter) {
        super(exchangeBean, delimiter);
    }

    List<CoinbaseProBeanV2> rows;
    List<CoinbaseProBeanV2> unSupportedRows = new ArrayList<>();
    List<CoinbaseProBeanV2> failedRows = new ArrayList<>();

    @Override
    public List<? extends ExchangeBean> convertMultipleRowsToTransactions(List<CoinbaseProBeanV2> rows) {
        List<CoinbaseProBeanV2> result;
//        this.rows = setRowsWithIds(rows);
        var groupedRowsByTradeId = createGroupedRows(rows);
        // clean groups of rows from unsupported rubbish
        var cleanUnsupportedGroups = removeGroupsWithUnsupportedRows(groupedRowsByTradeId);
        // clean group of rows from duplicities
        // creating transaction
        List<CoinbaseProBeanV2> rowsReadyForTxs = createTransactionFromGroupOfRows(cleanUnsupportedGroups);
        result = rowsReadyForTxs;
        unSupportedRows.stream().forEach(r -> {
            r.setRowNumber(r.getRowId());
        });
        failedRows.stream().forEach(r -> {
            r.setRowNumber(r.getRowId());
        });
        result.addAll(unSupportedRows);
        result.addAll(failedRows);
        return rowsReadyForTxs;
    }

    private Map<String, List<CoinbaseProBeanV2>> createGroupedRows(List<CoinbaseProBeanV2> rows) {
        return rows.stream().collect(groupingBy(CoinbaseProBeanV2::getTradeId));
    }

    @Override
    public Map<?, List<CoinbaseProBeanV2>> removeGroupsWithUnsupportedRows(Map<?, List<CoinbaseProBeanV2>> rowGroups) {
        Map<Object, List<CoinbaseProBeanV2>> result = new HashMap<>();
        for (Map.Entry<?, List<CoinbaseProBeanV2>> entry : rowGroups.entrySet()) {
            if ("".equals(entry.getKey())) {
                var rowsWithoutTradeId = entry.getValue();
                List<CoinbaseProBeanV2> supportedRowsWithoutTradeId = new ArrayList<>();
                for (CoinbaseProBeanV2 rowWithoutTradeId : rowsWithoutTradeId) {
                    if (rowWithoutTradeId.isUnsupportedRow()) {
                        setRowAsUnsupported(rowWithoutTradeId, "Type of row is unsupported; ");
                    } else {
                        supportedRowsWithoutTradeId.add(rowWithoutTradeId);
                    }
                }
                result.put("", supportedRowsWithoutTradeId);
            } else {
                var rowsInGroup = entry.getValue();
                var isOneOrMoreUnsupportedRows =
                    !rowsInGroup.stream().filter(r -> r.isUnsupportedRow() == true).collect(Collectors.toList()).isEmpty();
                if (!isOneOrMoreUnsupportedRows) {
                    result.put(entry.getKey(), entry.getValue());
                } else {
                    var s = CoinbaseProSortedGroup.parseListIds(rowsInGroup);
                    setRowsAsUnsupported(rowsInGroup, String.format("One or more rows in group (rows:%s) is unsupported;", s));
                }
            }
        }
        return result;
    }

    private void setRowsAsUnsupported(List<CoinbaseProBeanV2> rowsInGroup, String message) {
        rowsInGroup.forEach(r -> {
            r.setMessage(message);
            r.setUnsupportedRow(true);
        });
        unSupportedRows.addAll(rowsInGroup);
    }

    private void setFailedRows(List<CoinbaseProBeanV2> rowsInGroup, String message) {
        rowsInGroup.forEach(r -> {
            r.setMessage(message);
            r.setFailedDataRow(true);
        });
        failedRows.addAll(rowsInGroup);
    }

    private void setRowAsUnsupported(CoinbaseProBeanV2 row, String message) {
        row.setUnsupportedRow(true);
        row.setMessage(message);
        unSupportedRows.add(row);
    }

    private List<CoinbaseProBeanV2> setRowsWithIds(List<CoinbaseProBeanV2> rows) {
        int i = 1;
        for (CoinbaseProBeanV2 row : rows) {
            i++;
            row.setRowId(i);
            row.setMessage("RoW id " + i);
        }
        return rows;
    }

    @Override
    public List<CoinbaseProBeanV2> createTransactionFromGroupOfRows(Map<?, List<CoinbaseProBeanV2>> groups) {
        List<CoinbaseProBeanV2> result = new ArrayList<>();
        for (Map.Entry<?, List<CoinbaseProBeanV2>> entry : groups.entrySet()) {
            var rows = entry.getValue();
            // rows without "trade id" don't have to be sorted
            if ("".equals(entry.getKey())) {
                result.addAll(rows);
            } else {
                var sortedGroup = new CoinbaseProSortedGroup();
                sortedGroup.setTradeId(entry.getKey().toString());
                try {
                    sortedGroup.sortGroup(rows);
                    result.add(sortedGroup.createdTransaction);
                } catch (DataValidationException e) {
                    var eMess = e.getMessage();
                    var ids = rows.stream().map(r -> r.getRowId()).collect(Collectors.toList());
                    var s = CoinbaseProSortedGroup.parseIds(ids);
                    setFailedRows(rows, String.format("One or more rows in group (rows: %s) is unsupported; %s", ids, eMess));
                }
            }
        }
        return result;
    }
}
