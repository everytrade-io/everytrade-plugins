package io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean;

import com.univocity.parsers.annotations.Convert;
import com.univocity.parsers.annotations.Headers;
import com.univocity.parsers.annotations.Parsed;
import com.univocity.parsers.common.DataValidationException;
import io.everytrade.server.model.Currency;
import io.everytrade.server.model.TransactionType;
import io.everytrade.server.plugin.api.parser.BuySellImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.FeeRebateImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.ImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.TransactionCluster;
import io.everytrade.server.plugin.impl.everytrade.parser.ParserUtils;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.ExchangeBean;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Collections;
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
    private static final Map<String, Currency> CURRENCY_SHORT_CODES = new HashMap<>();
    private static final Map<String, Currency> CURRENCY_LONG_CODES = new HashMap<>();
    private static final Map<String, Currency> CURRENCY_EXCEPTIONS = new HashMap<>();

    static {
        CURRENCY_SHORT_CODES.put("XBT", Currency.BTC);
        CURRENCY_LONG_CODES.put("XXBT", Currency.BTC);
        CURRENCY_SHORT_CODES.put("XDG", Currency.DOGE);
        CURRENCY_LONG_CODES.put("XXDG", Currency.DOGE);

        for (Currency value : Currency.values()) {
            if (value.equals(Currency.BTC)) {
                continue;
            }
            CURRENCY_SHORT_CODES.put(value.code(), value);
            if (value.isFiat()) {
                CURRENCY_LONG_CODES.put("Z" + value.code(), value);
            } else {
                CURRENCY_LONG_CODES.put("X" + value.code(), value);
            }
        }
    }

    @Parsed(field = "txid")
    public void setTxid(String txid) {
        this.txid = txid;
    }

    @Parsed(field = "pair")
    public void setPair(String pair) {
        String mBase = findCurrencyCode(pair, true);
        String mQuote = findCurrencyCode(pair, false);
        if (!pair.equals(mBase.concat(mQuote))) {
            throw new DataValidationException(String.format("Can not parse pair %s.", pair));
        }

        this.pairBase = findCurrencyByCode(mBase);
        this.pairQuote = findCurrencyByCode(mQuote);
    }

    @Parsed(field = "time")
    @Convert(
        conversionClass=DateTimeConverterWithSecondsFraction.class,
        args={"yyyy-MM-dd HH:mm:ss", "M/d/yy h:mm a"}
        )
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
        validateCurrencyPair(pairBase, pairQuote);
        List<ImportedTransactionBean> related;
        if (ParserUtils.equalsToZero(fee)) {
            related = Collections.emptyList();
        } else {
            related = List.of(
                new FeeRebateImportedTransactionBean(
                    txid + FEE_UID_PART,
                    time,
                    pairBase,
                    pairQuote,
                    TransactionType.FEE,
                    fee.setScale(ParserUtils.DECIMAL_DIGITS, RoundingMode.HALF_UP),
                    pairQuote
                )
            );
        }

        return new TransactionCluster(
            new BuySellImportedTransactionBean(
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

    private String findCurrencyCode(String pairCode, boolean isFindingBase) {
        List<String> matchedShortCodes = CURRENCY_SHORT_CODES
            .keySet()
            .stream()
            .filter(prefix -> isFindingBase ? pairCode.startsWith(prefix) : pairCode.endsWith(prefix))
            .collect(Collectors.toList());
        List<String> matchedLongCodes = CURRENCY_LONG_CODES
            .keySet()
            .stream()
            .filter(prefix -> isFindingBase ? pairCode.startsWith(prefix) : pairCode.endsWith(prefix))
            .collect(Collectors.toList());

        final boolean foundBothCodes = matchedShortCodes.size() == 1 && matchedLongCodes.size() == 1;
        final boolean foundLongCode = matchedLongCodes.size() == 1 && matchedShortCodes.isEmpty();
        final boolean foundShortCode = matchedShortCodes.size() == 1 && matchedLongCodes.isEmpty();

        if (foundBothCodes) {
            final String matchedShortCode = matchedShortCodes.get(0);
            final String matchedLongCode = matchedLongCodes.get(0);
            final boolean foundCurrenciesMatch =
                CURRENCY_SHORT_CODES.get(matchedShortCode).equals(CURRENCY_LONG_CODES.get(matchedLongCode));
            if (foundCurrenciesMatch) {
                return matchedLongCode;
            } else {
                throw new DataValidationException(String.format(
                    "Found different %s currency codes (%s,%s) in pair code %s.",
                    isFindingBase ? "base" : "quote",
                    matchedShortCode,
                    matchedLongCode,
                    pairCode
                ));
            }
        }

        if (foundLongCode) {
            return matchedLongCodes.get(0);
        }

        if (foundShortCode) {
            return matchedShortCodes.get(0);
        }

        throw new DataValidationException(String.format(
            "Unknown %s currency code in pair code %s.",
            isFindingBase ? "base" : "quote",
            pairCode
        ));
    }

    private Currency findCurrencyByCode(String code) {
        final Currency currencyLong = CURRENCY_LONG_CODES.get(code);
        if (currencyLong != null) {
            return currencyLong;
        }
        final Currency currencyShort = CURRENCY_SHORT_CODES.get(code);
        if (currencyShort != null) {
            return currencyShort;
        }

        throw new IllegalStateException(String.format("Currency not found for code %s.", code));
    }
}
