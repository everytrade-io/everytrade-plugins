package io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean;

import com.univocity.parsers.annotations.Parsed;
import io.everytrade.server.model.Currency;
import io.everytrade.server.model.TransactionType;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.kuCoin.BaseTransactionMapper;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.Instant;

import static io.everytrade.server.model.TransactionType.BUY;
import static io.everytrade.server.model.TransactionType.SELL;

@EqualsAndHashCode(callSuper = true)
@Data
public class BitcoinRdBeanV1 extends BaseTransactionMapper {

    private String side;
    private BigDecimal size;
    private BigDecimal price;
    private Instant timestamp;
    private String symbol;
    private String order_id;
    private BigDecimal fee;
    private String fee_coin;

    private Currency baseCurrency;
    private Currency quoteCurrency;
    private TransactionType transactionType;

    @Parsed(field = "side")
    public void setSide(String side) {
        this.side = side;
    }

    @Parsed(field = "size")
    public void setSize(BigDecimal size) {
        this.size = size;
    }

    @Parsed(field = "price")
    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    @Parsed(field = "timestamp")
    public void setTimestamp(String timestamp) {
        this.timestamp = Instant.parse(timestamp);
    }

    @Parsed(field = "symbol")
    public void setSymbol(String symbol) {
        final String[] values = symbol.split("-");
        baseCurrency = Currency.fromCode(values[0].toUpperCase());
        quoteCurrency = Currency.fromCode(values[1].toUpperCase());
    }

    @Parsed(field = "order_id")
    public void setOrder_id(String order_id) {
        this.order_id = order_id;
    }

    @Parsed(field = "fee")
    public void setFee(BigDecimal fee) {
        this.fee = fee;
    }

    @Parsed(field = "fee_coin")
    public void setFee_coin(String fee_coin) {
        this.fee_coin = fee_coin.toUpperCase();
    }

    @Override
    protected TransactionType findTransactionType() {
        return switch (side) {
            case "buy" -> BUY;
            case "sell" -> SELL;
            default -> throw new IllegalArgumentException("Unsupported side: " + side);
        };
    }

    @Override
    protected BaseClusterData mapData() {
        return BaseClusterData.builder()
            .transactionType(findTransactionType())
            .uid(order_id)
            .executed(timestamp)
            .base(baseCurrency)
            .quote(quoteCurrency)
            .unitPrice(price)
            .volume(size)
            .fee(fee_coin)
            .feeAmount(fee)
            .build();
    }
}
