package io.everytrade.server.plugin.impl.everytrade.parser.exchange.okx;

import java.math.BigDecimal;
import java.util.LinkedList;
import java.util.List;

import static io.everytrade.server.model.TransactionType.FEE;
import static io.everytrade.server.model.TransactionType.valueOf;

public class OkxSortedGroup {
    public List<OkxBeanV2> createdTransactions = new LinkedList<>();

    public void sortGroup(List<OkxBeanV2> group) {
        for (int i = 0; i < group.size(); i += 2) {
            if (i + 1 < group.size()) {
                OkxBeanV2 base = group.get(i);
                OkxBeanV2 quote = group.get(i + 1);
                List<OkxBeanV2> firstTwoElements = group.subList(i, i + 2);
                OkxBeanV2 fee = firstTwoElements.stream()
                    .filter(bean -> bean.getFee().compareTo(BigDecimal.ZERO) != 0)
                    .findFirst()
                    .orElse(null);

                try {
                    OkxBeanV2 result = (OkxBeanV2) base.clone();
                    result.setBaseAmount(base.getAmount());
                    result.setBaseCurrency(base.getFeeUnit());
                    result.setQuoteAmount(quote.getAmount());
                    result.setQuoteCurrency(quote.getFeeUnit());
                    result.setTransactionType(valueOf(base.getAction().toUpperCase()));

                    if (fee != null) {
                        fee.setTransactionType(FEE);
                        fee.setFeeAmount(fee.getFee());
                        fee.setFeeCurrency(fee.getFeeUnit());
                        result.getFeeTransactions().add(fee);
                    }
                    createdTransactions.add(result);
                } catch (CloneNotSupportedException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }
}
