package io.everytrade.server.plugin.impl.everytrade.parser.exchange.anycoin;

import io.everytrade.server.model.TransactionType;

import java.util.ArrayList;
import java.util.List;

import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.anycoin.AnycoinOperationTypeV1.OPERATION_TYPE_TRADE_PAYMENT;

public class AnycoinSortedGroupV1 {

    public List<AnycoinBeanV1> createdTransactions = new ArrayList<>();
    private AnycoinBeanV1 tradePayment;
    private AnycoinBeanV1 tradeFill;


    public void sortGroup(List<AnycoinBeanV1> group) {

        AnycoinBeanV1 newBean = new AnycoinBeanV1();

        for (AnycoinBeanV1 bean : group) {
            if (bean.getOperationType().equals(OPERATION_TYPE_TRADE_PAYMENT)) {
                tradePayment = bean;
            }
            if (bean.getOperationType().equals(AnycoinOperationTypeV1.OPERATION_TYPE_TRADE_FILL)) {
                tradeFill = bean;
            }
        }

        if (tradePayment.getCoin().isFiat()){
            newBean.setDate(tradeFill.getDate().toString());
            newBean.setMarketBase(tradeFill.getCoin());
            newBean.setBaseAmount(tradeFill.getAmount());
            newBean.setMarketQuote(tradePayment.getCoin());
            newBean.setQuoteAmount(tradePayment.getAmount());
            newBean.setOrderId(tradePayment.getOrderId());
            newBean.setTransactionType(TransactionType.BUY);
            newBean.setType("BUY");
            newBean.setOperationType(tradePayment.getOperationType());
        } else {
            newBean.setDate(tradeFill.getDate().toString());
            newBean.setMarketBase(tradePayment.getCoin());
            newBean.setBaseAmount(tradePayment.getAmount());
            newBean.setMarketQuote(tradeFill.getCoin());
            newBean.setQuoteAmount(tradeFill.getAmount());
            newBean.setOrderId(tradePayment.getOrderId());
            newBean.setTransactionType(TransactionType.SELL);
            newBean.setType("SELL");
            newBean.setOperationType(tradePayment.getOperationType());
        }
        createdTransactions.add(newBean);
    }



}
