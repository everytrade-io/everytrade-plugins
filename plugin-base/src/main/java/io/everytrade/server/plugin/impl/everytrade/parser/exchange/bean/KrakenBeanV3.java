package io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean;

import com.univocity.parsers.annotations.Convert;
import com.univocity.parsers.annotations.Format;
import com.univocity.parsers.annotations.Headers;
import com.univocity.parsers.annotations.Parsed;
import com.univocity.parsers.common.DataValidationException;
import io.everytrade.server.model.Currency;
import io.everytrade.server.model.CurrencyPair;
import io.everytrade.server.model.TransactionType;
import io.everytrade.server.plugin.api.parser.FeeRebateImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.ImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.TransactionCluster;
import io.everytrade.server.plugin.impl.everytrade.parser.ParserUtils;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.ExchangeBean;
import io.everytrade.server.util.KrakenCurrencyUtil;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.Date;
import java.util.List;

@Headers(sequence = {"txid", "ordertxid", "pair", "time", "type", "ordertype", "price"
    , "cost", "fee", "vol", "margin", "misc", "ledgers"}, extract = true)
public class KrakenBeanV3 extends ExchangeBean {
    private String txid;
    private String orderTxId;
    private Currency pairBase;
    private Currency pairQuote;
    private Instant time;
    private String timek;
    private TransactionType type;
    private BigDecimal cost;
    private BigDecimal fee;
    private BigDecimal vol;
    private String orderType;
    private BigDecimal price;
    private BigDecimal margin;

    @Parsed(field = "txid")
    public void setTxid(String txid) {
        this.txid = txid;
    }

    @Parsed(field = "ordertxid")
    public void setOrderTxId(String orderTxId) {
        this.orderTxId = orderTxId.replace("\"", "");
    }

    @Parsed(field = "pair")
    public void setPair(String pair) {
        CurrencyPair currPair = KrakenCurrencyUtil.parseKrakenPair(pair);
        this.pairBase = currPair.getBase();
        this.pairQuote = currPair.getQuote();
    }

    @Parsed(field = "time")
    @Format(formats = {"yyyy-MM-dd HH:mm:ss.SSSS", "yyyy-MM-dd HH:mm:ss.SSS", "yyyy-MM-dd HH:mm:ss.SS"}, options = {"locale=EN",
        "timezone=UTC"})
    public void setTime(Date time) {
        this.time = time.toInstant();
    }

    @Parsed(field = "type")
    public void setType(String type) {
        this.type = detectTransactionType(type);
    }

    @Parsed(field = "ordertype")
    public void setOrderType(String ordertype) {
        this.orderType = ordertype;
    }

    @Parsed(field = "price", defaultNullRead = "0")
    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    @Parsed(field = "cost", defaultNullRead = "0")
    public void setCost(BigDecimal cost) {
        this.cost = cost;
    }

    @Parsed(field = "fee", defaultNullRead = "0")
    public void setFee(BigDecimal fee) {
        this.fee = fee;
    }

    @Parsed(field = "vol", defaultNullRead = "0")
    public void setVol(String vol) {
        BigDecimal vole = new BigDecimal(vol);
        if (vole.compareTo(BigDecimal.ZERO) == 0) {
            throw new DataValidationException("BaseQuantity can not be zero.");
        }
        this.vol = vole;
    }

    @Parsed(field = "margin", defaultNullRead = "0")
    public void setMargin(BigDecimal margin) {
        this.margin = margin;
    }

    @Parsed(field = "misc")
    public void setMisc(String misc) {
    }

    @Parsed(field = "ledgers")
    public void setLedgers(String ledgers) {
    }

    @Override
    public TransactionCluster toTransactionCluster() {
        validateCurrencyPair(pairBase, pairQuote);
        List<ImportedTransactionBean> related;
        if (ParserUtils.equalsToZero(fee)) {
            related = Collections.emptyList();
        } else {
            related = List.of(
                new FeeRebateImportedTransactionBean(
                    txid + FEE_UID_PART,
                    time,
                    pairQuote,
                    pairQuote,
                    TransactionType.FEE,
                    fee.setScale(ParserUtils.DECIMAL_DIGITS, RoundingMode.HALF_UP),
                    pairQuote
                )
            );
        }

        return new TransactionCluster(
            new ImportedTransactionBean(
                txid,             //uuid
                time,             //executed
                pairBase,         //base
                pairQuote,        //quote
                type,             //action
                vol,              //base quantity
                evalUnitPrice(cost, vol)   //unit price
            ),
            related
        );
    }
}
