package io.everytrade.server.plugin.impl.everytrade.parser.exchange;

import com.univocity.parsers.annotations.Format;
import com.univocity.parsers.annotations.Headers;
import com.univocity.parsers.annotations.Parsed;
import io.everytrade.server.model.Currency;
import io.everytrade.server.model.SupportedExchange;
import io.everytrade.server.model.TransactionType;
import io.everytrade.server.plugin.api.parser.ExchangeBean;
import io.everytrade.server.plugin.api.parser.ImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.postparse.ConversionParams;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Date;

//MIN>  ETR-001:|UID|DATE|SYMBOL|ACTION|QUANTY|PRICE|FEE|
//FULL> ETR-001:|UID|DATE|SYMBOL|ACTION|QUANTY|PRICE|FEE|
@Headers(sequence = {"UID", "DATE", "SYMBOL", "ACTION", "QUANTY", "PRICE", "FEE"}, extract = true)
public class EveryTradeBeanV1 extends ExchangeBean {
    private String uid;
    private Instant date;
    private Currency symbolBase;
    private Currency symbolQuote;
    private TransactionType action;
    private BigDecimal quantity;
    private BigDecimal price;
    private BigDecimal fee;

    public EveryTradeBeanV1() {
        super(SupportedExchange.EVERYTRADE);
    }

    @Parsed(field = "UID")
    public void setUid(String uid) {
        this.uid = uid;
    }

    @Parsed(field = "DATE")
    @Format(formats = {"dd.MM.yy HH:mm:ss", "yyyy-MM-dd HH:mm:ss"}, options = {"locale=US", "timezone=UTC"})
    public void setDate(Date date) {
        this.date = date.toInstant();
    }

    @Parsed(field = "SYMBOL")
    public void setSymbol(String symbol) {
        String[] symbolParts = symbol.split("/");
        symbolBase = Currency.valueOf(symbolParts[0]);
        symbolQuote = Currency.valueOf(symbolParts[1]);
    }

    @Parsed(field = "ACTION")
    public void setAction(String action) {
        this.action = detectTransactionType(action);
    }

    @Parsed(field = "QUANTY", defaultNullRead = "0")
    public void setQuantityBase(BigDecimal quantity) {
        this.quantity = quantity;
    }

    @Parsed(field = "PRICE", defaultNullRead = "0")
    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    @Parsed(field = "FEE", defaultNullRead = "0")
    public void setFee(BigDecimal fee) {
        this.fee = fee;
    }

    @Override
    public ImportedTransactionBean toImportedTransactionBean(ConversionParams conversionParams) {
        validateCurrencyPair(symbolBase, symbolQuote);
        return new ImportedTransactionBean(
            uid,               //uuid
            date,               //executed
            symbolBase,         //base
            symbolQuote,        //quote
            action,             //action
            quantity,           //base quantity
            price,              //unit price
            fee                 //fee quote
        );
    }
}
