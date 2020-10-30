package io.everytrade.server.plugin.impl.everytrade;

import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.trade.UserTrade;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

public class HuobiTestUtils {

    public static UserTrade createUserTrade(String orderId, Date date, CurrencyPair currencyPair) {
        return new UserTrade(
            Order.OrderType.ASK,
            BigDecimal.ONE,
            currencyPair,
            BigDecimal.ONE,
            date,
            "",
            orderId,
            BigDecimal.ONE,
            Currency.USD,
            ""
        );
    }

    public static int compareDesc(UserTrade tradeA, UserTrade tradeB) {
        return tradeB.getTimestamp().compareTo(tradeA.getTimestamp());
    }

    public static boolean checkAll(List<UserTrade> tradesA, List<UserTrade> tradesB) {
        if (tradesA.size() != tradesB.size()) {
            System.out.println("Different size.");
            return false;
        }

        for (int i = 0; i < tradesA.size(); i++) {
            if (!tradesA.get(i).getOrderId().equals(tradesB.get(i).getOrderId())) {
                System.out.println("Different orderId.");
                return false;
            }
        }
        return true;
    }
}
