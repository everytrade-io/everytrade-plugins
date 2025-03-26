package io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean;

import com.univocity.parsers.annotations.Format;
import com.univocity.parsers.annotations.Parsed;
import io.everytrade.server.model.Currency;
import io.everytrade.server.model.TransactionType;
import io.everytrade.server.plugin.impl.everytrade.parser.exception.DataIgnoredException;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.kuCoin.BaseTransactionMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Date;

public class BtcPayServerBeanV1 extends BaseTransactionMapper {

    Instant date;
    String invoiceId;
    String orderId;
    String category;
    String paymentMethodId;
    boolean confirmed;
    String address;
    Currency paymentCurrency;
    BigDecimal paymentAmount;
    BigDecimal paymentMethodFee;
    String lightningAddress;
    Currency invoiceCurrency;
    BigDecimal invoiceCurrencyAmount;
    BigDecimal rate;

    BigDecimal feeAmount;

    @Parsed(field = "Date")
    @Format(formats = {"yyyy-MM-dd HH:mm:ss"}, options = {"locale=EN", "timezone=UTC"})
    public void setDate(Date date) {
        this.date = date.toInstant();
    }

    @Parsed(field = "InvoiceId")
    public void setInvoiceId(String invoiceId) {
        this.invoiceId = invoiceId;
    }

    @Parsed(field = "OrderId")
    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    @Parsed(field = {"Category", "PaymentType"})
    public void setCategory(String category) {
        this.category = category;
    }

    @Parsed(field = "PaymentMethodId")
    public void setPaymentMethodId(String paymentMethodId) {
        this.paymentMethodId = paymentMethodId;
    }

    @Parsed(field = "Confirmed")
    public void setConfirmed(boolean confirmed) {
        this.confirmed = confirmed;
    }

    @Parsed(field = "Address")
    public void setAddress(String address) {
        this.address = address;
    }

    @Parsed(field = {"PaymentCurrency", "Crypto"})
    public void setPaymentCurrency(Currency paymentCurrency) {
        this.paymentCurrency = paymentCurrency;
    }

    @Parsed(field = {"PaymentAmount", "CryptoAmount"})
    public void setPaymentAmount(BigDecimal paymentAmount) {
        this.paymentAmount = paymentAmount;
    }

    @Parsed(field = "PaymentMethodFee")
    public void setPaymentMethodFee(BigDecimal paymentMethodFee) {
        this.paymentMethodFee = paymentMethodFee;
    }

    @Parsed(field = "LightningAddress")
    public void setLightningAddress(String lightningAddress) {
        this.lightningAddress = lightningAddress;
    }

    @Parsed(field = {"InvoiceCurrency", "Currency"})
    public void setInvoiceCurrency(Currency invoiceCurrency) {
        this.invoiceCurrency = invoiceCurrency;
    }

    @Parsed(field = {"InvoiceCurrencyAmount", "CurrencyAmount"})
    public void setInvoiceCurrencyAmount(BigDecimal invoiceCurrencyAmount) {
        this.invoiceCurrencyAmount = invoiceCurrencyAmount;
    }

    @Parsed(field = "Rate")
    public void setRate(BigDecimal rate) {
        this.rate = rate;
    }

    @Override
    protected TransactionType findTransactionType() {
        return switch (category.toUpperCase()) {
            case "LIGHTNING", "ON-CHAIN" -> {
                if (invoiceCurrency.equals(Currency.SATS)) {
                    feeAmount = paymentAmount
                        .subtract(invoiceCurrencyAmount.divide(new BigDecimal("100000000"), 8, RoundingMode.HALF_UP))
                        .setScale(8, RoundingMode.HALF_UP);
                    paymentAmount = invoiceCurrencyAmount.divide(new BigDecimal("100000000"), 8, RoundingMode.HALF_UP);
                    invoiceCurrency = Currency.BTC;
                    paymentCurrency = invoiceCurrency;
                    yield TransactionType.WITHDRAWAL;
                }
                yield TransactionType.BUY;
            }
            default -> throw new DataIgnoredException("Unknown category");
        };
    }

    @Override
    protected BaseClusterData mapData() {
        if (!confirmed) {
            throw new DataIgnoredException("Unconfirmed payment");
        }
        if (category.equalsIgnoreCase("LIGHTNING")) {
            address = null;
        }

        return BaseClusterData.builder()
            .transactionType(findTransactionType())
            .executed(date)
            .base(paymentCurrency)
            .quote(invoiceCurrency)
            .unitPrice(rate)
            .volume(paymentAmount)
            .fee(paymentCurrency.code())
            .feeAmount(feeAmount)
            .address(address)
            .build();
    }
}
