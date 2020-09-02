package io.everytrade.server.parser.exchange;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
//import com.univocity.parsers.annotations.Format;
//import com.univocity.parsers.annotations.Headers;
//import com.univocity.parsers.annotations.Parsed;
import com.univocity.parsers.common.DataValidationException;
import io.everytrade.server.model.CurrencyPair;
import io.everytrade.server.model.TransactionType;
import io.everytrade.server.model.Currency;
import io.everytrade.server.plugin.api.parser.ImportedTransactionBean;
//import io.everytrade.server.parser.postparse.ConversionParams;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Date;

@JsonFormat(shape = JsonFormat.Shape.ARRAY)
@JsonPropertyOrder({"uid", "timestamp", "base", "quote", "action", "quantity", "volume", "fee"})
//@Headers(sequence = {"UID", "TIMESTAMP", "BASE", "QUOTE", "ACTION", "QUANTITY", "VOLUME", "FEE"}, extract = true)
public class EveryTradeApiTransactionBean /*extends ExchangeBean*/ {
    private String uid;
    private Instant timestamp;
    private String base;
    private String quote;
    private String action;
    private BigDecimal quantity;
    private BigDecimal volume;
    private BigDecimal fee;

//    public EveryTradeApiTransactionBean() {
//        super(SupportedExchange.EVERYTRADE);
//    }

//    @Parsed(field = "UID")
    public void setUid(String uid) {
        this.uid = uid;
    }

//    @Parsed(field = "TIMESTAMP")
//    @Format(formats = {"dd.MM.yy HH:mm:ss"}, options = {"locale=US", "timezone=UTC"})
    public void setTimestamp(Date timestamp) {
        this.timestamp = timestamp.toInstant();
    }

//    @Parsed(field = "BASE")
    public void setBase(String symbol) {
        base = symbol;
    }

//    @Parsed(field = "QUOTE")
    public void setQuote(String symbol) {
        quote = symbol;
    }

//    @Parsed(field = "ACTION")
    public void setAction(String action) {
        this.action = action;
    }

//    @Parsed(field = "QUANTITY", defaultNullRead = "0")
    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }

//    @Parsed(field = "VOLUME", defaultNullRead = "0")
    public void setVolume(BigDecimal volume) {
        this.volume = volume;
    }

//    @Parsed(field = "FEE", defaultNullRead = "0")
    public void setFee(BigDecimal fee) {
        this.fee = fee;
    }

//    @Override
//    public ImportedTransactionBean toImportedTransactionBean(ConversionParams conversionParams) {
//        final Currency base = Currency.valueOf(this.base);
//        final Currency quote = Currency.valueOf(this.quote);
//        validateCurrencyPair(base, quote);
//
//        return new ImportedTransactionBean(
//            uid,                             //uuid
//            timestamp,                       //executed
//            base,                            //base
//            quote,                           //quote
//            TransactionType.valueOf(action), //action
//            quantity,                        //base quantity
//            evalUnitPrice(volume, quantity), //unit price
//            volume,                          //transaction price
//            fee                              //fee quote
//        );
//    }

    public ImportedTransactionBean toImportedTransactionBean() {
        final Currency base = Currency.valueOf(this.base);
        final Currency quote = Currency.valueOf(this.quote);
        try {
            new CurrencyPair(base, quote);
        } catch (CurrencyPair.FiatCryptoCombinationException e) {
            throw new DataValidationException(e.getMessage());
        }


        return new ImportedTransactionBean(
            uid,                             //uuid
            timestamp,                       //executed
            base,                            //base
            quote,                           //quote
            TransactionType.valueOf(action), //action
            quantity,                        //base quantity
            volume.divide(quantity, 10, RoundingMode.HALF_UP), //unit price
            volume,                          //transaction price
            fee                              //fee quote
        );
    }

    @Override
    public String toString() {
        return "EveryTradeApiTransactionBean{" +
            "uid='" + uid + '\'' +
            ", timestamp=" + timestamp +
            ", base='" + base + '\'' +
            ", quote='" + quote + '\'' +
            ", action='" + action + '\'' +
            ", quantity=" + quantity +
            ", volume=" + volume +
            ", fee=" + fee +
            '}';
    }
}
