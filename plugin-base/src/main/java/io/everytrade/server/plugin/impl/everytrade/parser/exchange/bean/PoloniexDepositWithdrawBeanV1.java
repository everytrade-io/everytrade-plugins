package io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean;

import com.univocity.parsers.annotations.Format;
import com.univocity.parsers.annotations.Parsed;
import io.everytrade.server.model.Currency;
import io.everytrade.server.model.TransactionType;
import io.everytrade.server.plugin.impl.everytrade.parser.exception.DataIgnoredException;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.kuCoin.BaseTransactionMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Date;

import static io.everytrade.server.model.TransactionType.DEPOSIT;
import static io.everytrade.server.model.TransactionType.WITHDRAWAL;

public class PoloniexDepositWithdrawBeanV1 extends BaseTransactionMapper {
    public static final String COMPLETED = "COMPLETED";

    private Instant f_created_at;
    private Currency currency;
    private BigDecimal f_amount;
    private String f_address;
    private String f_status;

    private BigDecimal f_feededucted;
    private String feeCurrency;

    @Parsed(field = {"f_created_at", "f_date"})
    @Format(formats = {"yyyy-MM-dd HH:mm:ss"}, options = {"locale=EN", "timezone=UTC"})
    public void setF_created_at(Date f_created_at) {
        this.f_created_at = f_created_at.toInstant();
    }

    @Parsed(field = "currency")
    public void setCurrency(String currency) {
        this.currency = Currency.fromCode(currency);
    }

    @Parsed(field = "f_amount")
    public void setF_amount(BigDecimal f_amount) {
        this.f_amount = f_amount;
    }

    @Parsed(field = "f_address")
    public void setF_address(String f_address) {
        this.f_address = f_address;
    }

    @Parsed(field = "f_feededucted")
    public void setF_feededucted(BigDecimal f_feededucted) {
        this.f_feededucted = f_feededucted;
        this.feeCurrency = String.valueOf(currency);
    }

    @Parsed(field = "f_status")
    public void setF_status(String f_status) {
        this.f_status = f_status;
        if (!COMPLETED.equals(f_status)) {
            throw new DataIgnoredException("Transaction status is not completed");
        }
    }


    @Override
    protected TransactionType findTransactionType() {
        return f_feededucted != null ? WITHDRAWAL : DEPOSIT;
    }

    @Override
    protected BaseClusterData mapData() {
        return BaseClusterData.builder()
            .transactionType(findTransactionType())
            .executed(f_created_at)
            .base(currency)
            .quote(currency)
            .volume(f_amount)
            .fee(feeCurrency)
            .feeAmount(f_feededucted)
            .address(f_address)
            .build();
    }
}
