package io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean;

import com.univocity.parsers.annotations.Headers;
import com.univocity.parsers.annotations.Parsed;
import com.univocity.parsers.annotations.Replace;
import com.univocity.parsers.common.DataValidationException;
import io.everytrade.server.model.Currency;
import io.everytrade.server.model.TransactionType;
import io.everytrade.server.plugin.api.parser.ImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.TransactionCluster;
import io.everytrade.server.plugin.impl.everytrade.parser.exception.DataIgnoredException;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.ExchangeBean;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Headers(sequence = {
    "type", "fiat_currency", "amount_fiat", "amount_btc", "status", "completed_at", "trade_hash"
}, extract = true)
public class PaxfulBeanV1 extends ExchangeBean {
    public static final String UNSUPPORTED_QUOTE_CURRENCY = "Unsupported quote currency ";
    private TransactionType type;
    private Currency fiatCurrency;
    private BigDecimal amountFiat;
    private BigDecimal amountBtc;
    private Instant completedAt;
    private String tradeHash;

    @Parsed(field = "type")
    public void setType(String value) {
        type = detectTransactionType(value);
    }

    @Parsed(field = "fiat_currency")
    public void setFiatCurrency(String value) {
        fiatCurrency = Currency.valueOf(value);
        if (!fiatCurrency.isFiat()) {
            throw new DataValidationException(UNSUPPORTED_QUOTE_CURRENCY.concat(fiatCurrency.name()));
        }
    }

    @Parsed(field = "amount_fiat")
    @Replace(expression = IGNORED_CHARS_IN_NUMBER, replacement = "")
    public void setAmountFiat(BigDecimal value) {
        amountFiat = value;
    }

    @Parsed(field = "amount_btc")
    @Replace(expression = IGNORED_CHARS_IN_NUMBER, replacement = "")
    public void setAmountBtc(BigDecimal value) {
        amountBtc = value;
    }

    @Parsed(field = "status")
    public void setStatuc(String value) {
        if (!"successful".equals(value)) {
            throw new DataIgnoredException(UNSUPPORTED_STATUS_TYPE.concat(value));
        }
    }

    @Parsed(field = "completed_at")
    public void setCompletedAt(String value) {
        if (value.length() < 19) {
            throw new DataValidationException(
                String.format("Unknown dateTime format(%s), illegal length %d.", value, value.length())
            );
        }
        final String withoutOffset = value.substring(0, 19);
        final LocalDateTime localDateTime = LocalDateTime.parse(withoutOffset, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        completedAt = localDateTime.toInstant(ZoneOffset.UTC);
    }

    @Parsed(field = "trade_hash")
    public void setTradeHash(String value) {
        tradeHash = value;
    }

    @Override
    public TransactionCluster toTransactionCluster() {
        //TODO: mcharvat - implement
        return null;
//        validateCurrencyPair(Currency.BTC, fiatCurrency);
//        return new ImportedTransactionBean(
//            tradeHash,               //uuid
//            completedAt,             //executed
//            Currency.BTC,            //base
//            fiatCurrency,            //quote
//            TransactionType.BUY.equals(type) ? TransactionType.SELL : TransactionType.BUY,    //action
//            amountBtc.abs(),               //base quantity
//            evalUnitPrice(amountFiat.abs(), amountBtc.abs()), //unit price
//            BigDecimal.ZERO          //fee quote
//        );
    }
}
