package io.everytrade.server.plugin.impl.everytrade.parser.exchange;

import com.univocity.parsers.annotations.Headers;
import com.univocity.parsers.annotations.Parsed;
import com.univocity.parsers.annotations.Replace;
import com.univocity.parsers.common.DataValidationException;
import io.everytrade.server.model.Currency;
import io.everytrade.server.model.SupportedExchange;
import io.everytrade.server.model.TransactionType;
import io.everytrade.server.plugin.api.parser.ExchangeBean;
import io.everytrade.server.plugin.api.parser.ImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.postparse.ConversionParams;
import io.everytrade.server.plugin.api.parser.ParserUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

//MIN> CBS-001:|trade id|product|side|created at|size|size unit|price|fee|price/fee/total unit|
//FULL> CBS-001:|portfolio|trade id|product|side|created at|size|size unit|price|fee|total|price/fee/total unit|
@Headers(
    sequence = {
        "trade id", "product", "side", "created at", "size", "size unit", "price", "fee", "price/fee/total unit"
    },
    extract = true
)
public class CoinbaseBeanV1 extends ExchangeBean {

    public static final String UNSUPPORTED_TRANSACTION_TYPE = "Unsupported transaction type ";
    public static final String QUOTE_DIFFERS_FROM_PRICE_FEE_TOTAL_UNIT
        = "Quote (%s) differs from price/fee/total unit (%s).";
    public static final String BASE_DIFFERS_FROM_UNIT_SIZE = "Base (%s) differs from unit size (%s).";
    private String tradeId;
    private Currency productBase;
    private Currency productQuote;
    private TransactionType side;
    private Instant createdAt;
    private BigDecimal size;
    private BigDecimal price;
    private BigDecimal fee;

    //for validation only
    private Currency sizeUnit;
    private Currency setPriceFeeTotalUnit;

    public CoinbaseBeanV1() {
        super(SupportedExchange.COINBASE);
    }


    @Parsed(field = "trade id")
    public void setTradeId(String value) {
        tradeId = value;
    }

    @Parsed(field = "product")
    public void setProduct(String value) {
        final String[] split = value.split("-");
        productBase = Currency.valueOf(split[0]);
        productQuote = Currency.valueOf(split[1]);
    }

    @Parsed(field = "side")
    public void setSide(String value) {
        side = detectTransactionType(value);
    }

    @Parsed(field = "created at")
    public void setCreatedAt(String value) {
        createdAt = Instant.parse(value);
    }

    @Parsed(field = "size")
    @Replace(expression = IGNORED_CHARS_IN_NUMBER, replacement = "")
    public void setSize(BigDecimal value) {
        size = value;
    }

    @Parsed(field = "size unit")
    public void setSizeUnit(String value) {
        sizeUnit = Currency.valueOf(value);
    }

    @Parsed(field = "price")
    @Replace(expression = IGNORED_CHARS_IN_NUMBER, replacement = "")
    public void setPrice(BigDecimal value) {
        price = value;
    }

    @Parsed(field = "fee")
    @Replace(expression = IGNORED_CHARS_IN_NUMBER, replacement = "")
    public void setFee(BigDecimal value) {
        fee = value;
    }

    @Parsed(field = "price/fee/total unit")
    public void setPriceFeeTotalUnit(String value) {
        setPriceFeeTotalUnit = Currency.valueOf(value);
    }

    @Override
    public ImportedTransactionBean toImportedTransactionBean(ConversionParams conversionParams) {
        if (!sizeUnit.equals(productBase)) {
            throw new DataValidationException(String.format(
                BASE_DIFFERS_FROM_UNIT_SIZE,
                productBase.name(), sizeUnit.name()
            ));
        }

        if (!setPriceFeeTotalUnit.equals(productQuote)) {
            throw new DataValidationException(String.format(
                QUOTE_DIFFERS_FROM_PRICE_FEE_TOTAL_UNIT,
                productQuote.name(), setPriceFeeTotalUnit.name())
            );
        }
        validateCurrencyPair(productBase, productQuote);

        return new ImportedTransactionBean(
            tradeId,             //uuid
            createdAt,           //executed
            productBase,         //base
            productQuote,        //quote
            side,                //action
            size.abs().setScale(ParserUtils.DECIMAL_DIGITS, RoundingMode.HALF_UP),         //base quantity
            price.abs().setScale(ParserUtils.DECIMAL_DIGITS, RoundingMode.HALF_UP),        //unit price
            fee.setScale(ParserUtils.DECIMAL_DIGITS, RoundingMode.HALF_UP)                 //fee quote
        );
    }
}
