package io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean;

import com.univocity.parsers.annotations.Format;
import com.univocity.parsers.annotations.Parsed;
import io.everytrade.server.model.Currency;
import io.everytrade.server.model.TransactionType;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.kuCoin.BaseTransactionMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Date;

import static io.everytrade.server.model.TransactionType.DEPOSIT;
import static io.everytrade.server.model.TransactionType.WITHDRAWAL;

public class HuobiDepWdrlBeanV1 extends BaseTransactionMapper {

    private String uid;
    private Currency currency;
    private BigDecimal amount;
    private Instant time;

    private BigDecimal fee;

    @Parsed(field = "uid")
    public void setUid(String uid) {
        this.uid = uid;
    }

    @Parsed(field = "currency")
    public void setCurrency(String currency) {
        this.currency = Currency.fromCode(currency.toUpperCase());
    }


    @Parsed(field = "amount")
    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    @Parsed(field = {"deposit_time", "withdraw_time"})
    @Format(formats = {"yyyy-MM-dd HH:mm:ss"}, options = {"locale=EN", "timezone=UTC"})
    public void setTime(Date time) {
        this.time = time.toInstant();
    }

    @Parsed(field = "fee")
    public void setFee(BigDecimal fee) {
        this.fee = fee;
    }

    @Override
    protected TransactionType findTransactionType() {
        if (fee != null) {
            return WITHDRAWAL;
        } else {
            return DEPOSIT;
        }
    }

    @Override
    protected BaseClusterData mapData() {
        return BaseClusterData.builder()
            .transactionType(findTransactionType())
            .uid(uid)
            .executed(time)
            .base(currency)
            .quote(currency)
            .volume(amount)
            .fee(currency.code())
            .feeAmount(fee)
            .build();
    }
}
