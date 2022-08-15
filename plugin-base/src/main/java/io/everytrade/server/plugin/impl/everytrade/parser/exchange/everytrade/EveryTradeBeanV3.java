package io.everytrade.server.plugin.impl.everytrade.parser.exchange.everytrade;

import com.univocity.parsers.annotations.Format;
import com.univocity.parsers.annotations.Headers;
import com.univocity.parsers.annotations.Parsed;
import com.univocity.parsers.common.DataValidationException;
import io.everytrade.server.model.Currency;
import io.everytrade.server.model.CurrencyPair;
import io.everytrade.server.model.TransactionType;
import io.everytrade.server.plugin.api.parser.BuySellImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.FeeRebateImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.ImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.TransactionCluster;
import io.everytrade.server.plugin.impl.everytrade.parser.EverytradeCSVParserValidator;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.ExchangeBean;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;

import static io.everytrade.server.plugin.impl.everytrade.parser.ParserUtils.nullOrZero;

@Headers(sequence = {
    "UID", "DATE", "SYMBOL", "ACTION", "QUANTY", "PRICE", "FEE", "FEE_CURRENCY", "REBATE", "REBATE_CURRENCY"
}, extract = true)
public class EveryTradeBeanV3 extends ExchangeBean {
    private String uid;
    private Instant date;
    private Currency symbolBase;
    private Currency symbolQuote;
    private TransactionType action;
    private BigDecimal quantity;
    private BigDecimal price;
    private BigDecimal fee;
    private Currency feeCurrency;
    private BigDecimal rebate;
    private Currency rebateCurrency;

    @Parsed(field = "UID")
    public void setUid(String value) {
        uid = value;
    }

    @Parsed(field = "DATE")
    @Format(formats = {"dd.MM.yy HH:mm:ss", "yyyy-MM-dd HH:mm:ss"}, options = {"locale=US", "timezone=UTC"})
    public void setDate(Date value) {
        date = value.toInstant();
    }

    @Parsed(field = "SYMBOL")
    public void setSymbol(String value) {
        CurrencyPair symbolParts = EverytradeCSVParserValidator.parseSymbol(value);
        symbolBase = symbolParts.getBase();
        symbolQuote = symbolParts.getQuote();
    }

    @Parsed(field = "ACTION")
    public void setAction(String value) {
        action = detectTransactionType(value);
    }

    @Parsed(field = "QUANTY", defaultNullRead = "0")
    public void setQuantityBase(String value) {
        quantity = EverytradeCSVParserValidator.parserNumber(value);
    }

    @Parsed(field = "PRICE", defaultNullRead = "0")
    public void setPrice(String value) {
        price = EverytradeCSVParserValidator.parserNumber(value);
    }

    @Parsed(field = "FEE", defaultNullRead = "0")
    public void setFee(String value) {
        fee = EverytradeCSVParserValidator.parserNumber(value);
    }

    @Parsed(field = "FEE_CURRENCY")
    public void setFeeCurrency(String value) {
        feeCurrency = value == null ? null : Currency.fromCode(EverytradeCSVParserValidator.correctCurrency(value));
    }

    @Parsed(field = "REBATE", defaultNullRead = "0")
    public void setRebate(String value) {
        rebate =  EverytradeCSVParserValidator.parserNumber(value);
    }

    @Parsed(field = "REBATE_CURRENCY")
    public void setRebateCurrency(String value) {
        rebateCurrency = value == null ? null : Currency.fromCode(EverytradeCSVParserValidator.correctCurrency(value));
    }

    @Override
    public TransactionCluster toTransactionCluster() {
        validateCurrencyPair(symbolBase, symbolQuote, action);
        validatePositivity(quantity, price, fee, rebate);
        switch (action) {
            case BUY:
            case SELL:
              return createBuySellTransactionCluster();
            case FEE:
                return new TransactionCluster(createFeeTransactionBean(true), List.of());
            case REBATE:
                return new TransactionCluster(createRebateTransactionBean(true), List.of());
            default:
                throw new IllegalStateException(String.format("Unsupported transaction type %s.", action));
        }
    }

    private TransactionCluster createBuySellTransactionCluster() {
        if (quantity.compareTo(BigDecimal.ZERO) == 0) {
            throw new DataValidationException("Quantity can not be zero.");
        }
        if (price.compareTo(BigDecimal.ZERO) == 0) {
            throw new DataValidationException("Price can not be zero.");
        }

        final ImportedTransactionBean buySell = new BuySellImportedTransactionBean(
            uid,               //uuid
            date,               //executed
            symbolBase,         //base
            symbolQuote,        //quote
            action,             //action
            quantity,           //base quantity
            price              //unit price
        );

        final List<ImportedTransactionBean> related = new ArrayList<>();
        if (fee.compareTo(BigDecimal.ZERO) > 0) {
            related.add(createFeeTransactionBean(false));
        }

        if (rebate.compareTo(BigDecimal.ZERO) > 0) {
            related.add(createRebateTransactionBean(false));
        }

        TransactionCluster transactionCluster = new TransactionCluster(buySell, related);
        if(nullOrZero(fee)) {
//            transactionCluster.setIgnoredFee(1, "Fee amount is 0 " + (feeCurrency != null ? feeCurrency.code() : ""));
        }
        return transactionCluster;
    }

    private FeeRebateImportedTransactionBean createFeeTransactionBean(boolean unrelated) {
        final boolean feeCurrencyIsBase = Objects.equals(feeCurrency, symbolBase);
        final boolean feeCurrencyIsQuote = Objects.equals(feeCurrency, symbolQuote);
        if (!feeCurrencyIsBase && !feeCurrencyIsQuote) {
            throw new DataValidationException(String.format(
                "Fee currency '%s' differs to base '%s' and to quote '%s'.",
                feeCurrency,
                symbolBase,
                symbolQuote
            ));
        }

        return new FeeRebateImportedTransactionBean(
            unrelated ? uid : uid + FEE_UID_PART,
            date,
            feeCurrency,
            feeCurrency,
            TransactionType.FEE,
            fee,
            feeCurrency
        );
    }

    private FeeRebateImportedTransactionBean createRebateTransactionBean(boolean unrelated) {
        final boolean rebateCurrencyIsBase = Objects.equals(rebateCurrency, symbolBase);
        final boolean rebateCurrencyIsQuote = Objects.equals(rebateCurrency, symbolQuote);
        if (!rebateCurrencyIsBase && !rebateCurrencyIsQuote) {
            throw new DataValidationException(String.format(
                "Rebate currency '%s' differs to base '%s' and to quote '%s'.",
                rebateCurrency,
                symbolBase,
                symbolQuote
            ));
        }

        return new FeeRebateImportedTransactionBean(
            unrelated ? uid : uid + FEE_UID_PART,
            date,
            rebateCurrency,
            rebateCurrency,
            TransactionType.REBATE,
            rebate,
            rebateCurrency
        );
    }
}
