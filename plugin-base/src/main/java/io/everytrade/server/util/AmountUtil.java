package io.everytrade.server.util;

import java.math.BigDecimal;

import static io.everytrade.server.plugin.impl.everytrade.parser.ParserUtils.DECIMAL_DIGITS;
import static java.math.RoundingMode.HALF_UP;

public class AmountUtil {
    public static BigDecimal evaluateBaseAmount(BigDecimal quoteAmount, BigDecimal unitPrice) {
        return quoteAmount.divide(unitPrice, DECIMAL_DIGITS, HALF_UP);
    }
}
