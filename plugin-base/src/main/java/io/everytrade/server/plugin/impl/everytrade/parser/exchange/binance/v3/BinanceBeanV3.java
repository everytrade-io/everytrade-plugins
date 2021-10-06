package io.everytrade.server.plugin.impl.everytrade.parser.exchange.binance.v3;

import com.univocity.parsers.common.DataValidationException;
import io.everytrade.server.model.Currency;
import io.everytrade.server.model.CurrencyPair;
import io.everytrade.server.model.TransactionType;
import io.everytrade.server.plugin.api.parser.BuySellImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.FeeRebateImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.ImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.TransactionCluster;
import io.everytrade.server.plugin.impl.everytrade.parser.ParserUtils;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.ExchangeBean;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static io.everytrade.server.model.CurrencyPair.getTradeablePairs;

public class BinanceBeanV3 extends ExchangeBean {

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

    public BinanceBeanV3(
        String date,
        String pair,
        String type,
        String filled,
        String total,
        String fee
    ) {
        this.date = ParserUtils.parse("yyyy-MM-dd HH:mm:ss", date);
        final CurrencyPair currencyPair = fastCurrencyPair.get(pair);
        if (currencyPair == null) {
            throw new DataValidationException(UNSUPPORTED_CURRENCY_PAIR.concat(pair));
        }
        pairBase = currencyPair.getBase();
        pairQuote = currencyPair.getQuote();
        this.type = detectTransactionType(type);
        this.filled = new BigDecimal(filled.replaceAll("[A-Z,\\s$]", ""));
        this.total = new BigDecimal(total.replaceAll("[A-Z,\\s$]", ""));
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
            = feeCurrency == null || !(feeCurrency.equals(pairBase) || feeCurrency.equals(pairQuote));

        final List<ImportedTransactionBean> related;

        if (isIncorrectFeeCoin) {
            related = Collections.emptyList();
        } else {
            related = List.of(
                new FeeRebateImportedTransactionBean(
                    null,
                    date,
                    pairBase,
                    pairQuote,
                    TransactionType.FEE,
                    fee.setScale(ParserUtils.DECIMAL_DIGITS, ParserUtils.ROUNDING_MODE),
                    feeCurrency
                )
            );
        }

        TransactionCluster cluster = new TransactionCluster(
            new BuySellImportedTransactionBean(
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
            cluster.setIgnoredFee(1, "Fee " + (feeCurrency != null ? feeCurrency.code() : "null") + " currency is neither base or quote");
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
}
