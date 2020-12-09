package io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean;

import com.univocity.parsers.annotations.Format;
import com.univocity.parsers.annotations.Headers;
import com.univocity.parsers.annotations.Parsed;
import com.univocity.parsers.annotations.Replace;
import com.univocity.parsers.common.DataValidationException;
import io.everytrade.server.model.Currency;
import io.everytrade.server.model.CurrencyPair;
import io.everytrade.server.model.TransactionType;
import io.everytrade.server.plugin.api.parser.TransactionCluster;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.ExchangeBean;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static io.everytrade.server.model.CurrencyPair.getTradeablePairs;

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
            feeCoin = Currency.valueOf(value);
        } catch (IllegalArgumentException e) {
            feeCoin = null;
        }
    }

    @Override
    public TransactionCluster toTransactionCluster() {
        //TODO: mcharvat - implement
        return null;
//        validateCurrencyPair(marketBase, marketQuote);
//
//        final boolean isIncorrectFeeCoin
//            = feeCoin == null || !(feeCoin.equals(marketBase) || feeCoin.equals(marketQuote));
//        final BigDecimal coefFeeBase;
//        final BigDecimal coefFeeQuote;
//        if (isIncorrectFeeCoin) {
//            coefFeeBase = BigDecimal.ZERO;
//            coefFeeQuote = BigDecimal.ZERO;
//        } else {
//            if (TransactionType.BUY.equals(type)) {
//                if (feeCoin.equals(marketBase)) {
//                    coefFeeBase = BigDecimal.ONE.negate();
//                    coefFeeQuote = BigDecimal.ZERO;
//                } else {
//                    coefFeeBase = BigDecimal.ZERO;
//                    coefFeeQuote = BigDecimal.ONE;
//                }
//            } else {
//                if (feeCoin.equals(marketBase)) {
//                    coefFeeBase = BigDecimal.ONE;
//                    coefFeeQuote = BigDecimal.ZERO;
//                } else {
//                    coefFeeBase = BigDecimal.ZERO;
//                    coefFeeQuote = BigDecimal.ONE.negate();
//                }
//            }
//        }
//        final BigDecimal baseQuantity = amount.abs().add(coefFeeBase.multiply(fee));
//        final BigDecimal quoteVolume = total.abs().add(coefFeeQuote.multiply(fee));
//        final BigDecimal unitPrice = evalUnitPrice(quoteVolume, baseQuantity);
//
//        validatePositivity(baseQuantity, quoteVolume, unitPrice);
//
//        return new ImportedTransactionBean(
//            null,         //uuid
//            date,              //executed
//            marketBase,        //base
//            marketQuote,       //quote
//            type,              //action
//            baseQuantity.setScale(ParserUtils.DECIMAL_DIGITS, RoundingMode.HALF_UP),      //base quantity
//            unitPrice,         //unit price
//            BigDecimal.ZERO,    //fee quote
//            new ImportDetail(isIncorrectFeeCoin)
//        );
    }
}
