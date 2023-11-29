package io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v2;

import com.univocity.parsers.annotations.Headers;
import com.univocity.parsers.common.DataValidationException;
import io.everytrade.server.model.Currency;
import io.everytrade.server.model.CurrencyPair;
import io.everytrade.server.model.TransactionType;
import io.everytrade.server.plugin.api.parser.FeeRebateImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.ImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.TransactionCluster;
import io.everytrade.server.plugin.impl.everytrade.parser.ParserUtils;
import io.everytrade.server.plugin.impl.everytrade.parser.exception.DataIgnoredException;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.ExchangeBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static io.everytrade.server.model.CurrencyPair.getTradeablePairs;
import static io.everytrade.server.plugin.impl.everytrade.parser.ParserUtils.equalsToZero;
import static io.everytrade.server.plugin.impl.everytrade.parser.ParserUtils.nullOrZero;

@Headers(sequence = {"Pair", "Type", "Filled", "Total", "Fee", "status"}, extract = true)
public class BinanceBeanV2 extends ExchangeBean {
    private static final Logger LOG = LoggerFactory.getLogger(BinanceBeanV2.class);
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
            String.format("%s%s", t.getBase().code(), t.getQuote().code()), t)
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
        if (!(STATUS_FILLED.equalsIgnoreCase(status) || STATUS_PARTIAL_FILL.equalsIgnoreCase(status))) {
            throw new DataIgnoredException(UNSUPPORTED_STATUS_TYPE.concat(status));
        }
        this.date = parseDate(date);
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
    public TransactionCluster toTransactionCluster() {
        validateCurrencyPair(pairBase, pairQuote);
        validatePositivity(filled, total, fee);

        final boolean isIncorrectFeeCoin
            = (feeCurrency == null && !equalsToZero(fee));

        final List<ImportedTransactionBean> related;

        if (isIncorrectFeeCoin || equalsToZero(fee)) {
            related = Collections.emptyList();
        } else {
            related = List.of(
                new FeeRebateImportedTransactionBean(
                    FEE_UID_PART,
                    date,
                    feeCurrency,
                    feeCurrency,
                    TransactionType.FEE,
                    fee.setScale(ParserUtils.DECIMAL_DIGITS, ParserUtils.ROUNDING_MODE),
                    feeCurrency
                )
            );
        }

        TransactionCluster cluster = new TransactionCluster(
            new ImportedTransactionBean(
                null,
                date,
                pairBase,
                pairQuote,
                type,
                filled.setScale(ParserUtils.DECIMAL_DIGITS, ParserUtils.ROUNDING_MODE),
                evalUnitPrice(total, filled)
            ),
            related
        );
        if (isIncorrectFeeCoin) {
            cluster.setFailedFee(1, "Fee " + (feeCurrency != null ? feeCurrency.code() : "null")
                + " currency is neither base or quote");
        } else if (nullOrZero(fee)) {
//            cluster.setIgnoredFee(1, "Fee amount is 0 " + (feeCurrency != null ? feeCurrency.code() : ""));
        }
        return cluster;
    }

    private Currency findEnds(String value) {
        List<Currency> matchedCurrencies = Arrays
            .stream(Currency.values())
            .filter(currency -> value.endsWith(currency.code()))
            .collect(Collectors.toList());
        if (matchedCurrencies.size() == 1) {
            return matchedCurrencies.get(0);
        }
        return null;
    }

    private Instant parseDate(String str) {
        var patterns = List.of(
            "yyyy-MM-dd HH:mm:ss",
            "d.M.yyyy H:mm"
        );
        for (String pattern : patterns) {
            try {
                return ParserUtils.parse(pattern, str);
            } catch (Exception e) {
                LOG.debug("Cannot parse date string", e);
            }
        }
        throw new IllegalArgumentException("Cannot parse date: " + str);
    }
}
