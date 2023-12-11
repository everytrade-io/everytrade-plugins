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
import java.util.Map;

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
        try {
            var pairs = pair.replace("\"","");
            CurrencyPair currPair = findKrakenCurrencyPair(pairs);
            this.pairBase = currPair.getBase();
            this.pairQuote = currPair.getQuote();
        } catch (Exception ignored) {
            findStandardPair(pair);
        }
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

    private CurrencyPair findKrakenCurrencyPair(String pairCode) {

        Map<String, Currency> currencyShortCodes = KrakenCurrencyUtil.CURRENCY_SHORT_CODES;
        Map<String, Currency> currencyLongCodes = KrakenCurrencyUtil.CURRENCY_LONG_CODES;

        List<String> matchedShortCodes = currencyShortCodes
            .keySet()
            .stream()
            .filter(prefix -> (pairCode.startsWith(prefix) && (currencyShortCodes.containsKey(pairCode.replaceFirst(prefix, "")) ||
                currencyLongCodes.containsKey(pairCode.replaceFirst(prefix, "")))))
            .toList();
        List<String> matchedLongCodes = currencyLongCodes
            .keySet()
            .stream()
            .filter(prefix -> (pairCode.startsWith(prefix) && (currencyShortCodes.containsKey(pairCode.replaceFirst(prefix, "")) ||
                currencyLongCodes.containsKey(pairCode.replaceFirst(prefix, "")))))
            .toList();

        if (matchedLongCodes.size() == 1 && matchedShortCodes.isEmpty()) {
            return new CurrencyPair(matchedLongCodes.get(0), pairCode.replaceFirst(matchedLongCodes.get(0), ""));
        } else if (matchedShortCodes.size() == 1 && matchedLongCodes.isEmpty()) {
            return new CurrencyPair(matchedShortCodes.get(0), pairCode.replaceFirst(matchedShortCodes.get(0), ""));
        } else {
            throw new DataValidationException(String.format(
                "Unknown currency code in pair code %s.",
                pairCode
            ));
        }
    }


    private void findStandardPair(String pair) {
        try {
            CurrencyPair currPair = findKrakenCurrencyPair(pair.replaceAll("Z", ""));
            this.pairBase = currPair.getBase();
            this.pairQuote = currPair.getQuote();
        } catch (Exception ignore) {
            try {
                CurrencyPair currPair = findKrakenCurrencyPair(pair.replaceFirst("X", ""));
                this.pairBase = currPair.getBase();
                this.pairQuote = currPair.getQuote();
            } catch (Exception e) {
                try {
                    if (pair.length() == 6) {
                        this.pairBase = KrakenCurrencyUtil.fromCode(pair.substring(0, 3));
                        String quote = pair.substring(3, 6);
                        this.pairQuote = KrakenCurrencyUtil.fromCode(quote);
                    } else if (pair.length() == 7) {
                        try {
                            this.pairBase = KrakenCurrencyUtil.fromCode(pair.substring(0, 4));
                            this.pairQuote = KrakenCurrencyUtil.fromCode(pair.substring(4, 7));
                        } catch (Exception ign) {
                            this.pairBase = KrakenCurrencyUtil.fromCode(pair.substring(0, 3));
                            String quote = pair.substring(3, 7);
                            this.pairQuote = KrakenCurrencyUtil.fromCode(quote);
                        }
                    } else if (pair.length() == 8) {
                        this.pairBase = KrakenCurrencyUtil.fromCode(pair.substring(0, 4));
                        String quote = pair.substring(4, 8);
                        this.pairQuote = KrakenCurrencyUtil.fromCode(quote);
                    } else if (pair.contains("Z") && pair.contains("X")) {
                        CurrencyPair currPair = findKrakenCurrencyPair(pair.replaceAll("Z", "").replaceFirst("X", ""));
                        this.pairBase = currPair.getBase();
                        this.pairQuote = currPair.getQuote();
                    }
                } catch (Exception ex) {
                    throw new DataValidationException(String.format("Can not parse pair %s.", pair));
                }
            }
        }
    }


}
