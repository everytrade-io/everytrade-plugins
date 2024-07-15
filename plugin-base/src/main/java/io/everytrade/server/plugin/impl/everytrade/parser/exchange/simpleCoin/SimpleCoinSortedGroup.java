package io.everytrade.server.plugin.impl.everytrade.parser.exchange.simpleCoin;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static io.everytrade.server.model.TransactionType.BUY;
import static io.everytrade.server.model.TransactionType.DEPOSIT;
import static io.everytrade.server.model.TransactionType.SELL;
import static io.everytrade.server.model.TransactionType.WITHDRAWAL;

public class SimpleCoinSortedGroup {

    public static List<SimpleCoinBeanV2> createBuyTx(SimpleCoinBeanV2 row) {
        List<SimpleCoinBeanV2> result = new ArrayList<>();
        row.setTransactionType(BUY);
        row.setBaseAmount(row.getAmountTo().abs());
        row.setQuoteAmount(row.getAmountFrom().abs());
        row.setBaseCurrency(row.getCurrencyTo());
        row.setQuoteCurrency(row.getCurrencyFrom());

        return createWithdrawDepositBean(row, result);
    }

    public static List<SimpleCoinBeanV2> createSellTx(SimpleCoinBeanV2 row) {
        List<SimpleCoinBeanV2> result = new ArrayList<>();
        row.setTransactionType(SELL);
        row.setBaseAmount(row.getAmountFrom().abs());
        row.setQuoteAmount(row.getAmountTo().abs());
        row.setBaseCurrency(row.getCurrencyFrom());
        row.setQuoteCurrency(row.getCurrencyTo());

        return createWithdrawDepositBean(row, result);
    }

    private static List<SimpleCoinBeanV2> createWithdrawDepositBean(SimpleCoinBeanV2 row, List<SimpleCoinBeanV2> result) {
        try {
            SimpleCoinBeanV2 deposit = (SimpleCoinBeanV2) row.clone();
            deposit.setTransactionType(DEPOSIT);
            deposit.setDateDone(row.getFromTxDate() == null
                ? Date.from(row.getDateDone().minusSeconds(1))
                : Date.from(row.getFromTxDate()));
            deposit.setBaseAmount(row.getAmountFrom().abs());
            deposit.setBaseCurrency(row.getCurrencyFrom());
            deposit.setQuoteCurrency(row.getCurrencyFrom());

            SimpleCoinBeanV2 withdraw = (SimpleCoinBeanV2) row.clone();
            withdraw.setTransactionType(WITHDRAWAL);
            withdraw.setDateDone(row.getToTxDate() == null
                ? Date.from(row.getDateDone().plusSeconds(1))
                : Date.from(row.getToTxDate()));
            withdraw.setBaseAmount(row.getAmountTo().abs());
            withdraw.setBaseCurrency(row.getCurrencyTo());
            withdraw.setQuoteCurrency(row.getCurrencyTo());

            result.addAll(List.of(deposit, withdraw, row));
        } catch (CloneNotSupportedException e) {
            throw new RuntimeException(e);
        }
        return result;
    }
}
