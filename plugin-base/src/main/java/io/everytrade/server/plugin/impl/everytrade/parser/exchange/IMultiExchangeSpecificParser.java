package io.everytrade.server.plugin.impl.everytrade.parser.exchange;

import java.util.List;
import java.util.Map;

public interface IMultiExchangeSpecificParser<T extends ExchangeBean> {

    List<? extends ExchangeBean> convertMultipleRowsToTransactions(List<T> rows);

    Map<?, List<T>> removeGroupsWithUnsupportedRows(Map<?, List<T>> rowGroups);

    List<T> createTransactionFromGroupOfRows(Map<?, List<T>> groups);
}
