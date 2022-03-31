package io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean;

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
import io.everytrade.server.plugin.impl.everytrade.parser.ParserUtils;
import io.everytrade.server.plugin.impl.everytrade.parser.exception.DataIgnoredException;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.ExchangeBean;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Instant;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static io.everytrade.server.model.CurrencyPair.getTradeablePairs;

@Headers(sequence = {"transactTime", "symbol", "execType", "side", "lastQty", "lastPx", "execComm", "orderID"},
    extract = true)
public class BitmexBeanV1 extends ExchangeBean {
    private Instant transactTime;
    private Currency symbolBase;
    private Currency symbolQuote;
    private TransactionType side;
    private BigDecimal lastQty;
    private BigDecimal lastPx;
    private BigDecimal execComm;
    private String orderID;


    private static final BigDecimal SATOSHIS_BY_BTC = BigDecimal.valueOf(100_000_000L);
    private static final Map<String, Currency> CURRENCIES = new HashMap<>() {};
    private static final Map<String, CurrencyPair> TRADABLE_CURRENCY_PAIRS = new HashMap<>();

    static {
        CURRENCIES.put("XBT", Currency.BTC);
        getTradeablePairs().forEach(t -> TRADABLE_CURRENCY_PAIRS.put(t.toString().replace("/", ""), t));
    }

    @Parsed(field = "transactTime")
    @Format(formats = {"MM/dd/yyyy, hh:mm:ss a"}, options = {"locale=US", "timezone=UTC"})
    public void setTransactTime(Date date) {
        transactTime = date.toInstant();
    }

    @Parsed(field = "symbol")
    public void setSymbol(String value) {
        String mappedPair;
        String replaceBase = findStarts(value);
        if (replaceBase != null) {
            mappedPair = value.replaceFirst(replaceBase, CURRENCIES.get(replaceBase).code());
        } else {
            mappedPair = value;
        }
        final CurrencyPair tradablePair = TRADABLE_CURRENCY_PAIRS.get(mappedPair);
        if (tradablePair == null) {
            throw new DataValidationException(String.format("Can not parse pair symbol %s.", value));
        }
        this.symbolBase = tradablePair.getBase();
        this.symbolQuote = tradablePair.getQuote();
    }

    @Parsed(field = "execType")
    public void setExecType(String value) {
        if (!"Trade".equals(value)) {
            throw new DataIgnoredException(UNSUPPORTED_STATUS_TYPE.concat(value));
        }
    }

    @Parsed(field = "side")
    public void setSide(String value) {
        side = detectTransactionType(value);
    }

    @Parsed(field = "lastQty", defaultNullRead = "0")
    public void setLastQty(BigDecimal value) {
        if (value.compareTo(BigDecimal.ZERO) == 0) {
            throw new DataValidationException("Quantity can not be zero.");
        }
        this.lastQty = value;
    }

    @Parsed(field = "lastPx", defaultNullRead = "0")
    public void setLastPx(BigDecimal value) {
        this.lastPx = value;
    }

    @Parsed(field = "execComm", defaultNullRead = "0")
    public void setExecComm(BigDecimal value) {
        this.execComm = value.divide(SATOSHIS_BY_BTC, MathContext.DECIMAL32);
    }

    @Parsed(field = "orderID")
    public void setOrderID(String value) {
        this.orderID = value;
    }



    @Override
    public TransactionCluster toTransactionCluster() {
        validateCurrencyPair(symbolBase, symbolQuote);
        validatePositivity(lastPx,lastQty,execComm);

        List<ImportedTransactionBean> related;
        if (ParserUtils.equalsToZero(execComm)) {
            related = Collections.emptyList();
        } else {
            related = List.of(
                new FeeRebateImportedTransactionBean(
                    orderID + FEE_UID_PART,
                    transactTime,
                    symbolQuote,
                    symbolQuote,
                    TransactionType.FEE,
                    execComm.setScale(ParserUtils.DECIMAL_DIGITS, ParserUtils.ROUNDING_MODE),
                    symbolQuote
                )
            );
        }

        return new TransactionCluster(
            new BuySellImportedTransactionBean(
                orderID,
                transactTime,
                symbolBase,
                symbolQuote,
                side,
                lastQty.setScale(ParserUtils.DECIMAL_DIGITS, ParserUtils.ROUNDING_MODE),
                lastPx.setScale(ParserUtils.DECIMAL_DIGITS, ParserUtils.ROUNDING_MODE)
            ),
            related
        );
    }

    private String findStarts(String value) {
        List<String> matchedCurrencies = CURRENCIES
            .keySet()
            .stream()
            .filter(value::startsWith)
            .collect(Collectors.toList());
        switch (matchedCurrencies.size()) {
            case 0:
                return null;
            case 1:
                return matchedCurrencies.get(0);
            default:
                throw new IllegalStateException(
                    String.format("Found more (%d) than one base currencies.", matchedCurrencies.size())
                );
        }
    }
}
