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
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.ExchangeBean;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.everytrade.server.model.CurrencyPair.getTradeablePairs;
import static io.everytrade.server.plugin.impl.everytrade.parser.ParserUtils.equalsToZero;
import static io.everytrade.server.plugin.impl.everytrade.parser.ParserUtils.nullOrZero;

//MIN> BIN-001:|^Date\(.*\)$|Market|Type|Amount|Total|Fee|Fee Coin|
//FULL> BIN-001:|^Date\(.*\)$|Market|Type|Price|Amount|Total|Fee|Fee Coin|
@Headers(sequence = {"Market", "Type", "Amount", "Total", "Fee", "Fee Coin"}, extract = true)
public class BinanceBeanV1 extends ExchangeBean {
    private static Map<String, CurrencyPair> fastCurrencyPair = new HashMap<>();
    private Instant date;
    private Currency marketBase;
    private Currency marketQuote;
    private TransactionType type;
    private BigDecimal amount;
    private BigDecimal total;
    private BigDecimal fee;
    private Currency feeCoin;

    static {
        getTradeablePairs().forEach(t -> fastCurrencyPair.put(t.toString().replace("/", ""), t));
    }

    //Date
    @Parsed(index = 0)
    @Format(formats = {"yyyy-MM-dd HH:mm:ss"}, options = {"locale=EN", "timezone=UTC"})
    public void setDate(Date value) {
        date = value.toInstant();
    }

    @Parsed(field = "Market")
    public void setMarket(String value) {
        final CurrencyPair currencyPair = fastCurrencyPair.get(value);
        if (currencyPair == null) {
            throw new DataValidationException(UNSUPPORTED_CURRENCY_PAIR.concat(value));
        }
        marketBase = currencyPair.getBase();
        marketQuote = currencyPair.getQuote();
    }

    @Parsed(field = "Type")
    public void setType(String value) {
        type = detectTransactionType(value);
    }

    @Parsed(field = "Amount")
    @Replace(expression = IGNORED_CHARS_IN_NUMBER, replacement = "")
    public void setAmount(BigDecimal value) {
        amount = value;
    }

    @Parsed(field = "Total")
    @Replace(expression = IGNORED_CHARS_IN_NUMBER, replacement = "")
    public void setTotal(BigDecimal value) {
        total = value;
    }

    @Parsed(field = "Fee")
    @Replace(expression = IGNORED_CHARS_IN_NUMBER, replacement = "")
    public void setFee(BigDecimal value) {
        fee = value;
    }

    @Parsed(field = "Fee Coin")
    public void setFeeCurrency(String value) {
        try {
            feeCoin = Currency.fromCode(value);
        } catch (IllegalArgumentException e) {
            feeCoin = null;
        }
    }

    @Override
    public TransactionCluster toTransactionCluster() {
        validateCurrencyPair(marketBase, marketQuote);
        validatePositivity(amount, total, fee);

        final boolean isIncorrectFeeCoin = (feeCoin == null);

        final List<ImportedTransactionBean> related;

        if (isIncorrectFeeCoin || equalsToZero(fee)) {
            related = Collections.emptyList();
        } else {
            related = List.of(
                new FeeRebateImportedTransactionBean(
                    null,
                    date,
                    feeCoin,
                    feeCoin,
                    TransactionType.FEE,
                    fee.setScale(ParserUtils.DECIMAL_DIGITS, ParserUtils.ROUNDING_MODE),
                    feeCoin
                )
            );
        }

        TransactionCluster cluster = new TransactionCluster(
            new BuySellImportedTransactionBean(
                null,
                date,
                marketBase,
                marketQuote,
                type,
                amount.setScale(ParserUtils.DECIMAL_DIGITS, ParserUtils.ROUNDING_MODE),
                evalUnitPrice(total, amount)
            ),
            related
        );
        if (isIncorrectFeeCoin) {
            cluster.setFailedFee(1, "Fee " + (feeCoin != null ? feeCoin.code() : "null") + " currency is neither base or quote");
        } else if (nullOrZero(fee)) {
//            cluster.setIgnoredFee(1, "Fee amount is 0 " + (feeCoin != null ? feeCoin.code() : ""));
        }
        return cluster;
    }
}
