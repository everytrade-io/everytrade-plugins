package io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean;

import com.univocity.parsers.annotations.Convert;
import com.univocity.parsers.annotations.Headers;
import com.univocity.parsers.annotations.Parsed;
import com.univocity.parsers.common.DataValidationException;
import io.everytrade.server.model.Currency;
import io.everytrade.server.model.TransactionType;
import io.everytrade.server.plugin.api.parser.ImportedTransactionBean;
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
    private static final Map<String, Currency> currencies = new HashMap<>() {};
    static {
        currencies.put("XXBT", Currency.BTC);
        currencies.put("XETH", Currency.ETH);
        currencies.put("XLTC", Currency.LTC);
        currencies.put("BCH", Currency.BCH);
        currencies.put("XXRP", Currency.XRP);
        currencies.put("XXMR", Currency.XMR);
        currencies.put("DASH", Currency.DASH);
        currencies.put("DAI", Currency.DAI);

        currencies.put("ZCZK", Currency.CZK);
        currencies.put("ZUSD", Currency.USD);
        currencies.put("ZEUR", Currency.EUR);
        currencies.put("ZCAD", Currency.CAD);
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
        if (!currencies.containsKey(mBase) || !currencies.containsKey(mQuote)) {
            throw new DataValidationException(String.format("Unknown pair base(%s) or pair quote(%s).", mBase, mQuote));
        }
        this.pairBase = currencies.get(mBase);
        this.pairQuote = currencies.get(mQuote);
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
    public ImportedTransactionBean toImportedTransactionBean() {
        validateCurrencyPair(pairBase, pairQuote);

        return new ImportedTransactionBean(
            txid,                   //uuid
            time,                   //executed
            pairBase,               //base
            pairQuote,              //quote
            type,                   //action
            vol,                    //base quantity
            evalUnitPrice(cost, vol),   //unit price
            fee                     //fee quote
        );
    }

    private String findStarts(String value) {
        List<String> matchedCurrencies = currencies
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
        List<String> matchedCurrencies = currencies
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
