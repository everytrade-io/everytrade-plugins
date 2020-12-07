package io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean;

import com.univocity.parsers.annotations.Convert;
import com.univocity.parsers.annotations.Headers;
import com.univocity.parsers.annotations.Parsed;
import com.univocity.parsers.common.DataValidationException;
import io.everytrade.server.model.Currency;
import io.everytrade.server.model.TransactionType;
import io.everytrade.server.plugin.api.parser.ImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.TransactionCluster;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.ExchangeBean;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Headers(sequence = {"txid", "pair", "time", "type", "cost", "fee", "vol"}, extract = true)
public class KrakenBeanV1 extends ExchangeBean {
    private String txid;
    private Currency pairBase;
    private Currency pairQuote;
    private Instant time;
    private TransactionType type;
    private BigDecimal cost;
    private BigDecimal fee;
    private BigDecimal vol;
    private static final Map<String, Currency> CURRENCIES = new HashMap<>() {};
    static {
        CURRENCIES.put("XXBT", Currency.BTC);
        CURRENCIES.put("XETH", Currency.ETH);
        CURRENCIES.put("XLTC", Currency.LTC);
        CURRENCIES.put("BCH", Currency.BCH);
        CURRENCIES.put("XXRP", Currency.XRP);
        CURRENCIES.put("XXMR", Currency.XMR);
        CURRENCIES.put("DASH", Currency.DASH);
        CURRENCIES.put("DAI", Currency.DAI);

        CURRENCIES.put("ZCZK", Currency.CZK);
        CURRENCIES.put("ZUSD", Currency.USD);
        CURRENCIES.put("ZEUR", Currency.EUR);
        CURRENCIES.put("ZCAD", Currency.CAD);
    }

    @Parsed(field = "txid")
    public void setTxid(String txid) {
        this.txid = txid;
    }

    @Parsed(field = "pair")
    public void setPair(String pair) {
        String mBase = findStarts(pair);
        String mQuote = findEnds(pair);
        if (!pair.equals(mBase.concat(mQuote))) {
            throw new DataValidationException(String.format("Can not parse pair %s.", pair));
        }
        if (!CURRENCIES.containsKey(mBase) || !CURRENCIES.containsKey(mQuote)) {
            throw new DataValidationException(String.format("Unknown pair base(%s) or pair quote(%s).", mBase, mQuote));
        }
        this.pairBase = CURRENCIES.get(mBase);
        this.pairQuote = CURRENCIES.get(mQuote);
    }

    @Parsed(field = "time")
    @Convert(conversionClass=DateTimeConverterWithSecondsFraction.class, args="yyyy-MM-dd HH:mm:ss")
    public void setTime(Instant time) {
        this.time = time;
    }

    @Parsed(field = "type")
    public void setType(String type) {
        this.type = detectTransactionType(type);
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
    public void setVol(BigDecimal vol) {
        if (vol.compareTo(BigDecimal.ZERO) == 0) {
            throw new DataValidationException("BaseQuantity can not be zero.");
        }
        this.vol = vol;
    }

    @Override
    public TransactionCluster toTransactionCluster() {
        //TODO: mcharvat - implement
        return null;
//        validateCurrencyPair(pairBase, pairQuote);
//
//        return new ImportedTransactionBean(
//            txid,                   //uuid
//            time,                   //executed
//            pairBase,               //base
//            pairQuote,              //quote
//            type,                   //action
//            vol,                    //base quantity
//            evalUnitPrice(cost, vol),   //unit price
//            fee                     //fee quote
//        );
    }

    private String findStarts(String value) {
        List<String> matchedCurrencies = CURRENCIES
            .keySet()
            .stream()
            .filter(value::startsWith)
            .collect(Collectors.toList());
        switch (matchedCurrencies.size()) {
            case 0:
                throw new DataValidationException(String.format("Unknown base currency %s.", value));
            case 1:
                return matchedCurrencies.get(0);
            default:
                throw new IllegalStateException(
                    String.format("Found more (%d) than one base currencies.", matchedCurrencies.size())
                );
        }
    }

    private String findEnds(String value) {
        List<String> matchedCurrencies = CURRENCIES
            .keySet()
            .stream()
            .filter(value::endsWith)
            .collect(Collectors.toList());
        switch (matchedCurrencies.size()) {
            case 0:
                throw new DataValidationException(String.format("Unknown quote currency %s.", value));
            case 1:
                return matchedCurrencies.get(0);
            default:
                throw new IllegalStateException(
                    String.format("Found more (%d) than one quote currencies.", matchedCurrencies.size())
                );
        }
    }
}
