package io.everytrade.server.plugin.impl.everytrade.parser.exchange.simplecoin;

import java.util.List;

import static io.everytrade.server.model.TransactionType.BUY;
import static io.everytrade.server.model.TransactionType.SELL;

/**
 * Each SimpleCoin order maps to exactly ONE trade transaction (BUY or SELL) decided by the fiat/crypto
 * combination of the order sides. No DEPOSIT/WITHDRAWAL legs are fabricated - SimpleCoin is a broker,
 * the order itself is the only taxable event.
 */
public class SimplecoinSortedGroup {

    public static List<SimplecoinBeanV2> createBuyTx(SimplecoinBeanV2 row) {
        row.setTransactionType(BUY);
        row.setBaseAmount(row.getAmountTo().abs());
        row.setQuoteAmount(row.getAmountFrom().abs());
        row.setBaseCurrency(row.getCurrencyTo());
        row.setQuoteCurrency(row.getCurrencyFrom());
        return List.of(row);
    }

    public static List<SimplecoinBeanV2> createSellTx(SimplecoinBeanV2 row) {
        row.setTransactionType(SELL);
        row.setBaseAmount(row.getAmountFrom().abs());
        row.setQuoteAmount(row.getAmountTo().abs());
        row.setBaseCurrency(row.getCurrencyFrom());
        row.setQuoteCurrency(row.getCurrencyTo());
        return List.of(row);
    }
}
