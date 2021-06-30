package io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean;

import com.univocity.parsers.annotations.Format;
import com.univocity.parsers.annotations.Headers;
import com.univocity.parsers.annotations.Parsed;
import com.univocity.parsers.common.DataValidationException;
import io.everytrade.server.model.Currency;
import io.everytrade.server.model.TransactionType;
import io.everytrade.server.plugin.api.parser.BuySellImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.FeeRebateImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.ImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.TransactionCluster;
import io.everytrade.server.plugin.impl.everytrade.parser.ParserUtils;
import io.everytrade.server.plugin.impl.everytrade.parser.exception.DataIgnoredException;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.ExchangeBean;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Collections;
import java.util.Date;
import java.util.List;

@Headers(sequence = {"ID", "Date", "Type", "Amount", "Amount Currency", "Price", "Price Currency", "Fee",
    "Fee Currency", "Status"}, extract = true)
public class CoinmateBeanV1 extends ExchangeBean {
    // auxiliary field for validation
    private Currency auxFeeCurrency;
    private String id;
    private Instant date;
    private TransactionType type;
    private BigDecimal amount;
    private Currency amountCurrency;
    private BigDecimal price;
    private Currency priceCurrency;
    private BigDecimal fee;

    @Parsed(field = "ID")
    public void setId(String id) {
        this.id = id;
    }

    @Parsed(field = "Date")
    @Format(formats = {"yyyy-MM-dd HH:mm:ss", "dd.MM.yyyy HH:mm"}, options = {"locale=US", "timezone=UTC"})
    public void setDate(Date date) {
        this.date = date.toInstant();
    }

    @Parsed(field = "Type")
    public void setType(String type) {
        if ("BUY".equals(type) || "QUICK_BUY".equals(type)) {
            this.type = TransactionType.BUY;
        } else if ("SELL".equals(type) || "QUICK_SELL".equals(type)) {
            this.type = TransactionType.SELL;
        } else {
            throw new DataIgnoredException(UNSUPPORTED_TRANSACTION_TYPE.concat(type));
        }
    }

    @Parsed(field = "Amount", defaultNullRead = "0")
    public void setAmount(BigDecimal amount) {
        this.amount = amount.abs();
    }

    @Parsed(field = "Amount Currency")
    public void setAmountCurrency(String curr) {
        amountCurrency = Currency.fromCode(curr);
    }

    @Parsed(field = "Price", defaultNullRead = "0")
    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    @Parsed(field = "Price Currency")
    public void setPriceCurrency(String curr) {
        priceCurrency = Currency.fromCode(curr);
    }

    @Parsed(field = "Fee", defaultNullRead = "0")
    public void setFee(BigDecimal fee) {
        this.fee = fee;
    }

    @Parsed(field = "Fee Currency")
    public void setFeeCurrency(String curr) {
        auxFeeCurrency = Currency.fromCode(curr);
    }

    @Parsed(field = "Status")
    public void checkStatus(String status) {
        if (!"OK".equals(status)) {
            throw new DataIgnoredException(UNSUPPORTED_STATUS_TYPE.concat(status));
        }
    }


    @Override
    public TransactionCluster toTransactionCluster() {
        validateCurrencyPair(amountCurrency, priceCurrency);
        final boolean ignoredFee = !priceCurrency.equals(auxFeeCurrency);
        List<ImportedTransactionBean> related;
        if (ParserUtils.equalsToZero(fee) || ignoredFee) {
            related = Collections.emptyList();
        } else {
            related = List.of(
                new FeeRebateImportedTransactionBean(
                    id + FEE_UID_PART,
                    date,
                    amountCurrency,
                    priceCurrency,
                    TransactionType.FEE,
                    fee.setScale(ParserUtils.DECIMAL_DIGITS, RoundingMode.HALF_UP),
                    auxFeeCurrency
                )
            );
        }

        return new TransactionCluster(
            new BuySellImportedTransactionBean(
                id,             //uuid
                date,           //executed
                amountCurrency, //base
                priceCurrency,  //quote
                type,           //action
                amount,         //base quantity
                price          //unit price
            ),
            related,
            ignoredFee ? 1 : 0
        );
    }
}
