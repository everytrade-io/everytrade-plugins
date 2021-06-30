package io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean;

import com.univocity.parsers.annotations.Headers;
import com.univocity.parsers.annotations.Parsed;
import com.univocity.parsers.annotations.Replace;
import com.univocity.parsers.common.DataValidationException;
import io.everytrade.server.model.Currency;
import io.everytrade.server.model.TransactionType;
import io.everytrade.server.plugin.api.parser.BuySellImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.FeeRebateImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.ImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.TransactionCluster;
import io.everytrade.server.plugin.impl.everytrade.parser.ParserUtils;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.ExchangeBean;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Collections;
import java.util.List;

@Headers(
    sequence = {
        "trade id", "product", "side", "created at", "size", "size unit", "price", "fee", "price/fee/total unit"
    },
    extract = true
)
public class CoinbaseProBeanV1 extends ExchangeBean {

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

    @Parsed(field = "trade id")
    public void setTradeId(String value) {
        tradeId = value;
    }

    @Parsed(field = "product")
    public void setProduct(String value) {
        final String[] split = value.split("-");
        productBase = Currency.fromCode(split[0]);
        productQuote = Currency.fromCode(split[1]);
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
        sizeUnit = Currency.fromCode(value);
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
        setPriceFeeTotalUnit = Currency.fromCode(value);
    }

    @Override
    public TransactionCluster toTransactionCluster() {
        if (!sizeUnit.equals(productBase)) {
            throw new DataValidationException(String.format(
                BASE_DIFFERS_FROM_UNIT_SIZE,
                productBase.code(), sizeUnit.code()
            ));
        }

        if (!setPriceFeeTotalUnit.equals(productQuote)) {
            throw new DataValidationException(String.format(
                QUOTE_DIFFERS_FROM_PRICE_FEE_TOTAL_UNIT,
                productQuote.code(), setPriceFeeTotalUnit.code())
            );
        }
        validateCurrencyPair(productBase, productQuote);

        List<ImportedTransactionBean> related;
        if (ParserUtils.equalsToZero(fee)) {
            related = Collections.emptyList();
        } else {
            related = List.of(
                new FeeRebateImportedTransactionBean(
                    tradeId + FEE_UID_PART,
                    createdAt,
                    productBase,
                    productQuote,
                    TransactionType.FEE,
                    fee.setScale(ParserUtils.DECIMAL_DIGITS, RoundingMode.HALF_UP),
                    productQuote
                )
            );
        }

        return new TransactionCluster(
            new BuySellImportedTransactionBean(
                tradeId,             //uuid
                createdAt,           //executed
                productBase,         //base
                productQuote,        //quote
                side,                //action
                size.abs().setScale(ParserUtils.DECIMAL_DIGITS, RoundingMode.HALF_UP),         //base quantity
                price.abs().setScale(ParserUtils.DECIMAL_DIGITS, RoundingMode.HALF_UP)        //unit price

            ),
            related
        );
    }
}
