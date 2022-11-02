package io.everytrade.server.plugin.impl.everytrade.parser.exchange.kuCoin.v1;

import com.univocity.parsers.annotations.Format;
import com.univocity.parsers.annotations.Parsed;
import com.univocity.parsers.common.DataValidationException;
import io.everytrade.server.model.Currency;
import io.everytrade.server.model.TransactionType;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean.BaseClusterData;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean.BaseTransactionMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Date;

import static io.everytrade.server.model.TransactionType.DEPOSIT;

public class KuCoinDepositV1 extends BaseTransactionMapper {
    Instant time;
    Currency coin;
    BigDecimal amount;
    String remark;
    String type;

    @Parsed(index = 0)
    @Format(formats = {"yyyy-MM-dd HH:mm:ss"}, options = {"locale=US", "timezone=UTC"})
    public void setTime(Date date) {
        time = date.toInstant();
    }

    @Parsed(field = "Coin")
    public void setCoin(String coin) {
        try {
            this.coin = Currency.fromCode(coin);
        } catch (Exception e) {
            throw new DataValidationException(UNSUPPORTED_CURRENCY_PAIR + coin);
        }
    }

    @Parsed(field = "Amount")
    public void setAmount(String amount) {
        this.amount = setAmountFromString(amount);
    }

    @Parsed(field = "Remark")
    public void setRemark(String remark) {
        this.remark = remark;
    }

    @Parsed(field = "Type")
    public void setType(String type) {
        this.type = type;
    }

    @Override
    protected TransactionType findTransactionType() {
        return switch (remark) {
            case "Deposit" -> DEPOSIT;
            default -> throw new DataValidationException(String.format("Unsupported transaction type %s. ", remark));
        };
    }

    @Override
    protected BaseClusterData mapData() {
        return BaseClusterData.builder()
            .transactionType(findTransactionType())
            .executed(time)
            .base(coin)
            .quote(coin)
            .volume(amount)
            .note(type)
            .build();
    }
}
