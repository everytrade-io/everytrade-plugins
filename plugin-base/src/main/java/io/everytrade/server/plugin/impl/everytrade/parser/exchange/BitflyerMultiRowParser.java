package io.everytrade.server.plugin.impl.everytrade.parser.exchange;

import io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean.BitflyerBeanV2;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.coibasePro.v2.CoinbaseProSortedGroup;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.groupingBy;

public class BitflyerMultiRowParser extends DefaultUnivocityExchangeSpecificParser implements IExchangeSpecificParser,
    IMultiExchangeSpecificParser<BitflyerBeanV2> {

    public BitflyerMultiRowParser(Class<? extends ExchangeBean> exchangeBean, String delimiter) {
        super(exchangeBean, delimiter);
    }

    List<BitflyerBeanV2> rows;
    List<BitflyerBeanV2> unSupportedRows = new ArrayList<>();
    List<BitflyerBeanV2> failedRows = new ArrayList<>();

    @Override
    public List<? extends ExchangeBean> convertMultipleRowsToTransactions(List<BitflyerBeanV2> rows) {
        List<BitflyerBeanV2> result;
        this.rows = setRowsWithIds(rows);
        var groupedRowsByTradeId = createGroupsFromRows(rows);
        var cleanUnsupportedGroups = removeGroupsWithUnsupportedRows(groupedRowsByTradeId);
        // creating transaction
        List<BitflyerBeanV2> rowsReadyForTxs = createTransactionFromGroupOfRows(groupedRowsByTradeId);
        result = rowsReadyForTxs;
        unSupportedRows.stream().forEach(r -> {
            r.setRowNumber(r.getRowId());
        });
        result.addAll(unSupportedRows);
        return rowsReadyForTxs;
    }

    @Override
    public Map<String, List<BitflyerBeanV2>> createGroupsFromRows(List<BitflyerBeanV2> rows) {
        return rows.stream().collect(groupingBy( r -> {
            String orderId = r.getOrderID();
            if (orderId.endsWith("F")) {
                orderId = orderId.substring(0,orderId.length()-1);
            }
            return orderId;
        }));
    }

    @Override
    public Map<?, List<BitflyerBeanV2>> removeGroupsWithUnsupportedRows(Map<?, List<BitflyerBeanV2>> rowGroups) {
        return rowGroups;
    }

    private void setRowsAsUnsupported(List<BitflyerBeanV2> rowsInGroup, String message) {
        rowsInGroup.forEach(r -> {
            r.setMessage(message);
            r.setUnsupportedRow(true);
        });
        unSupportedRows.addAll(rowsInGroup);
    }

    private List<BitflyerBeanV2> setRowsWithIds(List<BitflyerBeanV2> rows) {
        int i = 1;
        for (BitflyerBeanV2 row : rows) {
            i++;
            row.setRowId(i);
            row.setMessage("Row id " + i);
        }
        return rows;
    }

    @Override
    public List<BitflyerBeanV2> createTransactionFromGroupOfRows(Map<?, List<BitflyerBeanV2>> groups) {
        List<BitflyerBeanV2> result = new ArrayList<>();
        for (Map.Entry<?, List<BitflyerBeanV2>> entry : groups.entrySet()) {
            var rows = entry.getValue();
            if(rows.size() != 2) {
                result.addAll(rows);
            } else {
                try {
                    var tx = makeTxsFromTwoRows(rows);
                    result.addAll(tx);
                } catch (Exception e) {
                    var eMess = e.getMessage();
                    var ids = rows.stream().map(r -> r.getRowId()).collect(Collectors.toList());
                    var s = CoinbaseProSortedGroup.parseIds(ids);
                    setRowsAsUnsupported(rows, String.format("One or more rows in group (rows: %s) is unsupported; %s", ids, eMess));
                }
            }
        }
        return result;
    }

    private List<BitflyerBeanV2> makeTxsFromTwoRows(List<BitflyerBeanV2> rows) {
        List<BitflyerBeanV2> res = new ArrayList<>();
        if(rows.stream().filter(r -> r.getOrderID().endsWith("F")).count() == 1) {
            var rowFee = rows.get(0).getOrderID().endsWith("F") ? rows.get(0) : rows.get(1);
            var row = rows.get(0).equals(rowFee) ? rows.get(1) : rows.get(0);
            row.setFee(rowFee.getAmountCurrency1());
            row.setFeeCurrency(rowFee.getCurrency1());
            res.add(row);
            return res;
        } else {
            return rows;
        }
    }

}
