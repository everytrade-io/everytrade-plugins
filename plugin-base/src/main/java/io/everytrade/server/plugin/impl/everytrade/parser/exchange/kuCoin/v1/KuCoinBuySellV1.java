package io.everytrade.server.plugin.impl.everytrade.parser.exchange.kuCoin.v1;

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

public class KuCoinBuySellV1 extends BaseTransactionMapper {
    Instant tradeCreatedAt;
    String orderId;
    CurrencyPair symbol;
    String side;
    BigDecimal price;
    BigDecimal size;
    String feeCurrency;
    BigDecimal feeValue;
    Currency baseCurrency;
    Currency quoteCurrency;

    @Parsed(field = "tradeCreatedAt")
    @Format(formats = {"dd.MM.yyyy HH:mm:ss", "yyyy-MM-dd HH:mm:ss"}, options = {"locale=US", "timezone=UTC"})
    public void setTradeCreatedAt(Date date) {
        tradeCreatedAt = date.toInstant();
    }

    @Parsed(field = "orderId")
    public void setPair(String orderId) {
        this.orderId = orderId;
    }

    @Parsed(field = "symbol")
    public void setSymbol(String symbol) {
        try {
            String[] symbolParts = parsePair(symbol);
            this.baseCurrency = Currency.fromCode(symbolParts[0]);
            this.quoteCurrency = Currency.fromCode(symbolParts[1]);
        } catch (Exception e) {
            throw new DataValidationException(UNSUPPORTED_CURRENCY_PAIR + symbol);
        }
    }

    @Parsed(field = "side")
    public void setSide(String side) {
        this.side = side;
    }

    @Parsed(field = "price")
    public void setPrice(String price) {
        this.price = setAmountFromString(price);
    }

    @Parsed(field = "size")
    public void setSize(String size) {
        this.size = setAmountFromString(size);
    }

    @Parsed(field = "fee")
    public void setFee(String fee) {
        this.feeValue = setAmountFromString(fee);
    }

    @Parsed(field = "feeCurrency")
    public void setFeeCurrency(String feeCurrency) {
        this.feeCurrency = feeCurrency;
    }

    private static String[] parsePair(String value) {
        return value.split("-");
    }

    @Override
    protected TransactionType findTransactionType() {
        return switch (side) {
            case "buy" -> TransactionType.BUY;
            case "sell" -> TransactionType.SELL;
            default -> throw new DataValidationException(String.format("Unsupported transaction type %s. ", side));
        };
    }

    @Override
    protected BaseClusterData mapData() {
        return BaseClusterData.builder()
            .transactionType(findTransactionType())
            .executed(tradeCreatedAt)
            .note(orderId)
            .base(baseCurrency)
            .quote(quoteCurrency)
            .unitPrice(price)
            .volume(size)
            .fee(feeCurrency)
            .feeAmount(feeValue)
            .build();
    }
}
