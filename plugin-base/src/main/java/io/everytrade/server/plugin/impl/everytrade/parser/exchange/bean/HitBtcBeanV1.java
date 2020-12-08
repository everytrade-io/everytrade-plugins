package io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean;

import com.univocity.parsers.annotations.Format;
import com.univocity.parsers.annotations.Headers;
import com.univocity.parsers.annotations.Parsed;
import io.everytrade.server.model.Currency;
import io.everytrade.server.model.TransactionType;
import io.everytrade.server.plugin.api.parser.ImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.TransactionCluster;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.ExchangeBean;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Date;

@Headers(sequence = {"Instrument", "Trade ID", "Side", "Quantity", "Price", "Fee", "Rebate"}, extract = true)
public class HitBtcBeanV1 extends ExchangeBean {
    private Instant date;
    private Currency instrumentBase;
    private Currency instrumentQuote;
    private String tradeId;
    private TransactionType side;
    private BigDecimal quantity;
    private BigDecimal price;
    private BigDecimal fee;
    private BigDecimal rebate;

    //Date
    @Parsed(index = 0)
    @Format(formats = {"yyyy-MM-dd HH:mm:ss", "dd.MM.yyyy HH:mm"}, options = {"locale=EN", "timezone=UTC"})
    public void setDate(Date date) {
        this.date = date.toInstant();
    }

    @Parsed(field = "Instrument")
    public void setInstrument(String instrument) {
        String[] instrumentParts = instrument.split("/");
        instrumentBase = Currency.valueOf(instrumentParts[0]);
        instrumentQuote = Currency.valueOf(instrumentParts[1]);
    }

    @Parsed(field = "Trade ID")
    public void setTradeId(String tradeId) {
        this.tradeId = tradeId;
    }

    @Parsed(field = "Side")
    public void setSide(String side) {
       this.side = detectTransactionType(side);
    }

    @Parsed(field = "Quantity", defaultNullRead = "0")
    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }

    @Parsed(field = "Price", defaultNullRead = "0")
    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    @Parsed(field = "Fee", defaultNullRead = "0")
    public void setFee(BigDecimal fee) {
        this.fee = fee;
    }

    @Parsed(field = "Rebate", defaultNullRead = "0")
    public void setRebate(BigDecimal rebate) {
        this.rebate = rebate;
    }

    @Override
    public TransactionCluster toTransactionCluster() {
        //TODO: mcharvat - implement
        return null;
//        validateCurrencyPair(instrumentBase, instrumentQuote);
//
//        return new ImportedTransactionBean(
//            tradeId,            //uuid
//            date,               //executed
//            instrumentBase,     //base
//            instrumentQuote,    //quote
//            side,               //action
//            quantity,           //base quantity
//            price,              //unit price
//            fee.subtract(rebate) //fee Quote
//        );
    }
}
