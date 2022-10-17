package io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean;

import com.univocity.parsers.annotations.Parsed;
import com.univocity.parsers.common.DataValidationException;
import io.everytrade.server.model.Currency;
import io.everytrade.server.model.TransactionType;
import io.everytrade.server.plugin.api.parser.FeeRebateImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.ImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.TransactionCluster;
import io.everytrade.server.plugin.impl.everytrade.parser.ParserUtils;
import io.everytrade.server.plugin.impl.everytrade.parser.exception.DataIgnoredException;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.ExchangeBean;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static io.everytrade.server.model.TransactionType.BUY;
import static io.everytrade.server.model.TransactionType.FEE;
import static io.everytrade.server.model.TransactionType.SELL;
import static io.everytrade.server.plugin.impl.everytrade.parser.ParserUtils.nullOrZero;
import static java.util.Collections.emptyList;
import static lombok.AccessLevel.PRIVATE;

@FieldDefaults(level = PRIVATE)
public class OpenNodeV3 extends ExchangeBean {

    String openNodeId;
    String date;
    String time;
    String fromAmount;
    String fromCurrency;
    String toAmount;
    String toCurrency;
    String fromToExchangeRate;
    String conversionFeesPaid;
    String status;

    BigDecimal baseAmount;
    BigDecimal quoteAmount;
    BigDecimal feeAmount;
    Currency baseCurrency;
    Currency quoteCurrency;
    Currency feeCurrency;
    TransactionType type;
    Instant createdAt;

    @Parsed(field = "OpenNode ID")
    public void setOrderNodeId(String s) {
        this.openNodeId = s;
    }

    @Parsed(field = "Date (mm/dd/yyyy UTC)")
    public void setDate(String value) {
        date = value;
    }

    @Parsed(field = "Time (UTC)")
    public void setTime(String value) {
        time = value;
    }

    @Parsed(field = "From amount")
    public void setFromAmount(String s) {
        fromAmount = s;
    }

    @Parsed(field = "From currency")
    public void setFromCurrency(String s) {
        fromCurrency = s;
    }

    @Parsed(field = "To amount")
    public void setToAmount(String s) {
        toAmount = s;
    }

    @Parsed(field = "To currency")
    public void setToCurrency(String s) {
        toCurrency = s;
    }

    @Parsed(field = "From/To exchange rate")
    public void setFromToExchangeRate(String s) {
        fromToExchangeRate = s;
    }

    @Parsed(field = "Conversion fees paid (BTC)")
    public void setConversionFeesPaid(String s) {
        conversionFeesPaid = s;
    }

    @Parsed(field = "Status")
    public void setStatus(String s) {
        status = s;
    }

    private Instant combineDateAndTime(String day, String time) {
        try {
            String stringDate = String.format("%s %s", day, time);
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM/dd/yyyy hh-mm a");
            final LocalDateTime localDateTime = LocalDateTime.parse(stringDate, formatter);
            return localDateTime.toInstant(ZoneOffset.UTC);
        } catch (Exception e) {
            throw new DataIgnoredException("Wrong settlement date/time format. ");
        }
    }

    @Override
    public TransactionCluster toTransactionCluster() {
        if (!"confirmed".equalsIgnoreCase(status)) {
            throw new DataIgnoredException(String.format("Unfinished status: %s. ", status));
        }
        type = findTransactionType();
        setUpValues();
        return createBuySellTxs();
    }

    private TransactionType findTransactionType() {
        if (!Currency.fromCode(toCurrency).isFiat() || Currency.fromCode(fromCurrency).isFiat()) {
            return BUY;
        } else {
            return SELL;
        }
    }

    private void setUpValues() {
        switch (type) {
            case BUY -> {
                baseAmount = new BigDecimal(toAmount);
                baseCurrency = Currency.fromCode(toCurrency);
                quoteAmount = new BigDecimal(fromAmount);
                quoteCurrency = Currency.fromCode(fromCurrency);
            }
            case SELL -> {
                baseAmount = new BigDecimal(fromAmount);
                baseCurrency = Currency.fromCode(fromCurrency);
                quoteAmount = new BigDecimal(toAmount);
                quoteCurrency = Currency.fromCode(toCurrency);
            }
            default -> {
                throw new DataValidationException("Unsupported transaction type. ");
            }
        }
        createdAt = combineDateAndTime(this.date, time);
        feeAmount = new BigDecimal(conversionFeesPaid);
        feeCurrency = Currency.BTC;
    }

    private TransactionCluster createBuySellTxs() {
        try {
            boolean ignoredFee = false;
            List<ImportedTransactionBean> related;
            TransactionCluster cluster;

            if (ParserUtils.nullOrZero(feeAmount)) {
                related = emptyList();
            } else {
                try {
                    related = List.of(
                        new FeeRebateImportedTransactionBean(
                            null,
                            createdAt,
                            feeCurrency,
                            feeCurrency,
                            FEE,
                            feeAmount,
                            feeCurrency
                        )
                    );
                } catch (Exception e) {
                    ignoredFee = true;
                    related = emptyList();
                }

            }
            cluster = new TransactionCluster(
                new ImportedTransactionBean(
                    openNodeId,
                    createdAt,
                    baseCurrency,
                    quoteCurrency,
                    type,
                    baseAmount.abs(),
                    evalUnitPrice(quoteAmount.abs(), baseAmount.abs())
                ),
                related
            );
            if (ignoredFee) {
                if (!nullOrZero(feeAmount) && feeCurrency == null) {
                    cluster.setFailedFee(1, "Fee " + (feeCurrency != null ? feeCurrency.code() : "null")
                        + " currency is not defined");
                }
            }
            return cluster;
        } catch (Exception e) {
            throw new DataValidationException("Something went wrong. ");
        }
    }

}
