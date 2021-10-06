package io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean;

import com.univocity.parsers.annotations.Format;
import com.univocity.parsers.annotations.Headers;
import com.univocity.parsers.annotations.Parsed;
import com.univocity.parsers.annotations.Replace;
import com.univocity.parsers.common.DataValidationException;
import io.everytrade.server.model.Currency;
import io.everytrade.server.model.TransactionType;
import io.everytrade.server.plugin.api.parser.BuySellImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.FeeRebateImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.ImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.TransactionCluster;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.ExchangeBean;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Headers(sequence = {"Time","Type","Pair","Side","Amount","Total","Fee"}, extract = true)
public class HuobiBeanV1 extends ExchangeBean {
    public static final String UNSUPPORTED_TYPE = "Unsupported type ";
    private Instant time;
    private Currency pairBase;
    private Currency pairQuote;
    private TransactionType side;
    private BigDecimal amount;
    private BigDecimal total;
    private BigDecimal fee;
    private Currency feeCurrency;

    @Parsed(field = "Time")
    @Format(formats = {"yyyy-MM-dd HH:mm:ss"}, options = {"locale=EN", "timezone=UTC"})
    public void setTime(Date value) {
        time = value.toInstant();
    }

    @Parsed(field = "Type")
    public void setType(String value) {
        if (!"Exchange".equals(value)) {
            throw new DataValidationException(UNSUPPORTED_TYPE.concat(value));
        }
    }

    @Parsed(field = "Pair")
    public void setPair(String value) {
        final String[] values = value.split("/");
        pairBase = Currency.fromCode(values[0]);
        pairQuote = Currency.fromCode(values[1]);
    }

    @Parsed(field = "Side")
    public void setSide(String value) {
        side = detectTransactionType(value);
    }

    @Parsed(field = "Amount")
    @Replace(expression = IGNORED_CHARS_IN_NUMBER, replacement = "")
    public void setAmount(BigDecimal value) {
        amount = value;
    }

    @Parsed(field = "Total")
    @Replace(expression = IGNORED_CHARS_IN_NUMBER, replacement = "")
    public void setTotal(BigDecimal value) {
        total = value;
    }

    @Parsed(field = "Fee")
    public void setFee(String value) {
        feeCurrency = findEnds(value);
        if (feeCurrency != null) {
            final String feeValue = value.replaceAll("[A-Z,\\s$]", "");
            fee = new BigDecimal(feeValue);
        }
    }

    @Override
    public TransactionCluster toTransactionCluster() {
        validateCurrencyPair(pairBase, pairQuote);
        final boolean isIncorrectFeeCoin
            = feeCurrency == null || !(feeCurrency.equals(pairBase) || feeCurrency.equals(pairQuote));
        final BigDecimal baseQuantity;
        final BigDecimal quoteVolume;
        final List<ImportedTransactionBean> related;
        baseQuantity = amount.abs();
        quoteVolume = total.abs();

        if (isIncorrectFeeCoin) {
            related = Collections.emptyList();
        } else {
            related = List.of(
                new FeeRebateImportedTransactionBean(
                    null,
                    time,
                    pairBase,
                    pairQuote,
                    TransactionType.FEE,
                    fee,
                    feeCurrency
                )
            );
        }

        final BigDecimal unitPrice = evalUnitPrice(quoteVolume, baseQuantity);
        validatePositivity(baseQuantity, quoteVolume, unitPrice);

        TransactionCluster cluster = new TransactionCluster(
            new BuySellImportedTransactionBean(
                null,
                time,
                pairBase,
                pairQuote,
                side,
                baseQuantity,
                unitPrice
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
