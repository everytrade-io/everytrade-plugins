package io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean;

import com.univocity.parsers.annotations.Format;
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
import static lombok.AccessLevel.PRIVATE;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static io.everytrade.server.model.Currency.BTC;
import static io.everytrade.server.model.TransactionType.BUY;
import static io.everytrade.server.model.TransactionType.FEE;
import static io.everytrade.server.plugin.impl.everytrade.parser.ParserUtils.nullOrZero;
import static java.util.Collections.emptyList;

@FieldDefaults(level = PRIVATE)
public class OpenNodeV1 extends ExchangeBean {

    String openNodeId;
    String description;
    String settlementDate;
    String settlementTime;
    String paymentAmount;
    String merchantCurrencyAmount;
    String merchantAccountCurrency;
    String processingFeesPaidAmount;

    @Parsed(field = "OpenNode ID")
    public void setOrderNodeId(String s) {
        this.openNodeId = s;
    }

    @Parsed(field = "Description")
    public void setDescription(String s) {
        description = s;
    }

    @Parsed(field = "Settlement date (mm/dd/yyyy UTC)")
    public void setSettlementDate(String value) {
        settlementDate = value;
    }

    @Parsed(field = "Settlement time (UTC)")
    @Format(formats = {"\"hh:mm aa\""}, options = {"locale=EN", "timezone=UTC"})
    public void setSettlementTime(String value) {
        settlementTime = value;
    }

    @Parsed(field = "Payment amount (BTC)")
    public void setPaymentAmount(String s) {
        paymentAmount = s;
    }

    @Parsed(field = "Merchant currency amount")
    public void setMerchantCurrencyAmount(String s) {
        merchantCurrencyAmount = s;
    }

    @Parsed(field = "Merchant account currency")
    public void setMerchantCurrency(String s) {
        merchantAccountCurrency = s;
    }

    @Parsed(field = "Processing fees paid (BTC)")
    public void setProcessingFeesPaid(String s) {
        processingFeesPaidAmount = s;
    }

    private Instant combineDateAndTime(String day, String time) {
        try {
            String stringDate = String.format("%s %s", day, time);
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM/dd/yyyy hh:mm a");
            final LocalDateTime localDateTime = LocalDateTime.parse(stringDate, formatter);
            return localDateTime.toInstant(ZoneOffset.UTC);
        } catch (Exception e) {
            throw new DataIgnoredException("Wrong settlement date/time format. ");
        }
    }

    @Override
    public TransactionCluster toTransactionCluster() {
        TransactionType type = findTransactionType();
        Instant date = combineDateAndTime(settlementDate, settlementTime);

        if (type.equals(BUY)) {
            return createBuy(date);
        } else {
            throw new DataValidationException("Unsupported transaction type. ");
        }
    }

    private TransactionType findTransactionType() {
        return BUY;
    }

    private TransactionCluster createBuy(Instant createdAt) {
        try {
            BigDecimal baseAmount = new BigDecimal(paymentAmount.replace("\"", ""));
            Currency baseCurrency = BTC;
            BigDecimal quoteAmount = new BigDecimal(merchantCurrencyAmount.replace("\"", ""));
            Currency quoteCurrency = Currency.fromCode(merchantAccountCurrency.replace("\"", ""));
            Currency feeCurrency = BTC;
            BigDecimal feeAmount = new BigDecimal(processingFeesPaidAmount.replace("\"", ""));

            boolean ignoredFees = false;
            TransactionCluster cluster;
            List<ImportedTransactionBean> related;
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
                    ignoredFees = true;
                    related = emptyList();
                }
            }
            cluster = new TransactionCluster(
                new ImportedTransactionBean(
                    null,
                    createdAt,
                    baseCurrency,
                    quoteCurrency,
                    BUY,
                    baseAmount.abs(),
                    evalUnitPrice(quoteAmount.abs(), baseAmount.abs())
                ),
                related
            );
            if (ignoredFees) {
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
