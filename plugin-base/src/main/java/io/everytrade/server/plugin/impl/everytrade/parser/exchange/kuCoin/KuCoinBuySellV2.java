package io.everytrade.server.plugin.impl.everytrade.parser.exchange.kuCoin;

import com.univocity.parsers.annotations.Format;
import com.univocity.parsers.annotations.Parsed;
import com.univocity.parsers.common.DataValidationException;
import io.everytrade.server.model.Currency;
import io.everytrade.server.model.CurrencyPair;
import io.everytrade.server.model.TransactionType;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean.BaseClusterData;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Date;

import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.kuCoin.KuCoinCurrencySwitcher.SWITCHER;

public class KuCoinBuySellV2 extends BaseTransactionMapper {

    String orderId;
    CurrencyPair symbol;
    String side;
    BigDecimal avgFilledPrice;
    BigDecimal filledAmount;
    BigDecimal filledVolume;
    Instant filledTime;
    String feeCurrency;
    BigDecimal feeValue;
    Currency baseCurrency;
    Currency quoteCurrency;


    @Parsed(field = "Filled Time(UTC+02:00)")
    @Format(formats = {"dd.MM.yyyy HH:mm:ss", "yyyy-MM-dd HH:mm:ss"}, options = {"locale=US", "timezone=UTC"})
    public void setFilledTime(Date date) {
        filledTime = date.toInstant();
    }

    @Parsed(field = "Order ID")
    public void setPair(String orderId) {
        this.orderId = orderId;
    }

    @Parsed(field = "Symbol")
    public void setSymbol(String symbol) {
        try {
            String[] symbolParts = parsePair(symbol);
            this.baseCurrency = SWITCHER.containsKey(symbolParts[0]) ? SWITCHER.get(symbolParts[0]) : Currency.fromCode(symbolParts[0]);
            this.quoteCurrency = SWITCHER.containsKey(symbolParts[1]) ? SWITCHER.get(symbolParts[1]) : Currency.fromCode(symbolParts[1]);
        } catch (Exception e) {
            throw new DataValidationException(UNSUPPORTED_CURRENCY_PAIR + symbol);
        }
    }

    @Parsed(field = "Side")
    public void setSide(String side) {
        this.side = side;
    }

    @Parsed(field = "Avg. Filled Price")
    public void setAvgFilledPrice(BigDecimal avgFilledPrice) {
        this.avgFilledPrice = avgFilledPrice;
    }

    @Parsed(field = "Filled Amount")
    public void setFilledAmount(BigDecimal filledAmount) {
        this.filledAmount = filledAmount;
    }

    @Parsed(field = "Filled Volume")
    public void setFilledVolume(BigDecimal filledVolume) {
        this.filledVolume = filledVolume;
    }

    @Parsed(field = "Fee")
    public void setFee(BigDecimal feeValue) {
        this.feeValue = feeValue;
    }

    @Parsed(field = "Fee Currency")
    public void setFeeCurrency(String feeCurrency) {
        this.feeCurrency = feeCurrency;
    }

    private static String[] parsePair(String value) {
        return value.split("-");
    }

    @Override
    protected TransactionType findTransactionType() {
        return switch (side) {
            case "BUY" -> TransactionType.BUY;
            case "SELL" -> TransactionType.SELL;
            default -> throw new DataValidationException(String.format("Unsupported transaction type %s. ", side));
        };
    }

    @Override
    protected BaseClusterData mapData() {
        return BaseClusterData.builder()
            .transactionType(findTransactionType())
            .executed(filledTime)
            .note(orderId)
            .base(baseCurrency)
            .quote(quoteCurrency)
            .unitPrice(avgFilledPrice)
            .volume(filledAmount)
            .fee(feeCurrency)
            .feeAmount(feeValue)
            .build();
    }
}
