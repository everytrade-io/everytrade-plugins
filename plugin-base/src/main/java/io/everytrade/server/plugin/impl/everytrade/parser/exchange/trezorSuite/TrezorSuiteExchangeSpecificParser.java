package io.everytrade.server.plugin.impl.everytrade.parser.exchange.trezorSuite;

import io.everytrade.server.plugin.impl.everytrade.parser.exchange.DefaultUnivocityExchangeSpecificParser;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.ExchangeBean;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.IExchangeSpecificParser;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.IMultiExchangeSpecificParser;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class TrezorSuiteExchangeSpecificParser extends DefaultUnivocityExchangeSpecificParser implements IExchangeSpecificParser,
    IMultiExchangeSpecificParser<TrezorSuiteBeanV1> {

    public TrezorSuiteExchangeSpecificParser(Class<? extends ExchangeBean> exchangeBean, String delimiter) {
        super(exchangeBean, delimiter);
    }

    List<TrezorSuiteBeanV1> unSupportedRows = new LinkedList<>();

    @Override
    public List<? extends ExchangeBean> convertMultipleRowsToTransactions(List<TrezorSuiteBeanV1> rows) {
        List<TrezorSuiteBeanV1> result;
        List<TrezorSuiteBeanV1> beans = prepareBeansForTransactions(rows);
        result = beans;
        unSupportedRows.forEach(r -> r.setRowNumber(r.getRowId()));
        result.addAll(unSupportedRows);
        return beans;
    }

    private List<TrezorSuiteBeanV1> prepareBeansForTransactions(List<TrezorSuiteBeanV1> rows) {
        List<TrezorSuiteBeanV1> result = new LinkedList<>();
        for (TrezorSuiteBeanV1 row : rows) {
            switch (row.getType().toUpperCase()) {
                case "SENT" -> result.add(TrezorSuiteSortedGroup.createWithdrawalTx(row));
                case "RECV" -> result.add(TrezorSuiteSortedGroup.createDepositTx(row));
                case "SELF" -> result.addAll(TrezorSuiteSortedGroup.createTransferInOut(row));
            }
        }
        return result;
    }


    @Override
    public Map<?, List<TrezorSuiteBeanV1>> createGroupsFromRows(List<TrezorSuiteBeanV1> rows) {
        return null;
    }

    @Override
    public Map<?, List<TrezorSuiteBeanV1>> removeGroupsWithUnsupportedRows(Map<?, List<TrezorSuiteBeanV1>> rowGroups) {
        return null;
    }

    @Override
    public List<TrezorSuiteBeanV1> createTransactionFromGroupOfRows(Map<?, List<TrezorSuiteBeanV1>> groups) {
        return null;
    }
}
