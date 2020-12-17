package io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean;

import com.univocity.parsers.annotations.Format;
import com.univocity.parsers.annotations.Headers;
import com.univocity.parsers.annotations.Parsed;
import com.univocity.parsers.annotations.Replace;
import com.univocity.parsers.common.DataValidationException;
import io.everytrade.server.model.Currency;
import io.everytrade.server.model.TransactionType;
import io.everytrade.server.plugin.api.parser.BuySellImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.ImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.TransactionCluster;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.ExchangeBean;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.Date;

@Headers(
    sequence = {"Date", "Market", "Category", "Type", "Base Total Less Fee", "Quote Total Less Fee"},
    extract = true
)
public class PoloniexBeanV1 extends ExchangeBean {
    public static final String UNSUPPORTED_CATEGORY = "Unsupported category ";
    private Instant date;
    private Currency marketBase;
    private Currency marketQuote;
    private TransactionType type;
    private BigDecimal baseTotalLessFee;
    private BigDecimal quoteTotalLessFee;

    @Parsed(field = "Date")
    @Format(formats = {"yyyy-MM-dd HH:mm:ss"}, options = {"locale=EN", "timezone=UTC"})
    public void setDate(Date value) {
        date = value.toInstant();
    }

    @Parsed(field = "Market")
    public void setMarket(String value) {
        final String[] values = value.split("/");
        marketBase = Currency.valueOf(values[0]);
        marketQuote = Currency.valueOf(values[1]);
    }

    @Parsed(field = "Category")
    public void setCategory(String value) {
        if (!"Exchange".equals(value)) {
            throw new DataValidationException(UNSUPPORTED_CATEGORY.concat(value));
        }
    }

    @Parsed(field = "Type")
    public void setType(String value) {
        type = detectTransactionType(value);
    }

    @Parsed(field = "Base Total Less Fee")
    @Replace(expression = IGNORED_CHARS_IN_NUMBER, replacement = "")
    public void setBaseTotalLessFee(BigDecimal value) {
        baseTotalLessFee = value;
    }

    @Parsed(field = "Quote Total Less Fee")
    @Replace(expression = IGNORED_CHARS_IN_NUMBER, replacement = "")
    public void setQuoteTotalLessFee(BigDecimal value) {
        quoteTotalLessFee = value;
    }

    @Override
    public TransactionCluster toTransactionCluster() {
        validateCurrencyPair(marketBase, marketQuote);

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
            Collections.emptyList()
        );
    }
}
