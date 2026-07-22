package io.everytrade.server.plugin.impl.everytrade.parser.exchange;

import io.everytrade.server.plugin.impl.everytrade.parser.exchange.simplecoin.SimplecoinBeanV2;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.simplecoin.SimplecoinSortedGroup;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class SimplecoinExchangeSpecificParser extends DefaultUnivocityExchangeSpecificParser implements IExchangeSpecificParser,
    IMultiExchangeSpecificParser<SimplecoinBeanV2> {

    List<SimplecoinBeanV2> filteredRows = new LinkedList<>();
    List<SimplecoinBeanV2> unSupportedRows = new LinkedList<>();

    public SimplecoinExchangeSpecificParser(Class<? extends ExchangeBean> exchangeBean, String delimiter) {
        super(exchangeBean, delimiter);
    }

    @Override
    public List<? extends ExchangeBean> convertMultipleRowsToTransactions(List<SimplecoinBeanV2> rows) {
        List<SimplecoinBeanV2> result;
        filterRowsByType(rows);
        List<SimplecoinBeanV2> beans = prepareBeansForTransactions(filteredRows);
        result = beans;
        unSupportedRows.forEach(r -> r.setRowNumber(r.getRowId()));
        result.addAll(unSupportedRows);
        return beans;
    }

    private void filterRowsByType(List<SimplecoinBeanV2> rows) {
        rows.forEach(r -> {
            if (r.isUnsupportedRow()) {
                unSupportedRows.add(r);
            } else {
                filteredRows.add(r);
            }
        });
    }

    private List<SimplecoinBeanV2> prepareBeansForTransactions(List<SimplecoinBeanV2> rows) {
        List<SimplecoinBeanV2> result = new LinkedList<>();
        for (SimplecoinBeanV2 row : rows) {
            // Decide buy/sell from BOTH sides of the order (which side is fiat vs. crypto), not just "Currency From".
            final boolean fromFiat = row.getCurrencyFrom().isFiat();
            final boolean toFiat = row.getCurrencyTo().isFiat();
            if (fromFiat && !toFiat) {
                // fiat -> crypto: buying the received ("to") crypto
                result.addAll(SimplecoinSortedGroup.createBuyTx(row));
            } else if (!fromFiat && toFiat) {
                // crypto -> fiat: selling the given ("from") crypto
                result.addAll(SimplecoinSortedGroup.createSellTx(row));
            } else if (!fromFiat && !toFiat) {
                // crypto -> crypto: model as a disposal of the given ("from") asset for the received one
                result.addAll(SimplecoinSortedGroup.createSellTx(row));
            } else {
                // fiat -> fiat: not a crypto trade, cannot be represented as a buy/sell
                row.setUnsupportedRow(true);
                unSupportedRows.add(row);
            }
        }
        return result;
    }

    @Override
    public Map<?, List<SimplecoinBeanV2>> createGroupsFromRows(List<SimplecoinBeanV2> rows) {
        return null;
    }

    @Override
    public Map<?, List<SimplecoinBeanV2>> removeGroupsWithUnsupportedRows(Map<?, List<SimplecoinBeanV2>> rowGroups) {
        return null;
    }

    @Override
    public List<SimplecoinBeanV2> createTransactionFromGroupOfRows(Map<?, List<SimplecoinBeanV2>> groups) {
        return null;
    }
}
