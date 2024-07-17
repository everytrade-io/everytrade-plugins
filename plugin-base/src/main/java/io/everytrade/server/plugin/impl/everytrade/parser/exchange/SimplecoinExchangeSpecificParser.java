package io.everytrade.server.plugin.impl.everytrade.parser.exchange;

import io.everytrade.server.plugin.impl.everytrade.parser.exchange.simpleCoin.SimpleCoinBeanV2;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.simpleCoin.SimpleCoinSortedGroup;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class SimpleCoinExchangeSpecificParser extends DefaultUnivocityExchangeSpecificParser implements IExchangeSpecificParser,
    IMultiExchangeSpecificParser<SimpleCoinBeanV2> {

    List<SimpleCoinBeanV2> filteredRows = new LinkedList<>();
    List<SimpleCoinBeanV2> unSupportedRows = new LinkedList<>();

    public SimpleCoinExchangeSpecificParser(Class<? extends ExchangeBean> exchangeBean, String delimiter) {
        super(exchangeBean, delimiter);
    }

    @Override
    public List<? extends ExchangeBean> convertMultipleRowsToTransactions(List<SimpleCoinBeanV2> rows) {
        List<SimpleCoinBeanV2> result;
        filterRowsByType(rows);
        List<SimpleCoinBeanV2> beans = prepareBeansForTransactions(filteredRows);
        result = beans;
        unSupportedRows.forEach(r -> r.setRowNumber(r.getRowId()));
        result.addAll(unSupportedRows);
        return beans;
    }

    private void filterRowsByType(List<SimpleCoinBeanV2> rows) {
        rows.forEach(r -> {
            if (r.isUnsupportedRow()) {
                unSupportedRows.add(r);
            } else {
                filteredRows.add(r);
            }
        });
    }

    private List<SimpleCoinBeanV2> prepareBeansForTransactions(List<SimpleCoinBeanV2> rows) {
        List<SimpleCoinBeanV2> result = new LinkedList<>();
        for (SimpleCoinBeanV2 row : rows) {
            if (row.getCurrencyFrom().isFiat()) {
                result.addAll(SimpleCoinSortedGroup.createBuyTx(row));
            } else {
                result.addAll(SimpleCoinSortedGroup.createSellTx(row));
            }
        }
        return result;
    }

    @Override
    public Map<?, List<SimpleCoinBeanV2>> createGroupsFromRows(List<SimpleCoinBeanV2> rows) {
        return null;
    }

    @Override
    public Map<?, List<SimpleCoinBeanV2>> removeGroupsWithUnsupportedRows(Map<?, List<SimpleCoinBeanV2>> rowGroups) {
        return null;
    }

    @Override
    public List<SimpleCoinBeanV2> createTransactionFromGroupOfRows(Map<?, List<SimpleCoinBeanV2>> groups) {
        return null;
    }
}
