package io.everytrade.server.plugin.impl.everytrade.parser.exchange.kuCoin;

import com.univocity.parsers.annotations.Format;
import com.univocity.parsers.annotations.Parsed;
import com.univocity.parsers.common.DataValidationException;
import io.everytrade.server.model.Currency;
import io.everytrade.server.model.TransactionType;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean.BaseClusterData;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Date;

public class KuCoinWithdrawalV1 extends BaseTransactionMapper {
    Instant time;
    Currency coin;
    BigDecimal amount;
    String type;
    String walletAddress;
    String remark;

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

    @Parsed(field = "Type")
    public void setType(String type) {
        this.type = type;
    }

    @Parsed(field = "Wallet Address")
    public void setWalletAddress(String walletAddress) {
        this.walletAddress = walletAddress;
    }

    @Parsed(field = "Remark")
    public void setRemark(String remark) {
        this.remark = remark;
    }

    @Override
    protected TransactionType findTransactionType() {
        return TransactionType.WITHDRAWAL;
    }

    @Override
    protected BaseClusterData mapData() {
        return BaseClusterData.builder()
            .transactionType(findTransactionType())
            .executed(time)
            .base(coin)
            .quote(coin)
            .volume(amount)
            .address(walletAddress)
            .build();
    }
}
