package io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean;

import com.univocity.parsers.annotations.Parsed;
import io.everytrade.server.model.Currency;
import io.everytrade.server.model.TransactionType;
import io.everytrade.server.plugin.impl.everytrade.parser.exception.DataIgnoredException;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.kuCoin.BaseTransactionMapper;

import java.math.BigDecimal;
import java.time.Instant;

import static io.everytrade.server.model.TransactionType.DEPOSIT;
import static io.everytrade.server.model.TransactionType.WITHDRAWAL;

public class BitcoinRdDepWdrlBeanV1 extends BaseTransactionMapper {

    private static final String STATUS_TRUE = "true";

    private Currency currency;
    private String address;
    private BigDecimal amount;
    private String transaction_id;
    private String type;
    private String fee_coin;
    private BigDecimal fee;
    private String status;
    private Instant updated_at;

    @Parsed(field = "currency")
    public void setCurrency(String currency) {
        this.currency = Currency.fromCode(currency.toUpperCase());
    }

    @Parsed(field = "address")
    public void setAddress(String address) {
        this.address = address;
    }

    @Parsed(field = "amount")
    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    @Parsed(field = "transaction_id")
    public void setTransaction_id(String transaction_id) {
        this.transaction_id = transaction_id;
    }

    @Parsed(field = "type")
    public void setType(String type) {
        this.type = type;
    }

    @Parsed(field = "fee_coin")
    public void setFee_coin(String fee_coin) {
        this.fee_coin = fee_coin.toUpperCase();
    }

    @Parsed(field = "fee")
    public void setFee(BigDecimal fee) {
        this.fee = fee;
    }

    @Parsed(field = "status")
    public void setStatus(String status) {
        if (!status.equals(STATUS_TRUE)) {
            throw new DataIgnoredException("Ignored transaction due to status: " + status);
        }
        this.status = status;
    }

    @Parsed(field = "updated_at")
    public void setUpdated_at(String updated_at) {
        this.updated_at = Instant.parse(updated_at);
    }

    @Override
    protected TransactionType findTransactionType() {
        return switch (type) {
            case "deposit" -> DEPOSIT;
            case "withdrawal" -> WITHDRAWAL;
            default -> throw new IllegalArgumentException("Unsupported side: " + type);
        };
    }

    @Override
    protected BaseClusterData mapData() {
        return BaseClusterData.builder()
            .transactionType(findTransactionType())
            .uid(transaction_id)
            .executed(updated_at)
            .base(currency)
            .quote(currency)
            .volume(amount)
            .fee(fee_coin)
            .feeAmount(fee)
            .build();
    }
}
