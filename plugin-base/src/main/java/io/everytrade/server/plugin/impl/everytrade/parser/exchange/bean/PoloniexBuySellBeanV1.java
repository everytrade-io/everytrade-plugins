package io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean;

import com.univocity.parsers.annotations.Format;
import com.univocity.parsers.annotations.Parsed;
import com.univocity.parsers.common.DataValidationException;
import io.everytrade.server.model.Currency;
import io.everytrade.server.model.TransactionType;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.kuCoin.BaseTransactionMapper;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Date;

import static io.everytrade.server.model.TransactionType.BUY;
import static io.everytrade.server.model.TransactionType.SELL;

@EqualsAndHashCode(callSuper = true)
@Data
public class PoloniexBuySellBeanV1 extends BaseTransactionMapper {

    private Instant create_time;
    private String trade_id;
    private String market;
    private String buyer_wallet;
    private String side;
    private BigDecimal price;
    private BigDecimal amount;
    private BigDecimal fee;
    private String fee_currency;
    private BigDecimal fee_total;

    private Currency baseCurrency;
    private Currency quoteCurrency;
    private TransactionType transactionType;

    @Parsed(field = "create_time")
    @Format(formats = {"yyyy-MM-dd HH:mm:ss"}, options = {"locale=EN", "timezone=UTC"})
    public void setCreate_time(Date create_time) {
        this.create_time = create_time.toInstant();
    }

    @Parsed(field = "trade_id")
    public void setTrade_id(String trade_id) {
        this.trade_id = trade_id;
    }

    @Parsed(field = "market")
    public void setMarket(String market) {
        this.market = market;
    }

    @Parsed(field = "buyer_wallet")
    public void setBuyer_wallet(String buyer_wallet) {
        this.buyer_wallet = buyer_wallet;
    }

    @Parsed(field = "side")
    public void setSide(String side) {
        this.side = side;
    }

    @Parsed(field = "price")
    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    @Parsed(field = "amount")
    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    @Parsed(field = "fee")
    public void setFee(BigDecimal fee) {
        this.fee = fee;
    }

    @Parsed(field = "fee_currency")
    public void setFee_currency(String fee_currency) {
        this.fee_currency = fee_currency;
    }

    @Parsed(field = "fee_total")
    public void setFee_total(BigDecimal fee_total) {
        this.fee_total = fee_total;
    }

    @Override
    protected TransactionType findTransactionType() {
        if (market.endsWith(fee_currency)) {
            String remaining = market.substring(0, market.length() - fee_currency.length());
            for (Currency currency : Currency.values()) {
                if (String.valueOf(currency).equals(remaining)) {
                    try {
                        baseCurrency = Currency.valueOf(fee_currency);
                        quoteCurrency = Currency.valueOf(remaining);
                    } catch (IllegalArgumentException e) {
                        throw new DataValidationException(String.format("Unsupported currency pair %s. ", market));
                    }
                    break;
                }
            }
        }
       return switch (side.toUpperCase()) {
            case "SELL" -> SELL;
            case "BUY" -> BUY;
            default -> throw new DataValidationException(String.format("Unsupported transaction type %s. ", side));
        };
    }

    @Override
    protected BaseClusterData mapData() {
        return BaseClusterData.builder()
            .transactionType(findTransactionType())
            .executed(create_time)
            .base(baseCurrency)
            .quote(quoteCurrency)
            .unitPrice(price)
            .volume(amount)
            .fee(fee_currency)
            .feeAmount(fee_total)
            .build();
    }
}
