package io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean;

import com.univocity.parsers.annotations.Headers;
import com.univocity.parsers.annotations.Parsed;
import com.univocity.parsers.annotations.Replace;
import com.univocity.parsers.common.DataValidationException;
import io.everytrade.server.model.Currency;
import io.everytrade.server.model.TransactionType;
import io.everytrade.server.plugin.api.parser.BuySellImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.FeeRebateImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.ImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.TransactionCluster;
import io.everytrade.server.plugin.impl.everytrade.parser.ParserUtils;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.ExchangeBean;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;

//MIN> BFX-001:|#|PAIR|AMOUNT|PRICE|FEE|FEE CURRENCY|DATE|
//FULL> BFX-001:|#|PAIR|AMOUNT|PRICE|FEE|FEE CURRENCY|DATE|ORDER ID|
@Headers(sequence = {"#", "PAIR", "AMOUNT", "PRICE", "FEE", "FEE CURRENCY", "DATE"}, extract = true)
public class BitfinexBeanV1 extends ExchangeBean {
    public static final String ILLEGAL_ZERO_VALUE_OF_AMOUNT = "Illegal zero value of amount.";
    private String uid;
    private Currency pairBase;
    private Currency pairQuote;
    private BigDecimal amount;
    private BigDecimal price;
    private BigDecimal fee;
    private Currency feeCurrency;
    private String date;
    private Instant dateConverted;

    @Parsed(field = "#")
    public void setUid(String value) {
        uid = value;
    }

    @Parsed(field = "PAIR")
    public void setPair(String value) {
        final String[] values = value.split("/");
        pairBase = Currency.fromCode(values[0]);
        pairQuote = Currency.fromCode(values[1]);
    }

    @Parsed(field = "AMOUNT")
    @Replace(expression = IGNORED_CHARS_IN_NUMBER, replacement = "")
    public void setAmount(BigDecimal value) {
        if (value.compareTo(BigDecimal.ZERO) == 0) {
            throw new DataValidationException(ILLEGAL_ZERO_VALUE_OF_AMOUNT);
        }
        amount = value;
    }

    @Parsed(field = "PRICE")
    @Replace(expression = IGNORED_CHARS_IN_NUMBER, replacement = "")
    public void setPrice(BigDecimal value) {
        price = value;
    }

    @Parsed(field = "FEE")
    @Replace(expression = IGNORED_CHARS_IN_NUMBER, replacement = "")
    public void setFee(BigDecimal value) {
        fee = value;
    }

    @Parsed(field = "FEE CURRENCY")
    public void setFeeCurrency(String value) {
        try {
            feeCurrency = Currency.fromCode(value);
        } catch (IllegalArgumentException e) {
            feeCurrency = null;
        }
    }

    @Parsed(field = "DATE")
    public void setDate(String value) {
        date = value;
    }

    public String getDate() {
        return date;
    }

    public void setDateConverted(Instant dateConverted) {
        this.dateConverted = dateConverted;
    }

    @Override
    public TransactionCluster toTransactionCluster() {
        validateCurrencyPair(pairBase, pairQuote);
        validatePositivity(price);

        TransactionType transactionType = amount.compareTo(BigDecimal.ZERO) > 0
            ? TransactionType.BUY : TransactionType.SELL;

        final boolean isIncorrectFeeCurr = (feeCurrency == null);

        List<ImportedTransactionBean> related;

        if (isIncorrectFeeCurr || ParserUtils.equalsToZero(fee)) {
            related = Collections.emptyList();
        } else {
            related = List.of(
                new FeeRebateImportedTransactionBean(
                    uid + FEE_UID_PART,
                    dateConverted,
                    feeCurrency,
                    feeCurrency,
                    TransactionType.FEE,
                    fee.abs().setScale(ParserUtils.DECIMAL_DIGITS, ParserUtils.ROUNDING_MODE),
                    feeCurrency
                )
            );
        }

        TransactionCluster cluster = new TransactionCluster(
            new BuySellImportedTransactionBean(
                uid,
                dateConverted,
                pairBase,
                pairQuote,
                transactionType,
                amount.abs().setScale(ParserUtils.DECIMAL_DIGITS, ParserUtils.ROUNDING_MODE),
                price.setScale(ParserUtils.DECIMAL_DIGITS, ParserUtils.ROUNDING_MODE)
            ),
            related
        );
        if (isIncorrectFeeCurr) {
            cluster.setFailedFee(1, "Fee " + (feeCurrency != null ? feeCurrency.code() : "null") + " currency is neither base or quote");
        } else if (ParserUtils.equalsToZero(fee)) {
            cluster.setIgnoredFee(1, "Fee amount is 0 " + (feeCurrency != null ? feeCurrency.code() : ""));
        }
        return cluster;
    }
}
