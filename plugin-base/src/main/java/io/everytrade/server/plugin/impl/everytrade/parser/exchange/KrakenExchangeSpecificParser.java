package io.everytrade.server.plugin.impl.everytrade.parser.exchange;

import com.univocity.parsers.common.DataValidationException;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean.KrakenBeanV2;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v4.BinanceSortedGroupV4;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.kraken.KrakenSortedGroup;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.kraken.KrakenSupportedTypes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.groupingBy;

public class KrakenExchangeSpecificParser extends DefaultUnivocityExchangeSpecificParser implements IExchangeSpecificParser,
    IMultiExchangeSpecificParser<KrakenBeanV2> {

    public KrakenExchangeSpecificParser(Class<? extends ExchangeBean> exchangeBean, String delimiter) {
        super(exchangeBean, delimiter);
    }

    List<KrakenBeanV2> rows;
    List<KrakenBeanV2> unSupportedRows = new ArrayList<>();
    List<KrakenBeanV2> duplicities = new ArrayList<>();

    @Override
    public List<? extends ExchangeBean> convertMultipleRowsToTransactions(List<KrakenBeanV2> rows) {
        List<KrakenBeanV2> result;
        this.rows = setRowsWithIds(rows);
        var groupedRowsByRefId = createGroupsFromRows(rows);
        // clean groups of rows from unsupported rubbish
        var cleanUnsupportedGroups = removeGroupsWithUnsupportedRows(groupedRowsByRefId);
        var rowsWithoutDuplicities = removeDepositWithdrawalDuplicities(cleanUnsupportedGroups);

        // clean group of rows from duplicities
        // creating transaction
        List<KrakenBeanV2> rowsReadyForTxs = createTransactionFromGroupOfRows(rowsWithoutDuplicities);
        result = rowsReadyForTxs;
        unSupportedRows.stream().forEach(r -> {
            r.setRowNumber((long) r.getRowId());
        });
        duplicities.stream().forEach(r -> {
            r.setRowNumber((long) r.getRowId());
        });
        result.addAll(unSupportedRows);
        return rowsReadyForTxs;
    }

    @Override
    public Map<?, List<KrakenBeanV2>> removeGroupsWithUnsupportedRows(Map<?, List<KrakenBeanV2>> rowGroups) {
        Map<Object, List<KrakenBeanV2>> result = new HashMap<>();
        for (Map.Entry<?, List<KrakenBeanV2>> entry : rowGroups.entrySet()) {
            var rowsInGroup = entry.getValue();
            var isOneOrMoreUnsupportedRows =
                !rowsInGroup.stream().filter(r -> r.isUnsupportedRow() == true).collect(Collectors.toList()).isEmpty();
            if (!isOneOrMoreUnsupportedRows) {
                result.put(entry.getKey(), entry.getValue());
            } else {
                var ids = rowsInGroup.stream().map(r -> r.getRowId()).collect(Collectors.toList());
                var s = BinanceSortedGroupV4.parseIds(ids);
                setRowsAsUnsupported(rowsInGroup, "One or more rows in group " + "( rows: " + s + ") is unsupported");
            }
        }
        return result;
    }

    private Map<?, List<KrakenBeanV2>> removeDepositWithdrawalDuplicities(Map<?, List<KrakenBeanV2>> groupRows) {
        Map<Object, List<KrakenBeanV2>> result = new HashMap<>();
        for (Map.Entry<?, List<KrakenBeanV2>> entry : groupRows.entrySet()) {
            var values = entry.getValue();
            if (values.size() == 2 && KrakenSupportedTypes.DUPLICABLE_TYPES.contains(values.get(0).getType())) {
                var stRow = values.get(0);
                var ndRow = values.get(1);
                if ("".equals(stRow.getTxid()) || !"".equals(ndRow.getTxid())) {
                    stRow.setDuplicateLine();
                    duplicities.add(stRow);
                    result.put(entry.getKey(), List.of(ndRow));
                } else if ("".equals(ndRow.getTxid()) || !"".equals(stRow.getTxid())) {
                    ndRow.setDuplicateLine();
                    duplicities.add(ndRow);
                    result.put(entry.getKey(), List.of(stRow));
                } else {
                    result.put(entry.getKey(), values);
                }
            } else {
                result.put(entry.getKey(), values);
            }
        }
        return result;
    }

    private void setRowsAsUnsupported(List<KrakenBeanV2> rowsInGroup, String message) {
        rowsInGroup.forEach(r -> {
            r.setMessage(message);
            r.setUnsupportedRow(true);
        });
        unSupportedRows.addAll(rowsInGroup);
    }

    private List<KrakenBeanV2> setRowsWithIds(List<KrakenBeanV2> rows) {
        int i = 1;
        for (KrakenBeanV2 row : rows) {
            i++;
            row.setRowId(i);
            row.setMessage("Row id " + i);
        }
        return rows;
    }

    @Override
    public List<KrakenBeanV2> createTransactionFromGroupOfRows(Map<?, List<KrakenBeanV2>> groups) {
        List<KrakenBeanV2> result = new ArrayList<>();
        for (Map.Entry<?, List<KrakenBeanV2>> entry : groups.entrySet()) {
            var sortedGroup = new KrakenSortedGroup();
            sortedGroup.setRefId(entry.getKey());
            var rows = entry.getValue();
            try {
                sortedGroup.sortGroup(rows);
                result.add(sortedGroup.createdTransaction);
            } catch (DataValidationException e) {
                var eMess = e.getMessage();
                var ids = rows.stream().map(r -> r.getRowId()).collect(Collectors.toList());
                var s = BinanceSortedGroupV4.parseIds(ids);
                setRowsAsUnsupported(rows, "One or more rows in group " + "( rows: " + s + ") is unsupported;" + " " + eMess);
            }
        }
        return result;
    }

    @Override
    public Map<String,List<KrakenBeanV2>> createGroupsFromRows(List<KrakenBeanV2> rows) {
        return rows.stream().collect(groupingBy(KrakenBeanV2::getRefid));
    }
}
