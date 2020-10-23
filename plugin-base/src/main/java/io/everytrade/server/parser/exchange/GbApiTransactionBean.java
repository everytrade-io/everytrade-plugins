package io.everytrade.server.parser.exchange;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.univocity.parsers.common.DataValidationException;
import io.everytrade.server.model.Currency;
import io.everytrade.server.model.CurrencyPair;
import io.everytrade.server.model.TransactionType;
import io.everytrade.server.plugin.api.parser.ImportedTransactionBean;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Date;

@JsonFormat(shape = JsonFormat.Shape.ARRAY)
@JsonPropertyOrder({"uid", "timestamp", "base", "quote", "action", "quantity", "volume", "fee", "status"})
//@Headers(
//    sequence = {"UID", "TIMESTAMP", "BASE", "QUOTE", "ACTION", "QUANTITY", "VOLUME", "FEE", "STATUS"},
//    extract = true
//)
public class GbApiTransactionBean {
    private String uid;
    private Instant timestamp;
    private String base;
    private String quote;
    private String action;
    private BigDecimal quantity;
    private BigDecimal volume;
    private BigDecimal fee;
    private String status;

//    public GbApiTransactionBean() {
//        super(SupportedExchange.GENERAL_BYTES);
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

//    @Parsed(field = "STATUS")
    public void setStatus(String status) {
        this.status = status;
    }

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
            fee                              //fee quote
        );
    }

    public boolean isImportable() {
        return status == null || isImportable(status);
    }

    public static boolean isImportable(String status) {
        return status.startsWith("COMPLETED") ||
            status.contains("PAYMENT ARRIVED") ||
            status.contains("ERROR (EXCHANGE PURCHASE)");
    }

    @Override
    public String toString() {
        return "GbApiTransactionBean{" +
            "uid='" + uid + '\'' +
            ", timestamp=" + timestamp +
            ", base='" + base + '\'' +
            ", quote='" + quote + '\'' +
            ", action='" + action + '\'' +
            ", quantity=" + quantity +
            ", volume=" + volume +
            ", fee=" + fee +
            ", status=" + status +
            '}';
    }
}
