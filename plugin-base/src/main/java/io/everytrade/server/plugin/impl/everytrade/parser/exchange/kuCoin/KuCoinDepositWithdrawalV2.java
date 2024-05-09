package io.everytrade.server.plugin.impl.everytrade.parser.exchange.kuCoin;

import com.univocity.parsers.annotations.Format;
import com.univocity.parsers.annotations.Parsed;
import com.univocity.parsers.common.DataValidationException;
import io.everytrade.server.model.Currency;
import io.everytrade.server.model.TransactionType;
import io.everytrade.server.plugin.impl.everytrade.parser.exception.DataIgnoredException;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean.BaseClusterData;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Date;

import static io.everytrade.server.model.TransactionType.DEPOSIT;
import static io.everytrade.server.model.TransactionType.WITHDRAWAL;

public class KuCoinDepositWithdrawalV2 extends BaseTransactionMapper {
    String orderId;
    Instant timeOfUpdate;
    String remarks;
    KuCoinStatus status;
    BigDecimal feeAmount;
    BigDecimal amount;
    Currency coin;
    Currency transferNetwork;

    @Parsed(field = "UID")
    public void setPair(String orderId) {
        this.orderId = orderId;
    }

    @Parsed(field = "Time(UTC+02:00)")
    @Format(formats = {"dd.MM.yyyy HH:mm:ss", "yyyy-MM-dd HH:mm:ss"}, options = {"locale=US", "timezone=UTC"})
    public void setTimeOfUpdate(Date date) {
        timeOfUpdate = date.toInstant();
    }

    @Parsed(field = "Remarks")
    public void setRemarks(String remarks) {
        this.remarks = remarks.toUpperCase();
    }

    @Parsed(field = "Amount")
    public void setAmount(String amount) {
        this.amount = new BigDecimal(amount);
    }

    @Parsed(field = "Fee")
    public void setFeeAmount(String fee) {
        this.feeAmount = new BigDecimal(fee);
    }

    @Parsed(field = "Coin")
    public void setCoin(String coin) {
        try {
            this.coin = Currency.fromCode(coin);
        } catch (Exception e) {
            throw new DataValidationException(UNSUPPORTED_CURRENCY_PAIR + coin);
        }
    }

    @Parsed(field = "Transfer Network")
    public void setTransferNetwork(String transferNetwork) {
        try {
            this.transferNetwork = Currency.fromCode(transferNetwork);
        } catch (Exception e) {
            throw new DataValidationException(UNSUPPORTED_CURRENCY_PAIR + transferNetwork);
        }
    }

    @Parsed(field = "Status")
    public void setStatus(String status) {
        try {
            this.status = KuCoinStatus.valueOf(status);
        } catch (Exception e) {
            throw new DataIgnoredException("Status is not success");
        }
    }

    @Override
    protected TransactionType findTransactionType() {
        return switch (remarks) {
            case "DEPOSIT" -> DEPOSIT;
            case "WITHDRAWAL" -> WITHDRAWAL;
            default -> throw new DataValidationException(String.format("Unsupported transaction type %s. ", remarks));
        };
    }

    @Override
    protected BaseClusterData mapData() {
        return BaseClusterData.builder()
            .transactionType(findTransactionType())
            .uid(orderId)
            .executed(timeOfUpdate)
            .base(coin)
            .quote(coin)
            .feeAmount(feeAmount)
            .fee(coin.code())
            .volume(amount)
            .build();
    }
}
