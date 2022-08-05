package io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean;

import com.univocity.parsers.annotations.Format;
import com.univocity.parsers.annotations.Parsed;
import io.everytrade.server.model.Currency;
import io.everytrade.server.model.TransactionType;
import io.everytrade.server.plugin.api.parser.BuySellImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.DepositWithdrawalImportedTransaction;
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

import static io.everytrade.server.plugin.impl.everytrade.parser.ParserUtils.equalsToZero;
import static io.everytrade.server.plugin.impl.everytrade.parser.ParserUtils.nullOrZero;

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
    private String address;

    @Parsed(field = "ID")
    public void setId(String id) {
        this.id = id;
    }

    @Parsed(field = {"Date", "Datum"})
    @Format(formats = {"yyyy-MM-dd HH:mm:ss", "dd.MM.yyyy HH:mm"}, options = {"locale=US", "timezone=UTC"})
    public void setDate(Date date) {
        this.date = date.toInstant();
    }

    @Parsed(field = {"Type", "Typ"})
    public void setType(String type) {
        if ("BUY".equals(type) || "QUICK_BUY".equals(type)) {
            this.type = TransactionType.BUY;
        } else if ("SELL".equals(type) || "QUICK_SELL".equals(type)) {
            this.type = TransactionType.SELL;
        } else if ("DEPOSIT".equals(type)) {
            this.type = TransactionType.DEPOSIT;
        } else if ("WITHDRAWAL".equals(type)) {
            this.type = TransactionType.WITHDRAWAL;
        } else {
            throw new DataIgnoredException(UNSUPPORTED_TRANSACTION_TYPE.concat(type));
        }
    }

    @Parsed(field = {"Amount", "Částka"}, defaultNullRead = "0")
    public void setAmount(BigDecimal amount) {
        this.amount = amount.abs();
    }

    @Parsed(field = {"Amount Currency", "Částka měny"})
    public void setAmountCurrency(String curr) {
        amountCurrency = parseCurrency(curr);
    }

    @Parsed(field = {"Price", "Cena"}, defaultNullRead = "0")
    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    @Parsed(field = {"Price Currency", "Cena měny"})
    public void setPriceCurrency(String curr) {
        priceCurrency = parseCurrency(curr);
    }

    @Parsed(field = {"Fee", "Poplatek"}, defaultNullRead = "0")
    public void setFee(BigDecimal fee) {
        this.fee = fee;
    }

    @Parsed(field = {"Fee Currency", "Poplatek měny"})
    public void setFeeCurrency(String curr) {
        auxFeeCurrency = parseCurrency(curr);
    }

    @Parsed(field = "Status")
    public void checkStatus(String status) {
        if ("COMPLETED".equals(status)) {
            status = "OK";
        }
        if (!"OK".equals(status)) {
            throw new DataIgnoredException(UNSUPPORTED_STATUS_TYPE.concat(status));
        }
    }

    @Parsed(field = {"Description", "Popisek"})
    public void setAddress(String address) {
        this.address = address;
    }

    @Override
    public TransactionCluster toTransactionCluster() {
        switch (this.type) {
            case BUY:
            case SELL:
                return createBuySellTransactionCluster();
            case DEPOSIT:
            case WITHDRAWAL:
                return createDepositOrWithdrawalTxCluster();
            default:
                throw new IllegalStateException(String.format("Unsupported transaction type %s.", type.name()));
        }
    }

    private TransactionCluster createBuySellTransactionCluster() {
        validateCurrencyPair(amountCurrency, priceCurrency);
        final boolean isIncorrectFee = (auxFeeCurrency == null);
        List<ImportedTransactionBean> related;
        if (equalsToZero(fee) || isIncorrectFee) {
            related = Collections.emptyList();
        } else {
            related = List.of(
                new FeeRebateImportedTransactionBean(
                    id + FEE_UID_PART,
                    date,
                    auxFeeCurrency,
                    auxFeeCurrency,
                    TransactionType.FEE,
                    fee.setScale(ParserUtils.DECIMAL_DIGITS, RoundingMode.HALF_UP),
                    auxFeeCurrency
                )
            );
        }
        TransactionCluster cluster = new TransactionCluster(
            new BuySellImportedTransactionBean(
                id,             //uuid
                date,           //executed
                amountCurrency, //base
                priceCurrency,  //quote
                type,           //action
                amount,         //base quantity
                price          //unit price
            ),
            related
        );
        if (isIncorrectFee) {
            cluster.setFailedFee(
                1,
                "Fee " + (auxFeeCurrency != null ? auxFeeCurrency.code() : "null") + " currency is neither base or quote"
            );
        } else if(nullOrZero(fee)) {
//            cluster.setIgnoredFee(1, "Fee amount is 0 " + (auxFeeCurrency != null ? auxFeeCurrency.code() : ""));
        }
        return cluster;
    }

    private TransactionCluster createDepositOrWithdrawalTxCluster() {
        var tx = new DepositWithdrawalImportedTransaction(
            id,
            date,
            amountCurrency, //base
            priceCurrency,  //quote
            type,
            amount,
            amountCurrency.isFiat() ? null : address
        );
        return new TransactionCluster(tx, getRelatedFeeTransaction());
    }

    private List<ImportedTransactionBean> getRelatedFeeTransaction() {
        List<ImportedTransactionBean> related;
        if (equalsToZero(fee)) {
            related = Collections.emptyList();
        } else {
            related = List.of(
                new FeeRebateImportedTransactionBean(
                    id + FEE_UID_PART,
                    date,
                    auxFeeCurrency,
                    auxFeeCurrency,
                    TransactionType.FEE,
                    fee.setScale(ParserUtils.DECIMAL_DIGITS, RoundingMode.HALF_UP),
                    auxFeeCurrency
                )
            );
        }
        return related;
    }

    private Currency parseCurrency(String c) {
        if (c == null) {
            return null;
        }
        if (c.startsWith("$")) {
            c = c.substring(1);
        }
        return Currency.fromCode(c);
    }
}
