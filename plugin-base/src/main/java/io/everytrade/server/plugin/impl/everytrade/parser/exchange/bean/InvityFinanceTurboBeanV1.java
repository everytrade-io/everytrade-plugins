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
import java.util.Collections;

import static io.everytrade.server.model.TransactionType.REWARD;
import static io.everytrade.server.model.TransactionType.SELL;
import static lombok.AccessLevel.PRIVATE;

@Getter
@Setter
@FieldDefaults(level = PRIVATE)
public class InvityFinanceTurboBeanV1 extends BaseTransactionMapper {

    private static final DateTimeFormatter DATE_FORMATTER = new DateTimeFormatterBuilder()
        .appendPattern("yyyy-MM-dd HH:mm:ss")
        .appendFraction(ChronoField.NANO_OF_SECOND, 0, 9, true)
        .toFormatter();

    Instant finalizedAt;
    String id;
    String identityId;
    String type;
    Currency fiatCurrency;
    BigDecimal settledFiatAmount;
    BigDecimal feeAmount;
    BigDecimal optionContractPremium;
    Currency cryptoCurrency;
    BigDecimal collateralCryptoAmount;
    String paymentMethodType;
    String externalId;

    boolean rewardMode;

    @Parsed(field = "finalizedAt")
    public void setFinalizedAt(String value) {
        this.finalizedAt = LocalDateTime.parse(value.trim(), DATE_FORMATTER).toInstant(ZoneOffset.UTC);
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

    @Parsed(field = "settledFiatAmount")
    public void setSettledFiatAmount(BigDecimal settledFiatAmount) {
        this.settledFiatAmount = settledFiatAmount;
    }

    @Parsed(field = "feeAmount")
    public void setFeeAmount(BigDecimal feeAmount) {
        this.feeAmount = feeAmount;
    }

    @Parsed(field = "optionContractPremium")
    public void setOptionContractPremium(BigDecimal optionContractPremium) {
        this.optionContractPremium = optionContractPremium;
    }

    @Parsed(field = "cryptoCurrency")
    public void setCryptoCurrency(String cryptoCurrency) {
        this.cryptoCurrency = Currency.fromCode(cryptoCurrency);
    }

    public void setCryptoCurrency(Currency cryptoCurrency) {
        this.cryptoCurrency = cryptoCurrency;
    }

    @Parsed(field = "collateralCryptoAmount")
    public void setCollateralCryptoAmount(BigDecimal collateralCryptoAmount) {
        this.collateralCryptoAmount = collateralCryptoAmount;
    }

    @Parsed(field = "paymentMethodType")
    public void setPaymentMethodType(String paymentMethodType) {
        this.paymentMethodType = paymentMethodType;
    }

    @Parsed(field = "externalId")
    public void setExternalId(String externalId) {
        this.externalId = externalId;
    }

    @Override
    protected TransactionType findTransactionType() {
        if (rewardMode) {
            return REWARD;
        }
        return SELL;
    }

    @Override
    protected BaseClusterData mapData() {
        return null;
    }

    @Override
    public TransactionCluster toTransactionCluster() {
        if (rewardMode) {
            return createRewardCluster();
        }
        return createMainCluster();
    }

    private TransactionCluster createMainCluster() {
        validateCurrencyPair(cryptoCurrency, fiatCurrency, SELL);
        var tx = new ImportedTransactionBean(
            id,
            finalizedAt,
            cryptoCurrency,
            fiatCurrency,
            SELL,
            collateralCryptoAmount,
            evalUnitPrice(settledFiatAmount, collateralCryptoAmount),
            "Turbo buy - prodej kolaterálu zákazníkovi",
            null,
            paymentMethodType,
            identityId,
            externalId
        );
        return new TransactionCluster(tx, Collections.emptyList());
    }

    private TransactionCluster createRewardCluster() {
        BigDecimal rewardAmount = feeAmount.subtract(optionContractPremium);
        var tx = new ImportedTransactionBean(
            id,
            finalizedAt,
            fiatCurrency,
            fiatCurrency,
            REWARD,
            rewardAmount,
            null,
            "Fiat fees from Turbo buy Add Option Value",
            null,
            paymentMethodType,
            identityId,
            externalId
        );
        return new TransactionCluster(tx, Collections.emptyList());
    }
}
