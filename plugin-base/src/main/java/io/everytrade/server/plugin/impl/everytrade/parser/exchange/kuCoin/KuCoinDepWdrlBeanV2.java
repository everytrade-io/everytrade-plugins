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

public class KuCoinDepWdrlBeanV2 extends BaseTransactionMapper {
    String orderId;
    Instant timeOfUpdate;
    String remarks;
    KuCoinStatus status;
    BigDecimal feeAmount;
    BigDecimal amount;
    Currency coin;
    Currency transferNetwork;
    boolean hasWithdrawalAddress;

    @Parsed(field = "UID")
    public void setPair(String orderId) {
        this.orderId = orderId;
    }

    // The export header names a fixed offset; the wall time in the column is local time at exactly that offset.
    @Parsed(field = "Time(UTC+02:00)")
    @Format(formats = {"dd.MM.yyyy HH:mm:ss", "yyyy-MM-dd HH:mm:ss"}, options = {"locale=US", "timezone=GMT+02:00"})
    public void setTimeUtcPlus2(Date date) {
        timeOfUpdate = date.toInstant();
    }

    @Parsed(field = "Time(UTC+08:00)")
    @Format(formats = {"dd.MM.yyyy HH:mm:ss", "yyyy-MM-dd HH:mm:ss"}, options = {"locale=US", "timezone=GMT+08:00"})
    public void setTimeUtcPlus8(Date date) {
        timeOfUpdate = date.toInstant();
    }

    @Parsed(field = {"Remarks", "Side"})
    public void setRemarks(String remarks) {
        if (remarks != null) {
            this.remarks = remarks.toUpperCase();
        }
    }

    @Parsed(field = "Withdrawal Address/Account")
    public void setWithdrawalAddress(String address) {
        hasWithdrawalAddress = true;
    }

    @Parsed(field = "Amount")
    public void setAmount(String amount) {
        this.amount = new BigDecimal(amount);
    }

    @Parsed(field = "Fee")
    public void setFeeAmount(String fee) {
        this.feeAmount = new BigDecimal(fee);
    }

    @Parsed(field = {"Coin", "Currency"})
    public void setCoin(String coin) {
        try {
            this.coin = Currency.fromCode(coin);
        } catch (Exception e) {
            throw new DataValidationException(UNSUPPORTED_CURRENCY_PAIR + coin);
        }
    }

    @Parsed(field = "Transfer Network")
    public void setTransferNetwork(String transferNetwork) {
        if (transferNetwork == null) {
            return;
        }
        try {
            this.transferNetwork = Currency.fromCode(transferNetwork);
        } catch (Exception ignored) {
        }
    }

    @Parsed(field = "Status")
    public void setStatus(String status) {
        if (status == null) {
            return;
        }
        try {
            this.status = KuCoinStatus.valueOf(status);
        } catch (Exception e) {
            throw new DataIgnoredException("Status is not success");
        }
    }

    @Override
    protected TransactionType findTransactionType() {
        if (remarks != null && !remarks.isEmpty()) {
            return switch (remarks) {
                case "DEPOSIT" -> DEPOSIT;
                case "WITHDRAWAL" -> WITHDRAWAL;
                default -> throw new DataValidationException(String.format("Unsupported transaction type %s. ", remarks));
            };
        }
        if (hasWithdrawalAddress) {
            return WITHDRAWAL;
        }
        throw new DataValidationException("Cannot determine transaction type from remarks or columns.");
    }

    @Override
    protected BaseClusterData mapData() {
        if (timeOfUpdate == null) {
            // e.g. a "Time(UTC+hh:mm)" column with an offset that has no setter above
            throw new DataValidationException("Missing transaction time or unsupported time column offset.");
        }
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
