package io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean;

import com.univocity.parsers.annotations.Format;
import com.univocity.parsers.annotations.Headers;
import com.univocity.parsers.annotations.Parsed;
import io.everytrade.server.model.Currency;
import io.everytrade.server.model.TransactionType;
import io.everytrade.server.plugin.api.parser.FeeRebateImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.ImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.TransactionCluster;
import io.everytrade.server.plugin.impl.everytrade.parser.ParserUtils;
import io.everytrade.server.plugin.impl.everytrade.parser.exception.DataIgnoredException;
import io.everytrade.server.plugin.impl.everytrade.parser.exception.ParserErrorCurrencyException;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.ExchangeBean;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Date;
import java.util.List;

import static io.everytrade.server.model.TransactionType.BUY;
import static io.everytrade.server.model.TransactionType.DEPOSIT;
import static io.everytrade.server.model.TransactionType.REWARD;
import static io.everytrade.server.model.TransactionType.SELL;
import static io.everytrade.server.model.TransactionType.WITHDRAWAL;
import static io.everytrade.server.plugin.impl.everytrade.parser.ParserUtils.equalsToZero;
import static io.everytrade.server.plugin.impl.everytrade.parser.ParserUtils.nullOrZero;
import static io.everytrade.server.util.CoinMateDataUtil.AFFILIATE_OPERATION;
import static io.everytrade.server.util.CoinMateDataUtil.BUY_OPERATION;
import static io.everytrade.server.util.CoinMateDataUtil.REFERRAL_OPERATION;
import static io.everytrade.server.util.CoinMateDataUtil.SELL_OPERATION;
import static io.everytrade.server.util.CoinMateDataUtil.MARKET_BUY_OPERATION;
import static io.everytrade.server.util.CoinMateDataUtil.MARKET_SELL_OPERATION;
import static io.everytrade.server.util.CoinMateDataUtil.QUICK_BUY_OPERATION;
import static io.everytrade.server.util.CoinMateDataUtil.QUICK_SELL_OPERATION;
import static io.everytrade.server.util.CoinMateDataUtil.DEPOSIT_OPERATION;
import static io.everytrade.server.util.CoinMateDataUtil.WITHDRAWAL_OPERATION;
import static java.util.Collections.emptyList;

@Headers(sequence = {"ID", "Date", "Datum", "Description", "Popisek", "Type", "Typ", "Amount", "Částka", "Amount Currency", "Částka měny",
    "Price", "Cena", "Price Currency", "Cena měny", "Fee", "Poplatek", "Fee Currency", "Poplatek měny", "Status"},
    extract = true)
public class CoinmateBeanV1 extends ExchangeBean {
    // auxiliary field for validation
    private Currency auxFeeCurrency;
    private String id;
    private Instant date;
    private TransactionType type;
    private String originalType;
    private BigDecimal amount;
    private Currency amountCurrency;
    private BigDecimal price;
    private Currency priceCurrency;
    private BigDecimal fee;
    private String address;
    private boolean isFailedFee;
    private String failedFeeMessage;

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
        originalType = type;
        if (BUY_OPERATION.equals(type) || QUICK_BUY_OPERATION.equals(type) || MARKET_BUY_OPERATION.equals(type)) {
            this.type = BUY;
        } else if (SELL_OPERATION.equals(type) || QUICK_SELL_OPERATION.equals(type) || MARKET_SELL_OPERATION.equals(type)) {
            this.type = SELL;
        } else if (DEPOSIT_OPERATION.equals(type)) {
            this.type = DEPOSIT;
        } else if (WITHDRAWAL_OPERATION.equals(type)) {
            this.type = WITHDRAWAL;
        } else if (type == null && address.contains("User:") && address.contains("(ID:") && address.contains("Account ID:")
            || AFFILIATE_OPERATION.equalsIgnoreCase(type) || REFERRAL_OPERATION.equalsIgnoreCase(type)){
            this.type = REWARD;
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

    @Parsed(field = {"Fee", "Poplatek"})
    public void setFee(String value) {
        try {
            if (!"".equals(value) && value != null) {
                fee = new BigDecimal(value).abs().setScale(ParserUtils.DECIMAL_DIGITS, ParserUtils.ROUNDING_MODE);
            }
        } catch (Exception e) {
            isFailedFee = true;
            failedFeeMessage = e.getMessage();
        }
    }

    @Parsed(field = {"Fee Currency", "Poplatek měny"})
    public void setFeeCurrency(String value) {
        try {
            if (!"".equals(value)) {
                auxFeeCurrency = parseCurrency(value);
            }
        } catch (Exception e) {
            isFailedFee = true;
            failedFeeMessage = e.getMessage();
        }
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
            case REWARD:
                return createRewardTransactionCluster();
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
        if (nullOrZero(fee) || isIncorrectFee || isFailedFee) {
            related = emptyList();
        } else {
            try {
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
            } catch (Exception e) {
                isFailedFee = true;
                failedFeeMessage = e.getMessage();
                related = emptyList();
            }
        }
        TransactionCluster cluster = new TransactionCluster(
            new ImportedTransactionBean(
                id,             //uuid
                date,           //executed
                amountCurrency, //base
                priceCurrency,  //quote
                type,           //action
                amount,         //base quantity
                price,
                type.name().equalsIgnoreCase(originalType) ? null : originalType,
                null
            ),
            related
        );
        if (isFailedFee) {
            cluster.setFailedFee(1, String.format("Fee transaction failed - %s", failedFeeMessage));
        } else if (nullOrZero(fee)) {
//            cluster.setIgnoredFee(1, "Fee amount is 0 " + (auxFeeCurrency != null ? auxFeeCurrency.code() : ""));
        }
        return cluster;
    }

    private TransactionCluster createRewardTransactionCluster() {
        TransactionCluster cluster = new TransactionCluster(
            new ImportedTransactionBean(
                id,                 //uuid
                date,               //executed
                amountCurrency,     //base
                amountCurrency,     //quote
                REWARD,             //action
                amount,             //base quantity
                null,     //fee rebate
                REWARD.name().equalsIgnoreCase(originalType) ? null : originalType,
                null
            ),
            emptyList()
        );
        return cluster;
    }

    private TransactionCluster createDepositOrWithdrawalTxCluster() {
        var tx = ImportedTransactionBean.createDepositWithdrawal(
            id,
            date,
            amountCurrency, //base
            priceCurrency,  //quote
            type,
            amount,
            amountCurrency.isFiat() ? null : address,
            type.name().equalsIgnoreCase(originalType) ? null : originalType,
            null
        );
        return new TransactionCluster(tx, getRelatedFeeTransaction());
    }

    private List<ImportedTransactionBean> getRelatedFeeTransaction() {
        List<ImportedTransactionBean> related;
        if (equalsToZero(fee)) {
            related = emptyList();
        } else {
            try {
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
            } catch (Exception e) {
                isFailedFee = true;
                failedFeeMessage = e.getMessage();
                related = emptyList();
            }
        }
        return related;
    }

    private Currency parseCurrency(String c) {
        if (c == null) {
            return null;
        }
        if (c.startsWith("$")) {
            c = c.substring(1);
        } try {
            return Currency.fromCode(c);
        } catch (IllegalArgumentException e) {
            throw new ParserErrorCurrencyException("Unknown currency pair: " + c);
        }
    }
}
