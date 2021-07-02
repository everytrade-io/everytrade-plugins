package io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean;

import com.univocity.parsers.annotations.Format;
import com.univocity.parsers.annotations.Headers;
import com.univocity.parsers.annotations.Parsed;
import com.univocity.parsers.annotations.Replace;
import com.univocity.parsers.common.DataValidationException;
import io.everytrade.server.model.Currency;
import io.everytrade.server.model.TransactionType;
import io.everytrade.server.plugin.api.parser.BuySellImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.FeeRebateImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.TransactionCluster;
import io.everytrade.server.plugin.impl.everytrade.parser.ParserUtils;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.ExchangeBean;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Date;
import java.util.List;

@Headers(
    sequence = {"Date", "Market", "Category", "Type", "Amount", "Total", "Fee", "Fee Currency"},
    extract = true
)
public class PoloniexBeanV2 extends ExchangeBean {
    public static final String UNSUPPORTED_CATEGORY = "Unsupported category ";
    public static final String EXCHANGE_CATEGORY = "Exchange";
    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private Instant date;
    private Currency marketBase;
    private Currency marketQuote;
    private TransactionType type;
    private BigDecimal amount;
    private BigDecimal total;
    private BigDecimal fee;
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
        amount = value;
    }

    @Parsed(field = "Total")
    public void setTotal(BigDecimal value) {
        total = value;
    }

    @Parsed(field = "Fee")
    @Replace(expression = "%", replacement = "")
    public void setFee(BigDecimal value) {
        fee = value;
    }

    @Parsed(field = "Fee Currency")
    public void setFeeCurrency(String value) {
        feeCurrency = Currency.fromCode(value);
    }

    @Override
    public TransactionCluster toTransactionCluster() {
        validateCurrencyPair(marketBase, marketQuote);
        validatePositivity(amount,total,fee);

        final BigDecimal feeValue;
        if (feeCurrency.equals(marketBase)) {
            feeValue = amount
                .divide(HUNDRED, ParserUtils.DECIMAL_DIGITS, ParserUtils.ROUNDING_MODE)
                .multiply(fee)
                .setScale(ParserUtils.DECIMAL_DIGITS, ParserUtils.ROUNDING_MODE);
        } else if (feeCurrency.equals(marketQuote)) {
            feeValue = total
                .divide(HUNDRED, ParserUtils.DECIMAL_DIGITS, ParserUtils.ROUNDING_MODE)
                .multiply(fee)
                .setScale(ParserUtils.DECIMAL_DIGITS, ParserUtils.ROUNDING_MODE);
        } else {
            throw new DataValidationException(String.format(
                "Fee currency '%s' differs from  base currency '%s' and quote currency '%s'.",
                feeCurrency,
                marketBase,
                marketQuote
                ));
        }

        return new TransactionCluster(
            new BuySellImportedTransactionBean(
                null,               //uuid
                date,                    //executed
                marketBase,              //base
                marketQuote,             //quote
                type,                    //action
                amount,                  //base quantity
                evalUnitPrice(total,amount) //unit price
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
