package io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean;

import com.univocity.parsers.annotations.Format;
import com.univocity.parsers.annotations.Parsed;
import com.univocity.parsers.common.DataValidationException;
import io.everytrade.server.model.Currency;
import io.everytrade.server.model.CurrencyPair;
import io.everytrade.server.model.TransactionType;
import io.everytrade.server.plugin.impl.everytrade.parser.exception.DataIgnoredException;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.kuCoin.BaseTransactionMapper;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Date;

import static io.everytrade.server.plugin.impl.everytrade.parser.ParserUtils.DECIMAL_DIGITS;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean.BybitEuSupport.parseSpotPair;
import static java.math.RoundingMode.HALF_UP;
import static lombok.AccessLevel.PRIVATE;

/**
 * ByBit EU Spot Order History export (one row per order).
 *
 * Header:
 * Spot Pairs,feeCoin,ExecFeeV2,feeInfo,Order Type,Direction,Filled Value,Avg. Filled Price,Order Price,Order Quantity,
 * Order Value,Order Status,Order No.,Timestamp (UTC)
 *
 * Market BUY orders are specified by quote value ("Order Value"), so "Order Quantity" is empty ("--") — the base
 * quantity is then derived as Filled Value / Avg. Filled Price. FILLED orders are imported in full; orders with any
 * other status but a non-empty executed part (e.g. partially filled and then cancelled) are imported with the
 * EXECUTED quantity (derived from Filled Value / Avg. Filled Price, never from the ordered quantity); orders with
 * no executed part are ignored.
 *
 * NOTE: Trade History (BybitEuBeanV1) covers the same trades with per-fill granularity and is the recommended
 * export — do not import both files into the same container, the transactions would be duplicated.
 */
@FieldDefaults(level = PRIVATE)
public class BybitEuBeanV2 extends BaseTransactionMapper {

    private static final String ORDER_STATUS_FILLED = "FILLED";

    Currency baseCurrency;
    Currency quoteCurrency;
    String direction;
    Currency feeCoin;
    BigDecimal execFee;
    BigDecimal filledValue;
    BigDecimal avgFilledPrice;
    BigDecimal orderQuantity;
    String orderStatus;
    String orderNo;
    Instant timestamp;

    @Parsed(field = "Spot Pairs")
    public void setSpotPairs(String value) {
        final CurrencyPair currencyPair = parseSpotPair(value);
        baseCurrency = currencyPair.getBase();
        quoteCurrency = currencyPair.getQuote();
    }

    @Parsed(field = "Direction")
    public void setDirection(String value) {
        direction = value;
    }

    @Parsed(field = "feeCoin")
    public void setFeeCoin(String value) {
        if (value != null && !value.isBlank() && !"--".equals(value)) {
            feeCoin = Currency.fromCode(value);
        }
    }

    @Parsed(field = "ExecFeeV2")
    public void setExecFee(String value) {
        if (value != null && !value.isBlank() && !"--".equals(value)) {
            execFee = new BigDecimal(value);
        }
    }

    @Parsed(field = "Filled Value")
    public void setFilledValue(String value) {
        if (value != null && !value.isBlank() && !"--".equals(value)) {
            filledValue = new BigDecimal(value);
        }
    }

    @Parsed(field = "Avg. Filled Price")
    public void setAvgFilledPrice(String value) {
        if (value != null && !value.isBlank() && !"--".equals(value)) {
            avgFilledPrice = new BigDecimal(value);
        }
    }

    @Parsed(field = "Order Quantity")
    public void setOrderQuantity(String value) {
        if (value != null && !value.isBlank() && !"--".equals(value)) {
            orderQuantity = new BigDecimal(value);
        }
    }

    @Parsed(field = "Order Status")
    public void setOrderStatus(String value) {
        orderStatus = value;
    }

    @Parsed(field = "Order No.")
    public void setOrderNo(String value) {
        orderNo = value;
    }

    @Parsed(field = "Timestamp (UTC)")
    @Format(formats = {"HH:mm yyyy-MM-dd"}, options = {"locale=EN", "timezone=UTC"})
    public void setTimestamp(Date value) {
        timestamp = value.toInstant();
    }

    @Override
    protected TransactionType findTransactionType() {
        return detectTransactionType(direction);
    }

    private boolean hasExecutedPart() {
        return filledValue != null && filledValue.signum() > 0 && avgFilledPrice != null && avgFilledPrice.signum() > 0;
    }

    private BigDecimal deriveExecutedQuantity() {
        if (!hasExecutedPart()) {
            throw new DataValidationException("Cannot derive filled quantity - missing Filled Value or Avg. Filled Price");
        }
        return filledValue.divide(avgFilledPrice, DECIMAL_DIGITS, HALF_UP);
    }

    private BigDecimal evalVolume(boolean fullyFilled) {
        // "Order Quantity" is the ORDERED amount - it only equals the executed amount for fully FILLED orders
        // (and is empty for market BUYs, which are specified by quote value)
        if (fullyFilled && orderQuantity != null) {
            return orderQuantity;
        }
        return deriveExecutedQuantity();
    }

    @Override
    protected BaseClusterData mapData() {
        final boolean fullyFilled = ORDER_STATUS_FILLED.equalsIgnoreCase(orderStatus);
        // import the executed part of partially filled (e.g. later cancelled) orders; ignore rows with no fills
        if (!fullyFilled && !hasExecutedPart()) {
            throw new DataIgnoredException(String.format("Order status %s ignored - no executed part.", orderStatus));
        }
        return BaseClusterData.builder()
            .transactionType(findTransactionType())
            .uid(orderNo)
            .executed(timestamp)
            .base(baseCurrency)
            .quote(quoteCurrency)
            .volume(evalVolume(fullyFilled))
            .unitPrice(avgFilledPrice)
            .fee(feeCoin == null ? null : feeCoin.code())
            .feeAmount(execFee)
            .build();
    }
}
