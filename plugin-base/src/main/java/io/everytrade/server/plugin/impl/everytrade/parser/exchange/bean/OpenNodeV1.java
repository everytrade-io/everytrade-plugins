package io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean;

import com.univocity.parsers.annotations.Format;
import com.univocity.parsers.annotations.Headers;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;

import static io.everytrade.server.model.Currency.BTC;
import static io.everytrade.server.model.TransactionType.BUY;
import static io.everytrade.server.model.TransactionType.FEE;
import static io.everytrade.server.plugin.impl.everytrade.parser.ParserUtils.nullOrZero;
import static java.util.Collections.emptyList;

public class OpenNodeV1 extends ExchangeBean {

    private String openNodeID;
    private String description;
//    private String paymentRequestDate;
//    private String paymentRequestTime;
    private String settlementDate;
    private String settlementTime;
    private String paymentAmount;
//    private String originatingAmount;
//    private String originatingCurrency;
    private String merchantCurrencyAmount;
    private String merchantAccountCurrency;
    private String processingFeesPaidAmount;
//    private String processingFeesPaidCurrency;
//    private String netSettledAmount;
//    private String settlementCurrency;
//    private String automaticallyConvertedCurrency;
//    private String paymentMethod;
//    private String orderId;
//    private String metadata;
//    private String metadataEmail;

//    private String internalId;
//    private String note;
//    private Instant time;

//    Currency base;
//    BigDecimal baseAmount;
//    Currency quote;
//    BigDecimal quoteAmount;
//    Currency feeCurrency;
//    BigDecimal feeAmount;
//    TransactionType type;

    @Parsed(field = "OpenNode ID")
    public void setOrderNodeId(String s) {
        this.openNodeID = s;
    }

    @Parsed(field = "Description")
    public void setDescription(String s) {
        description = s;
    }

//    @Parsed(field = "\"Payment request date (mm/dd/yyyy UTC)\"")
//    @Format(formats = {"\"MM/dd/yyyy\""}, options = {"locale=EN", "timezone=UTC"})
//    public void setPaymentRequestDate(Date value) {
//        paymentRequestDate = value;
//    }
//
//    @Parsed(field = "\"Payment request time (UTC)\"")
//    @Format(formats = {"\"hh:mm aa\""}, options = {"locale=EN", "timezone=UTC"})
//    public void setPaymentRequestTime(Date value) {
//        paymentRequestTime = value;
//    }

    @Parsed(field = "Settlement date (mm/dd/yyyy UTC)")
//    @Format(formats = {"\"MM/dd/yyyy\""}, options = {"locale=EN", "timezone=UTC"})
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

        switch (type) {
            case BUY: {
                return createBuy(date);
            }
            default: {
                throw new DataValidationException("Unsupported transaction type. ");
            }
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
