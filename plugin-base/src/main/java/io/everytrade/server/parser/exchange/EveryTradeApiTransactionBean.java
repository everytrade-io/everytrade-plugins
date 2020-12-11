package io.everytrade.server.parser.exchange;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.univocity.parsers.common.DataValidationException;
import io.everytrade.server.model.CurrencyPair;
import io.everytrade.server.model.TransactionType;
import io.everytrade.server.model.Currency;
import io.everytrade.server.plugin.api.parser.BuySellImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.FeeRebateImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.ImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.TransactionCluster;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.ExchangeBean.FEE_UID_PART;

@JsonFormat(shape = JsonFormat.Shape.ARRAY)
@JsonPropertyOrder({"uid", "timestamp", "base", "quote", "action", "quantity", "volume", "fee", "feeCurrency"})
public class EveryTradeApiTransactionBean {
    private String uid;
    private Instant timestamp;
    private String base;
    private String quote;
    private String action;
    private BigDecimal quantity;
    private BigDecimal volume;
    private BigDecimal fee;
    private String feeCurrency;

    public void setUid(String uid) {
        this.uid = uid;
    }

    public void setTimestamp(Date timestamp) {
        this.timestamp = timestamp.toInstant();
    }

    public void setBase(String symbol) {
        base = symbol;
    }

    public void setQuote(String symbol) {
        quote = symbol;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }

    public void setVolume(BigDecimal volume) {
        this.volume = volume;
    }

    public void setFee(BigDecimal fee) {
        this.fee = fee;
    }

    public void setFeeCurrency(String feeCurrency) {
        this.feeCurrency = feeCurrency;
    }

    public String getUid() {
        return uid;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public String getBase() {
        return base;
    }

    public String getQuote() {
        return quote;
    }

    public String getAction() {
        return action;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public BigDecimal getVolume() {
        return volume;
    }

    public BigDecimal getFee() {
        return fee;
    }

    public String getFeeCurrency() {
        return feeCurrency;
    }

    public TransactionCluster  toTransactionCluster() {
        final Currency base = Currency.valueOf(this.base);
        final Currency quote = Currency.valueOf(this.quote);
        try {
            new CurrencyPair(base, quote);
        } catch (CurrencyPair.FiatCryptoCombinationException e) {
            throw new DataValidationException(e.getMessage());
        }

        final Currency parsedFeeCurrency = Currency.valueOf(feeCurrency);
        final List<ImportedTransactionBean> related;
        final boolean ignoredFee;
        if (parsedFeeCurrency == base || parsedFeeCurrency == quote) {
            related = List.of(
                new FeeRebateImportedTransactionBean(
                    uid + FEE_UID_PART,
                    timestamp,
                    base,
                    quote,
                    TransactionType.FEE,
                    fee,
                    parsedFeeCurrency
                )
            );
            ignoredFee = false;
        } else {
            related = Collections.emptyList();
            ignoredFee = true;
        }

        return new TransactionCluster(
            new BuySellImportedTransactionBean(
                uid,
                timestamp,
                base,
                quote,
                TransactionType.valueOf(action),
                quantity,
                volume.divide(quantity, 10, RoundingMode.HALF_UP) //unit price
            ),
            related,
            ignoredFee ? 1 : 0
        );
    }

    @Override
    public String toString() {
        return "EveryTradeApiTransactionBean{" +
            "uid='" + uid + '\'' +
            ", timestamp=" + timestamp +
            ", base='" + base + '\'' +
            ", quote='" + quote + '\'' +
            ", action='" + action + '\'' +
            ", quantity=" + quantity +
            ", volume=" + volume +
            ", fee=" + fee +
            ", feeCurrency=" + feeCurrency +
            '}';
    }
}
