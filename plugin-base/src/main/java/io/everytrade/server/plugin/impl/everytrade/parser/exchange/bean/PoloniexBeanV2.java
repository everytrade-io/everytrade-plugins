package io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean;

import com.univocity.parsers.annotations.Format;
import com.univocity.parsers.annotations.Headers;
import com.univocity.parsers.annotations.Parsed;
import com.univocity.parsers.common.DataValidationException;
import io.everytrade.server.model.Currency;
import io.everytrade.server.model.TransactionType;
import io.everytrade.server.plugin.api.parser.BuySellImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.FeeRebateImportedTransactionBean;
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

        final BigDecimal feeValue;
        if (feeCurrency.equals(marketBase)) {
            feeValue = amount.subtract(quoteTotalLessFee.abs());
        } else if (feeCurrency.equals(marketQuote)) {
            feeValue = total.subtract(baseTotalLessFee.abs());
        } else {
            throw new DataValidationException(String.format(
                "Fee currency '%s' differs from  base currency '%s' and quote currency '%s'.",
                feeCurrency,
                marketBase,
                marketQuote
                ));
        }
        validatePositivity(feeValue);

        return new TransactionCluster(
            new BuySellImportedTransactionBean(
                null,               //uuid
                date,                    //executed
                marketBase,              //base
                marketQuote,             //quote
                type,                    //action
                quoteTotalLessFee.abs(), //base quantity
                evalUnitPrice(baseTotalLessFee.abs(), quoteTotalLessFee.abs()) //unit price
            ),
            List.of(
                new FeeRebateImportedTransactionBean(
                    null,
                    date,
                    marketBase,
                    marketQuote,
                    TransactionType.FEE,
                    feeValue,
                    feeCurrency
                )
            )
        );
    }
}
