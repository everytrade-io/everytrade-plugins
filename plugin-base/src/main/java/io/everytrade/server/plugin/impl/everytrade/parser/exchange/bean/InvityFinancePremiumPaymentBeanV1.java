package io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean;

import com.univocity.parsers.annotations.Parsed;
import io.everytrade.server.model.Currency;
import io.everytrade.server.model.TransactionType;
import io.everytrade.server.plugin.api.parser.ImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.TransactionCluster;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.kuCoin.BaseTransactionMapper;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;

import static io.everytrade.server.model.TransactionType.REWARD;
import static lombok.AccessLevel.PRIVATE;

@Getter
@Setter
@FieldDefaults(level = PRIVATE)
public class InvityFinancePremiumPaymentBeanV1 extends BaseTransactionMapper {

    private static final DateTimeFormatter DATE_FORMATTER = new DateTimeFormatterBuilder()
        .appendPattern("yyyy-MM-dd HH:mm:ss")
        .appendFraction(ChronoField.NANO_OF_SECOND, 0, 9, true)
        .toFormatter();

    Instant finalizedAt;
    String id;
    String identityId;
    String type;
    Currency fiatCurrency;
    Currency cryptoCurrency;
    BigDecimal premiumCryptoAmount;
    BigDecimal premiumFiatAmount;
    String paymentMethodType;
    String externalId;
    Instant paymentCreated;

    @Parsed(field = "finalizedAt")
    public void setFinalizedAt(String value) {
        if (value != null && !value.trim().isEmpty()) {
            this.finalizedAt = LocalDateTime.parse(value.trim(), DATE_FORMATTER).toInstant(ZoneOffset.UTC);
        }
    }

    public void setFinalizedAt(Instant finalizedAt) {
        this.finalizedAt = finalizedAt;
    }

    @Parsed(field = "id")
    public void setId(String id) {
        this.id = id;
    }

    @Parsed(field = "identityId")
    public void setIdentityId(String identityId) {
        this.identityId = identityId;
    }

    @Parsed(field = "type")
    public void setType(String type) {
        this.type = type;
    }

    @Parsed(field = "fiatCurrency")
    public void setFiatCurrency(String fiatCurrency) {
        this.fiatCurrency = Currency.fromCode(fiatCurrency);
    }

    public void setFiatCurrency(Currency fiatCurrency) {
        this.fiatCurrency = fiatCurrency;
    }

    @Parsed(field = "cryptoCurrency")
    public void setCryptoCurrency(String cryptoCurrency) {
        this.cryptoCurrency = Currency.fromCode(cryptoCurrency);
    }

    public void setCryptoCurrency(Currency cryptoCurrency) {
        this.cryptoCurrency = cryptoCurrency;
    }

    @Parsed(field = "premiumCryptoAmount")
    public void setPremiumCryptoAmount(BigDecimal premiumCryptoAmount) {
        this.premiumCryptoAmount = premiumCryptoAmount;
    }

    @Parsed(field = "premiumFiatAmount")
    public void setPremiumFiatAmount(BigDecimal premiumFiatAmount) {
        this.premiumFiatAmount = premiumFiatAmount;
    }

    @Parsed(field = "paymentMethodType")
    public void setPaymentMethodType(String paymentMethodType) {
        this.paymentMethodType = paymentMethodType;
    }

    @Parsed(field = "externalId")
    public void setExternalId(String externalId) {
        this.externalId = externalId;
    }

    @Parsed(field = "paymentCreated")
    public void setPaymentCreated(String value) {
        if (value != null && !value.trim().isEmpty()) {
            this.paymentCreated = LocalDateTime.parse(value.trim(), DATE_FORMATTER).toInstant(ZoneOffset.UTC);
        }
    }

    public void setPaymentCreated(Instant paymentCreated) {
        this.paymentCreated = paymentCreated;
    }

    boolean isCryptoReward;

    public void setIsCryptoReward(boolean isCryptoReward) {
        this.isCryptoReward = isCryptoReward;
    }

    @Override
    protected TransactionType findTransactionType() {
        return REWARD;
    }

    @Override
    protected BaseClusterData mapData() {
        return null;
    }

    @Override
    public TransactionCluster toTransactionCluster() {
        if (isCryptoReward) {
            return createCryptoRewardCluster();
        }
        return createFiatRewardCluster();
    }

    private TransactionCluster createFiatRewardCluster() {
        validateCurrencyPair(cryptoCurrency, fiatCurrency, REWARD);
        var tx = new ImportedTransactionBean(
            id,
            finalizedAt,
            fiatCurrency,
            fiatCurrency,
            REWARD,
            premiumFiatAmount,
            null,
            "Premium payment fee od zakaznika - FIAT fee",
            null,
            paymentMethodType + " PremiumPayment",
            identityId,
            externalId
        );
        return new TransactionCluster(tx, java.util.Collections.emptyList());
    }

    private TransactionCluster createCryptoRewardCluster() {
        validateCurrencyPair(cryptoCurrency, cryptoCurrency, REWARD);
        var tx = new ImportedTransactionBean(
            id,
            finalizedAt,
            cryptoCurrency,
            cryptoCurrency,
            REWARD,
            premiumCryptoAmount,
            null,
            "Premium payment fee od zakaznika - strzeni BTC z kolateralu",
            null,
            "PremiumPayment",
            identityId,
            externalId
        );
        return new TransactionCluster(tx, java.util.Collections.emptyList());
    }
}
