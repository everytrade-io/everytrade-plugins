package io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean;

import com.univocity.parsers.annotations.Format;
import com.univocity.parsers.annotations.Parsed;
import io.everytrade.server.model.Currency;
import io.everytrade.server.model.CurrencyPair;
import io.everytrade.server.model.TransactionType;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.kuCoin.BaseTransactionMapper;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Date;

import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean.BybitEuSupport.parseSpotPair;
import static lombok.AccessLevel.PRIVATE;

/**
 * ByBit EU Spot Trade History export (one row per fill).
 *
 * Header:
 * Spot Pairs,Order Type,Direction,feeCoin,ExecFeeV2,Filled Value,Filled Price,Filled Quantity,Fees,Transaction ID,Order No.,
 * Timestamp (UTC)
 */
@FieldDefaults(level = PRIVATE)
public class BybitEuBeanV1 extends BaseTransactionMapper {

    Currency baseCurrency;
    Currency quoteCurrency;
    String direction;
    Currency feeCoin;
    BigDecimal execFee;
    BigDecimal filledPrice;
    BigDecimal filledQuantity;
    String transactionId;
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

    @Parsed(field = "Filled Price")
    public void setFilledPrice(BigDecimal value) {
        filledPrice = value;
    }

    @Parsed(field = "Filled Quantity")
    public void setFilledQuantity(BigDecimal value) {
        filledQuantity = value;
    }

    @Parsed(field = "Transaction ID")
    public void setTransactionId(String value) {
        transactionId = value;
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

    @Override
    protected BaseClusterData mapData() {
        return BaseClusterData.builder()
            .transactionType(findTransactionType())
            .uid(transactionId)
            .executed(timestamp)
            .base(baseCurrency)
            .quote(quoteCurrency)
            .volume(filledQuantity)
            .unitPrice(filledPrice)
            .fee(feeCoin == null ? null : feeCoin.code())
            .feeAmount(execFee)
            .build();
    }
}
