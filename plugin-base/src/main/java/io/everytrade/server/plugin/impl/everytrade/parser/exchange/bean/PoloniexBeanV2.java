package io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean;

import com.univocity.parsers.annotations.Format;
import com.univocity.parsers.annotations.Headers;
import com.univocity.parsers.annotations.Parsed;
import com.univocity.parsers.common.DataValidationException;
import io.everytrade.server.model.Currency;
import io.everytrade.server.model.TransactionType;
import io.everytrade.server.plugin.api.parser.FeeRebateImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.ImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.TransactionCluster;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.ExchangeBean;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Date;
import java.util.List;

@Headers(
    sequence = {"Date", "Market", "Category", "Type", "Amount", "Total", "Base Total Less Fee", "Quote Total Less Fee","Fee Currency"},
    extract = true
)
public class PoloniexBeanV2 extends ExchangeBean {
    public static final String UNSUPPORTED_CATEGORY = "Unsupported category ";
    public static final String EXCHANGE_CATEGORY = "Exchange";
    public static final String UNSUPPORTED_FEE_CURRENCY = "Unsupported fee currency ";
    private Instant date;
    private Currency marketBase;
    private Currency marketQuote;
    private TransactionType type;
    private BigDecimal amount;
    private BigDecimal total;
    private BigDecimal baseTotalLessFee;
    private BigDecimal quoteTotalLessFee;
    private Currency feeCurrency;

    @Parsed(field = "Date")
    @Format(formats = {"yyyy-MM-dd HH:mm:ss"}, options = {"locale=EN", "timezone=UTC"})
    public void setDate(Date value) {
        date = value.toInstant();
    }

    @Parsed(field = "Market")
    public void setMarket(String value) {
        final String[] values = value.split("/");
        marketBase = Currency.fromCode(values[0]);
        marketQuote = Currency.fromCode(values[1]);
    }

    @Parsed(field = "Category")
    public void setCategory(String value) {
        if (!EXCHANGE_CATEGORY.equals(value)) {
            throw new DataValidationException(UNSUPPORTED_CATEGORY.concat(value));
        }
    }

    @Parsed(field = "Type")
    public void setType(String value) {
        type = detectTransactionType(value);
    }

    @Parsed(field = "Amount")
    public void setAmount(BigDecimal value) {
        if (value.compareTo(BigDecimal.ZERO) == 0) {
            throw new DataValidationException("Amount can not be zero.");
        }
        amount = value;
    }

    @Parsed(field = "Total")
    public void setTotal(BigDecimal value) {
        total = value;
    }

    @Parsed(field = "Base Total Less Fee")
    public void setBaseTotalLessFee(BigDecimal value) {
        baseTotalLessFee = value;
    }

    @Parsed(field = "Quote Total Less Fee")
    public void setQuoteTotalLessFee(BigDecimal value) {
        if (value.compareTo(BigDecimal.ZERO) == 0) {
            throw new DataValidationException("'Quote Total Less Fee' can not be zero.");
        }
        quoteTotalLessFee = value;
    }

    @Parsed(field = "Fee Currency")
    public void setFeeCurrency(String value) {
        feeCurrency = Currency.fromCode(value);
    }

    @Override
    public TransactionCluster toTransactionCluster() {
        validateCurrencyPair(marketBase, marketQuote);
        validatePositivity(amount,total);

        final BigDecimal totalPrice;
        final BigDecimal fee;
        if (TransactionType.BUY.equals(type)) {
            totalPrice = total;
            if (feeCurrency.equals(marketBase)) {
                fee = amount.subtract(quoteTotalLessFee.abs());
            } else {
                throw new DataValidationException(String.format(
                    UNSUPPORTED_FEE_CURRENCY + "'%s' for BUY transaction on pair '%s/%s'.",
                    feeCurrency,
                    marketBase,
                    marketQuote
                ));
            }
        } else if (TransactionType.SELL.equals(type)) {
            totalPrice = baseTotalLessFee.abs();
            if (feeCurrency.equals(marketQuote)) {
                fee = total.subtract(baseTotalLessFee.abs());
            } else {
                throw new DataValidationException(String.format(
                    UNSUPPORTED_FEE_CURRENCY + "'%s' for SELL transaction on pair '%s/%s'.",
                    feeCurrency,
                    marketBase,
                    marketQuote
                ));
            }
        } else {
            throw new DataValidationException(String.format("Unsupported transaction type '%s'.", type));
        }

        validatePositivity(totalPrice, fee);

        return new TransactionCluster(
            new ImportedTransactionBean(
                null,               //uuid
                date,                    //executed
                marketBase,              //base
                marketQuote,             //quote
                type,                    //action
                amount,                  //base quantity
                evalUnitPrice(totalPrice, amount) //unit price
            ),
            List.of(
                new FeeRebateImportedTransactionBean(
                    null,
                    date,
                    feeCurrency,
                    feeCurrency,
                    TransactionType.FEE,
                    fee,
                    feeCurrency
                )
            )
        );
    }
}
