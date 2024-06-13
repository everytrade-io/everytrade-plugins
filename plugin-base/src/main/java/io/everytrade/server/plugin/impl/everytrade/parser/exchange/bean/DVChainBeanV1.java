package io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean;


import com.univocity.parsers.annotations.Headers;
import com.univocity.parsers.annotations.Parsed;
import com.univocity.parsers.annotations.Replace;
import io.everytrade.server.model.Currency;
import io.everytrade.server.model.TransactionType;
import io.everytrade.server.plugin.api.parser.ImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.TransactionCluster;
import io.everytrade.server.plugin.impl.everytrade.parser.ParserUtils;
import io.everytrade.server.plugin.impl.everytrade.parser.exception.DataIgnoredException;
import io.everytrade.server.plugin.impl.everytrade.parser.exception.ParserErrorCurrencyException;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.ExchangeBean;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.format.DateTimeFormatter;

import static java.util.Collections.emptyList;

@Headers(
    sequence = {"asset", "counterasset", "price", "quantity", "side", "status", "limitprice", "batchid", "createdat", "filledat"},
    extract = true
)
public class DVChainBeanV1 extends ExchangeBean {

    private static final String REQUIRED_STATUS = "COMPLETE";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern(
            "[yyyy-MM-dd HH:mm:ss.SX]" +
            "[yyyy-MM-dd HH:mm:ss.SSX]" +
            "[yyyy-MM-dd HH:mm:ss.SSSX]" +
            "[yyyy-MM-dd HH:mm:ss.SSSSX]" +
            "[yyyy-MM-dd HH:mm:ss.SSSSSX]" +
            "[yyyy-MM-dd HH:mm:ss.SSSSSSX]"
    );

    private Instant date;
    private Currency marketBase;
    private Currency marketQuote;
    private TransactionType type;
    private BigDecimal amount;
    private BigDecimal price;

    @Parsed(field = "filledat")
    public void setDate(String value) {
        this.date = Instant.from(DATE_FORMATTER.parse(value));
    }

    @Parsed(field = "asset")
    public void setBase(String value) {
        try {
            marketBase = Currency.fromCode(value);
        } catch (IllegalArgumentException e) {
            throw new ParserErrorCurrencyException("Unknown currency pair: " + value);
        }
    }

    @Parsed(field = "counterasset")
    public void setQuote(String value) {
        try {
            marketQuote = Currency.fromCode(value);
        } catch (IllegalArgumentException e) {
            throw new ParserErrorCurrencyException("Unknown currency pair: " + value);
        }
    }

    @Parsed(field = "side")
    public void setType(String value) {
        type = detectTransactionType(value);
    }

    @Parsed(field = "quantity")
    @Replace(expression = IGNORED_CHARS_IN_NUMBER, replacement = "")
    public void setAmount(BigDecimal value) {
        amount = value;
    }

    @Parsed(field = "price")
    @Replace(expression = IGNORED_CHARS_IN_NUMBER, replacement = "")
    public void setPrice(BigDecimal value) {
        price = value;
    }

    @Parsed(field = "status")
    public void checkStatus(String value) {
        if (!REQUIRED_STATUS.equalsIgnoreCase(value)) {
            throw new DataIgnoredException(UNSUPPORTED_STATUS_TYPE.concat(value));
        }
    }

    @Override
    public TransactionCluster toTransactionCluster() {
        validateCurrencyPair(marketBase, marketQuote);
        validatePositivity(amount, price);

        return new TransactionCluster(
            new ImportedTransactionBean(
                null,
                date,
                marketBase,
                marketQuote,
                type,
                amount.setScale(ParserUtils.DECIMAL_DIGITS, ParserUtils.ROUNDING_MODE),
                price
            ),
            emptyList()
        );
    }
}
