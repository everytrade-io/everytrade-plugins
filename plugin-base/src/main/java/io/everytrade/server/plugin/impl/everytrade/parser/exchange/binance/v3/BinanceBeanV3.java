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
import java.util.Arrays;
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
        CurrencyPair currencyPair
    ) {
        this.date = ParserUtils.parse("yyyy-MM-dd HH:mm:ss", date);
        this.pairBase = currencyPair.getBase();
        this.pairQuote = currencyPair.getQuote();
        this.type = detectTransactionType(type);
        this.filled = new BigDecimal(filled.replaceAll("[^\\d.\\-]", ""));
        this.total = new BigDecimal(total.replaceAll("[^\\d.\\-]", ""));
        String feeValue = fee.replaceAll("[^\\d.\\-]", "");
        BigDecimal feeAbsValue = new BigDecimal(feeValue).abs(); // abs value of fee
        Currency currencyEnds = findEnds(fee); // end of string with currency code
        if (currencyEnds != null && feeAbsValue.compareTo((BigDecimal.ZERO)) > 0)  {
            feeCurrency = currencyEnds;
            this.fee = feeAbsValue;
        } else {
            this.fee = BigDecimal.ZERO;
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

    private Currency findEnds(String value) {
        List<Currency> matchedCurrencies = Arrays.stream(Currency.values())
            .filter(currency -> value.endsWith(currency.code()))
            .toList();
        if (matchedCurrencies.size() == 1) {
            return matchedCurrencies.get(0);
        }
        return null;
    }
}
