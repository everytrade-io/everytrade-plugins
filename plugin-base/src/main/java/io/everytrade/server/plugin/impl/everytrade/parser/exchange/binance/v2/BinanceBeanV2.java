package io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v2;

import com.univocity.parsers.annotations.Headers;
import com.univocity.parsers.common.DataValidationException;
import io.everytrade.server.model.Currency;
import io.everytrade.server.model.CurrencyPair;
import io.everytrade.server.model.TransactionType;
import io.everytrade.server.plugin.api.parser.ImportDetail;
import io.everytrade.server.plugin.api.parser.ImportedTransactionBean;
import io.everytrade.server.plugin.impl.everytrade.parser.ParserUtils;
import io.everytrade.server.plugin.impl.everytrade.parser.exception.DataIgnoredException;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.ExchangeBean;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static io.everytrade.server.model.CurrencyPair.getTradeablePairs;

@Headers(sequence = {"Pair", "Type", "Filled", "Total", "Fee", "status"}, extract = true)
public class BinanceBeanV2 extends ExchangeBean {
    private static final String STATUS_FILLED = "Filled";
    private static final String STATUS_PARTIAL_FILL = "Partial Fill";
    private static Map<String, CurrencyPair> fastCurrencyPair = new HashMap<>();
    private Instant date;
    private Currency pairBase;
    private Currency pairQuote;
    private TransactionType type;
    private BigDecimal filled;
    private BigDecimal total;
    private BigDecimal fee;
    private Currency feeCurrency;

    static {
        getTradeablePairs().forEach(t -> fastCurrencyPair.put(
            String.format("%s%s", t.getBase().name(), t.getQuote().name()), t)
        );
    }

    public BinanceBeanV2(
        String date,
        String pair,
        String type,
        String filled,
        String total,
        String fee,
        String status
    ) {
        if (!(STATUS_FILLED.equals(status) || STATUS_PARTIAL_FILL.equals(status))) {
            throw new DataIgnoredException(UNSUPPORTED_STATUS_TYPE.concat(status));
        }
        this.date = ParserUtils.parse("yyyy-MM-dd HH:mm:ss", date);
        final CurrencyPair currencyPair = fastCurrencyPair.get(pair);
        if (currencyPair == null) {
            throw new DataValidationException(UNSUPPORTED_CURRENCY_PAIR.concat(pair));
        }
        pairBase = currencyPair.getBase();
        pairQuote = currencyPair.getQuote();
        this.type = detectTransactionType(type);
        this.filled = new BigDecimal(filled.replaceAll(IGNORED_CHARS_IN_NUMBER, ""));
        this.total = new BigDecimal(total.replaceAll(IGNORED_CHARS_IN_NUMBER, ""));
        feeCurrency = findEnds(fee);
        if (feeCurrency != null) {
            final String feeValue = fee.replaceAll("[A-Z,\\s$]", "");
            this.fee = new BigDecimal(feeValue);
        } else {
            this.fee = BigDecimal.ZERO;
        }
    }

    @Override
    public ImportedTransactionBean toImportedTransactionBean() {
        validateCurrencyPair(pairBase, pairQuote);

        final boolean isIncorrectFeeCoin
            = feeCurrency == null || !(feeCurrency.equals(pairBase) || feeCurrency.equals(pairQuote));
        final BigDecimal coefFeeBase;
        final BigDecimal coefFeeQuote;
        if (isIncorrectFeeCoin) {
            coefFeeBase = BigDecimal.ZERO;
            coefFeeQuote = BigDecimal.ZERO;
        } else {
            if (TransactionType.BUY.equals(type)) {
                if (feeCurrency.equals(pairBase)) {
                    coefFeeBase = BigDecimal.ONE.negate();
                    coefFeeQuote = BigDecimal.ZERO;
                } else {
                    coefFeeBase = BigDecimal.ZERO;
                    coefFeeQuote = BigDecimal.ONE;
                }
            } else {
                if (feeCurrency.equals(pairBase)) {
                    coefFeeBase = BigDecimal.ONE;
                    coefFeeQuote = BigDecimal.ZERO;
                } else {
                    coefFeeBase = BigDecimal.ZERO;
                    coefFeeQuote = BigDecimal.ONE.negate();
                }
            }
        }
        final BigDecimal baseQuantity = filled.abs().add(coefFeeBase.multiply(fee));
        final BigDecimal quoteVolume = total.abs().add(coefFeeQuote.multiply(fee));
        final BigDecimal unitPrice = evalUnitPrice(quoteVolume, baseQuantity);

        validatePositivity(baseQuantity, quoteVolume, unitPrice);

        return new ImportedTransactionBean(
            null,         //uuid
            date,              //executed
            pairBase,          //base
            pairQuote,         //quote
            type,              //action
            baseQuantity.setScale(ParserUtils.DECIMAL_DIGITS, RoundingMode.HALF_UP),      //base quantity
            unitPrice,         //unit price
            BigDecimal.ZERO,    //fee quote
            new ImportDetail(isIncorrectFeeCoin)
        );
    }

    private Currency findEnds(String value) {
        List<Currency> matchedCurrencies = Arrays
            .stream(Currency.values())
            .filter(currency -> value.endsWith(currency.name()))
            .collect(Collectors.toList());
        if (matchedCurrencies.size() == 1) {
            return matchedCurrencies.get(0);
        }
        return null;
    }
}
