package io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean;

import com.univocity.parsers.annotations.Headers;
import com.univocity.parsers.annotations.Parsed;
import com.univocity.parsers.annotations.Replace;
import com.univocity.parsers.common.DataValidationException;
import io.everytrade.server.model.Currency;
import io.everytrade.server.model.TransactionType;
import io.everytrade.server.plugin.api.parser.ImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.TransactionCluster;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.ExchangeBean;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.Locale;

import static java.time.format.DateTimeFormatter.ISO_LOCAL_DATE;
import static java.time.format.DateTimeFormatter.ISO_LOCAL_TIME;

@Headers(sequence = {"id","trade_type","btc_final","fiat_amount","currency","transaction_released_at"}, extract = true)
public class LocalBitcoinsBeanV1 extends ExchangeBean {
    public static final DateTimeFormatter FORMATTER = new DateTimeFormatterBuilder()
        .parseCaseInsensitive()
        .append(ISO_LOCAL_DATE)
        .appendLiteral(' ')
        .append(ISO_LOCAL_TIME)
        .parseStrict().toFormatter(Locale.US);

    public static final String UNSUPPORTED_QOUTE_BTC = "Unsupported qoute BTC.";
    private String id;
    private TransactionType tradeType;
    private BigDecimal btcFinal;
    private BigDecimal fiatAmount;
    private Currency currency;
    private Instant transactionReleasedAt;

    @Parsed(field = "id")
    public void setId(String value) {
        id = value;
    }

    @Parsed(field = "trade_type")
    public void setTradeType(String value) {
        if ("ONLINE_BUY".equals(value)) {
            this.tradeType = TransactionType.BUY;
        } else if ("ONLINE_SELL".equals(value)) {
            this.tradeType = TransactionType.SELL;
        } else {
            throw new DataValidationException(UNSUPPORTED_TRANSACTION_TYPE.concat(value));
        }
    }

    @Parsed(field = "btc_final")
    @Replace(expression = IGNORED_CHARS_IN_NUMBER, replacement = "")
    public void setBtcFinal(BigDecimal value) {
        btcFinal = value;
    }

    @Parsed(field = "fiat_amount")
    @Replace(expression = IGNORED_CHARS_IN_NUMBER, replacement = "")
    public void setFiatAmount(BigDecimal value) {
        fiatAmount = value;
    }

    @Parsed(field = "currency")
    public void setCurrency(String value) {
        currency = Currency.valueOf(value);
        if (Currency.BTC.equals(currency)) {
            throw new DataValidationException(UNSUPPORTED_QOUTE_BTC);

        }
    }

    @Parsed(field = "transaction_released_at")
    public void setTransactionReleasedAt(String value) {
        if (value.length() < 19) {
            throw new DataValidationException(
                String.format("Unknown dateTime format(%s), illegal length %d.", value, value.length())
            );
        }
        final String withoutOffset = value.substring(0, 19);
        final LocalDateTime localDateTime = LocalDateTime.parse(
            withoutOffset, FORMATTER
        );
        this.transactionReleasedAt = localDateTime.toInstant(ZoneOffset.UTC);
    }

    @Override
    public TransactionCluster toTransactionCluster() {
        //TODO: mcharvat - implement
        return null;
//        final boolean isCrypto = !currency.isFiat();
//        final boolean isReverseTrade = isCrypto && !Currency.USDT.equals(currency);
//        final BigDecimal baseQuantity = isReverseTrade ? fiatAmount.abs() : btcFinal.abs();
//        final BigDecimal volume = isReverseTrade ? btcFinal.abs() : fiatAmount.abs();
//        return new ImportedTransactionBean(
//            id,                      //uuid
//            transactionReleasedAt,   //executed
//            isReverseTrade ? currency : Currency.BTC,            //base
//            isReverseTrade ? Currency.BTC : currency,            //quote
//            isReverseTrade ? switchAction(tradeType) : tradeType,//action
//            baseQuantity,                        //base quantity
//            evalUnitPrice(volume, baseQuantity), //unit price
//            BigDecimal.ZERO          //fee quote
//        );
    }

    private TransactionType switchAction(TransactionType transactionType) {
        if (TransactionType.BUY.equals(transactionType)) {
            return TransactionType.SELL;
        } else {
            return TransactionType.BUY;
        }
    }
}
