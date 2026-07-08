package io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v3;

import io.everytrade.server.model.Currency;
import io.everytrade.server.model.CurrencyPair;
import io.everytrade.server.model.TransactionType;
import io.everytrade.server.plugin.api.parser.FeeRebateImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.ImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.TransactionCluster;
import io.everytrade.server.plugin.impl.everytrade.parser.ParserUtils;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.ExchangeBean;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static io.everytrade.server.plugin.impl.everytrade.parser.ParserUtils.equalsToZero;
import static io.everytrade.server.plugin.impl.everytrade.parser.ParserUtils.nullOrZero;
import static java.util.Collections.emptyList;


public class BinanceBeanV3 extends ExchangeBean {

    private Instant date;
    private Currency pairBase;
    private Currency pairQuote;
    private TransactionType type;
    private BigDecimal filled;
    private BigDecimal total;
    private BigDecimal fee;
    private Currency feeCurrency;

    public BinanceBeanV3(
        String date,
        String pair,
        String type,
        String filled,
        String total,
        String fee,
        Currency feeCurrency,
        CurrencyPair currencyPair
    ) {
        this.date = parseDate(date);
        this.pairBase = currencyPair.getBase();
        this.pairQuote = currencyPair.getQuote();
        this.type = detectTransactionType(type);
        // Strip the trailing currency code as a literal suffix (base for filled, quote for total) instead of removing
        // every letter: for a digit-leading ticker "5.11INCH" (5.1 of 1INCH) the blind letter-strip would leave 5.11,
        // whereas removing exactly the known code "1INCH" yields the correct 5.1.
        this.filled = new BigDecimal(stripCurrencyCode(filled, currencyPair.getBase()));
        this.total = new BigDecimal(stripCurrencyCode(total, currencyPair.getQuote()));
        String feeValue = stripCurrencyCode(fee, feeCurrency);
        BigDecimal feeAbsValue = new BigDecimal(feeValue).abs(); // abs value of fee
        if (feeCurrency != null && feeAbsValue.compareTo((BigDecimal.ZERO)) > 0)  {
            this.feeCurrency = feeCurrency;
            this.fee = feeAbsValue;
        } else {
            this.fee = BigDecimal.ZERO;
        }
    }

    /**
     * Removes the trailing {@link Currency#code()} from a glued "amount+ticker" value (e.g. "5.11INCH" with base 1INCH
     * -&gt; "5.1"), then drops grouping/space characters. When the code is not a suffix (already-clean numbers, or an
     * unknown fee coin passed as {@code null}) it falls back to keeping only digits, dot and minus — matching the
     * previous behaviour for every non-digit-leading ticker.
     */
    private static String stripCurrencyCode(String raw, Currency currency) {
        String value = raw.trim();
        if (currency != null) {
            String code = currency.code();
            int start = value.length() - code.length();
            if (start > 0 && value.regionMatches(true, start, code, 0, code.length())) {
                value = value.substring(0, start);
            }
        }
        return value.replaceAll("[^\\d.\\-]", "");
    }

    // Binance varies the year width by export locale/version: 4-digit (2020-05-29) or 2-digit (25-11-17).
    // Java maps the 2-digit "yy" to 2000-2099, which matches Binance's data range.
    private static Instant parseDate(String date) {
        try {
            return ParserUtils.parse("yyyy-MM-dd HH:mm:ss", date);
        } catch (java.time.DateTimeException e) {
            return ParserUtils.parse("yy-MM-dd HH:mm:ss", date);
        }
    }

    @Override
    public TransactionCluster toTransactionCluster() {
        // validateCurrencyPair(pairBase, pairQuote); already called in constructor as null check
        validatePositivity(filled, total, fee);

        var isIncorrectFeeCoin = (feeCurrency == null && !equalsToZero(fee));

        List<ImportedTransactionBean> related;
        if (isIncorrectFeeCoin || equalsToZero(fee)) {
            related = emptyList();
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
            cluster.setFailedFee(1, "Fee " + (feeCurrency != null ? feeCurrency.code() : "null") + " currency is neither base or quote");
        } else if (nullOrZero(fee)) {
//            cluster.setIgnoredFee(1, "Fee amount is 0 " + (feeCurrency != null ? feeCurrency.code() : ""));
        }
        return cluster;
    }
}
