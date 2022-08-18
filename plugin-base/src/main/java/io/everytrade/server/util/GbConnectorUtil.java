package io.everytrade.server.util;

import io.everytrade.server.parser.exchange.GbApiTransactionBean;
import io.everytrade.server.plugin.impl.everytrade.parser.exception.DataIgnoredException;

import java.math.BigDecimal;

public class GbConnectorUtil {
    public static void buySellValidator(GbApiTransactionBean bean) {
        if (bean.actionToTransactionType().isBuyOrSell()) {
            if(bean.getVolume().equals(BigDecimal.ZERO) || bean.getQuantity().equals(BigDecimal.ZERO)) {
                throw new DataIgnoredException("Volume or Quantity is zero. ");
            }
        }
    }
}
