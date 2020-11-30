package io.everytrade.server.plugin.impl.everytrade.parser.exchange;

import com.univocity.parsers.annotations.Headers;
import com.univocity.parsers.annotations.Parsed;
import com.univocity.parsers.annotations.Replace;
import com.univocity.parsers.common.DataValidationException;
import io.everytrade.server.model.Currency;
import io.everytrade.server.model.SupportedExchange;
import io.everytrade.server.model.TransactionType;
import io.everytrade.server.plugin.api.parser.ImportDetail;
import io.everytrade.server.plugin.api.parser.ImportedTransactionBean;
import io.everytrade.server.plugin.impl.everytrade.parser.ParserUtils;
import io.everytrade.server.plugin.impl.everytrade.parser.postprocessor.ConversionParams;

import java.math.BigDecimal;
import java.math.RoundingMode;

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

    @Parsed(field = "#")
    public void setUid(String value) {
        uid = value;
    }

    @Parsed(field = "PAIR")
    public void setPair(String value) {
        final String[] values = value.split("/");
        pairBase = Currency.valueOf(values[0]);
        pairQuote = Currency.valueOf(values[1]);
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
            feeCurrency = Currency.valueOf(value);
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


    @Override
    public ImportedTransactionBean toImportedTransactionBean(ConversionParams conversionParams) {
        validateCurrencyPair(pairBase, pairQuote);

        TransactionType transactionType = amount.compareTo(BigDecimal.ZERO) > 0
            ? TransactionType.BUY : TransactionType.SELL;

        final boolean isIncorrectFeeCurr
            = feeCurrency == null || !(feeCurrency.equals(pairBase) || feeCurrency.equals(pairQuote));
        final BigDecimal coefFeeBase;
        final BigDecimal coefFeeQuote;
        if (isIncorrectFeeCurr) {
            coefFeeBase = BigDecimal.ZERO;
            coefFeeQuote = BigDecimal.ZERO;
        } else {
            if (TransactionType.BUY.equals(transactionType)) {
                if (feeCurrency.equals(pairBase)) {
                    coefFeeBase = BigDecimal.ONE;
                    coefFeeQuote = BigDecimal.ZERO;
                } else {
                    coefFeeBase = BigDecimal.ZERO;
                    coefFeeQuote = BigDecimal.ONE.negate();
                }
            } else {
                if (feeCurrency.equals(pairBase)) {
                    coefFeeBase = BigDecimal.ONE.negate();
                    coefFeeQuote = BigDecimal.ZERO;
                } else {
                    coefFeeBase = BigDecimal.ZERO;
                    coefFeeQuote = BigDecimal.ONE;
                }
            }
        }
        final BigDecimal baseQuantity = amount.abs().add(coefFeeBase.multiply(fee));
        final BigDecimal quoteVolume = amount.abs().multiply(price).add(coefFeeQuote.multiply(fee));
        final BigDecimal unitPrice = evalUnitPrice(quoteVolume, baseQuantity);

        validatePositivity(baseQuantity, quoteVolume, unitPrice);

        return new ImportedTransactionBean(
            uid,               //uuid
            ParserUtils.parse(conversionParams.getDateTimePattern(), date),          //executed
            pairBase,          //base
            pairQuote,         //quote
            transactionType,   //action
            baseQuantity.setScale(ParserUtils.DECIMAL_DIGITS, RoundingMode.HALF_UP),      //base quantity
            unitPrice,         //unit price
            BigDecimal.ZERO,    //fee quote
            new ImportDetail(isIncorrectFeeCurr)
        );
    }
}
