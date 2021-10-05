package io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean;

import com.univocity.parsers.annotations.Format;
import com.univocity.parsers.annotations.Headers;
import com.univocity.parsers.annotations.Parsed;
import com.univocity.parsers.annotations.Replace;
import com.univocity.parsers.common.DataValidationException;
import io.everytrade.server.model.Currency;
import io.everytrade.server.model.CurrencyPair;
import io.everytrade.server.model.TransactionType;
import io.everytrade.server.plugin.api.parser.BuySellImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.FeeRebateImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.ImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.TransactionCluster;
import io.everytrade.server.plugin.impl.everytrade.parser.ParserUtils;
import io.everytrade.server.plugin.impl.everytrade.parser.exception.DataIgnoredException;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.ExchangeBean;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.everytrade.server.model.CurrencyPair.getTradeablePairs;
import static java.util.Collections.emptyList;

@Headers(
    sequence = {
        "Trade Date", "Status", "Pair", "Average Price", "Limit Price", "Strategy", "Side", "Amount", "Order Type", "Filled",
        "Remaining", "Total", "Fee"
    },
    extract = true
)
public class AquanowBeanV1  extends ExchangeBean {

    private static final String REQUIRED_STATUS = "COMPLETE";

    private Instant date;
    private Currency marketBase;
    private Currency marketQuote;
    private TransactionType type;
    private BigDecimal amount;
    private BigDecimal price;
    private BigDecimal fee;

    private static Map<String, CurrencyPair> fastCurrencyPair = new HashMap<>();
    static {
        getTradeablePairs().forEach(t -> fastCurrencyPair.put(t.toString().replace("/", "-"), t));
    }

    @Parsed(field = "Trade date")
    @Format(formats = {"yyyy-MM-dd HH:mm:ss"}, options = {"locale=EN", "timezone=UTC"})
    public void setDate(Date value) {
        date = value.toInstant();
    }

    @Parsed(field = "Pair")
    public void setPair(String value) {
        final CurrencyPair currencyPair = fastCurrencyPair.get(value);
        if (currencyPair == null) {
            throw new DataValidationException(UNSUPPORTED_CURRENCY_PAIR.concat(value));
        }
        marketBase = currencyPair.getBase();
        marketQuote = currencyPair.getQuote();
    }

    @Parsed(field = "Side")
    public void setType(String value) {
        type = detectTransactionType(value);
    }

    @Parsed(field = "Filled")
    @Replace(expression = IGNORED_CHARS_IN_NUMBER, replacement = "")
    public void setAmount(BigDecimal value) {
        amount = value;
    }

    @Parsed(field = "Average Price")
    @Replace(expression = IGNORED_CHARS_IN_NUMBER, replacement = "")
    public void setTotal(BigDecimal value) {
        price = value;
    }

    @Parsed(field = "Fee")
    @Replace(expression = IGNORED_CHARS_IN_NUMBER, replacement = "")
    public void setFee(BigDecimal value) {
        fee = value;
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
        validatePositivity(amount, price, fee);

        final List<ImportedTransactionBean> related;

        if (ParserUtils.equalsToZero(fee)) {
            related = emptyList();
        } else {
            related = List.of(
                new FeeRebateImportedTransactionBean(
                    null,
                    date,
                    marketBase,
                    marketQuote,
                    TransactionType.FEE,
                    fee.setScale(ParserUtils.DECIMAL_DIGITS, ParserUtils.ROUNDING_MODE),
                    marketBase
                )
            );
        }

        return new TransactionCluster(
            new BuySellImportedTransactionBean(
                null,
                date,
                marketBase,
                marketQuote,
                type,
                amount.setScale(ParserUtils.DECIMAL_DIGITS, ParserUtils.ROUNDING_MODE),
                price
            ),
            related,
            0
        );
    }
}
