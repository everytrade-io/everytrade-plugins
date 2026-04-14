package io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean;

import com.univocity.parsers.annotations.Format;
import com.univocity.parsers.annotations.Parsed;
import io.everytrade.server.model.Currency;
import io.everytrade.server.model.TransactionType;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.kuCoin.BaseTransactionMapper;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Date;

@EqualsAndHashCode(callSuper = true)
@Data
public class HuobiBuySellBeanV1 extends BaseTransactionMapper {

    private String uid;
    private String symbol;
    private String deal_type;
    private BigDecimal price;
    private BigDecimal volume;
    private BigDecimal amount;
    private BigDecimal fee_amount;
    private String fee_currency;
    private Instant deal_time;

    private Currency baseCurrency;
    private Currency quoteCurrency;

    @Parsed(field = "uid")
    public void setUid(String uid) {
        this.uid = uid;
    }

    @Parsed(field = "symbol")
    public void setSymbol(String symbol) {
        final String[] values = symbol.split("/");
        baseCurrency = Currency.fromCode(values[0]);
        quoteCurrency = Currency.fromCode(values[1]);

    }

    @Parsed(field = "deal_type")
    public void setDeal_type(String deal_type) {
        this.deal_type = deal_type;
    }

    @Parsed(field = "price")
    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    @Parsed(field = "volume")
    public void setVolume(BigDecimal volume) {
        this.volume = volume;
    }

    @Parsed(field = "amount")
    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    @Parsed(field = "fee_amount")
    public void setFee_amount(BigDecimal fee_amount) {
        this.fee_amount = fee_amount;
    }

    @Parsed(field = "fee_currency")
    public void setFee_currency(String fee_currency) {
        this.fee_currency = fee_currency.toUpperCase();
    }

    @Parsed(field = "deal_time")
    @Format(formats = {"yyyy-MM-dd HH:mm:ss"}, options = {"locale=EN", "timezone=UTC"})
    public void setDeal_time(Date deal_time) {
        this.deal_time = deal_time.toInstant();
    }

    @Override
    protected TransactionType findTransactionType() {
        return switch (deal_type) {
            case "buy" -> TransactionType.BUY;
            case "sell" -> TransactionType.SELL;
            default -> throw new IllegalArgumentException("Unsupported transaction type " + deal_type);
        };
    }

    @Override
    protected BaseClusterData mapData() {
        return BaseClusterData.builder()
            .transactionType(findTransactionType())
            .uid(uid)
            .executed(deal_time)
            .base(baseCurrency)
            .quote(quoteCurrency)
            .unitPrice(price)
            .volume(volume)
            .fee(fee_currency)
            .feeAmount(fee_amount)
            .build();
    }
}
