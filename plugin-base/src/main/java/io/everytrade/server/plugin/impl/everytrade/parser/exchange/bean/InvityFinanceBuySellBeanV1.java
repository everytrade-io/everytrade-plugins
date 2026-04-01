package io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean;

import com.univocity.parsers.annotations.Parsed;
import com.univocity.parsers.common.DataValidationException;
import io.everytrade.server.model.Currency;
import io.everytrade.server.model.TransactionType;
import io.everytrade.server.plugin.api.parser.ImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.TransactionCluster;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.kuCoin.BaseTransactionMapper;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.Collections;

import static io.everytrade.server.model.TransactionType.BUY;
import static io.everytrade.server.model.TransactionType.REWARD;
import static io.everytrade.server.model.TransactionType.SELL;
import static lombok.AccessLevel.PRIVATE;

@Getter
@Setter
@FieldDefaults(level = PRIVATE)
@ToString
public class InvityFinanceBuySellBeanV1 extends BaseTransactionMapper {

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
    Currency cryptoCurrency;
    BigDecimal settledCryptoAmount;
    String paymentMethodType;
    String externalId;
    String blockchainTxid;

    boolean rewardMode;
    boolean noncustodialMode;

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

    @Parsed(field = "cryptoCurrency")
    public void setCryptoCurrency(String cryptoCurrency) {
        this.cryptoCurrency = Currency.fromCode(cryptoCurrency);
    }

    public void setCryptoCurrency(Currency cryptoCurrency) {
        this.cryptoCurrency = cryptoCurrency;
    }

    @Parsed(field = "settledCryptoAmount")
    public void setSettledCryptoAmount(BigDecimal settledCryptoAmount) {
        this.settledCryptoAmount = settledCryptoAmount;
    }

    @Parsed(field = "paymentMethodType")
    public void setPaymentMethodType(String paymentMethodType) {
        this.paymentMethodType = paymentMethodType;
    }

    @Parsed(field = "externalId")
    public void setExternalId(String externalId) {
        this.externalId = externalId;
    }

    @Parsed(field = "blockchainTxid")
    public void setBlockchainTxid(String blockchainTxid) {
        this.blockchainTxid = blockchainTxid;
    }

    @Override
    protected TransactionType findTransactionType() {
        if (rewardMode) {
            return REWARD;
        }
        return switch (type) {
            case "Buy", "Savings" -> SELL;
            case "Sell" -> BUY;
            default -> throw new DataValidationException("Unsupported Invity transaction type: " + type);
        };
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
        var txType = findTransactionType();
        validateCurrencyPair(cryptoCurrency, fiatCurrency, txType);
        var tx = new ImportedTransactionBean(
            id,
            finalizedAt,
            cryptoCurrency,
            fiatCurrency,
            txType,
            settledCryptoAmount,
            evalUnitPrice(settledFiatAmount, settledCryptoAmount),
            resolveNote(),
            null,
            paymentMethodType,
            identityId,
            externalId
        );
        return new TransactionCluster(tx, Collections.emptyList());
    }

    private TransactionCluster createRewardCluster() {
        var tx = new ImportedTransactionBean(
            id,
            finalizedAt,
            fiatCurrency,
            fiatCurrency,
            REWARD,
            feeAmount,
            null,
            resolveRewardNote(),
            null,
            paymentMethodType,
            identityId,
            externalId
        );
        return new TransactionCluster(tx, Collections.emptyList());
    }

    private String resolveNote() {
        if (noncustodialMode) {
            return switch (type) {
                case "Buy", "Savings" -> "Non-custodial buy";
                case "Sell" -> "Non-custodial sell";
                default -> type;
            };
        }
        return switch (type) {
            case "Buy", "Savings" -> "Custodial buy";
            case "Sell" -> "Custodial sell";
            default -> type;
        };
    }

    private String resolveRewardNote() {
        if (noncustodialMode) {
            return switch (type) {
                case "Buy", "Savings" -> "Fiat fees from non-custodial buy";
                case "Sell" -> "Fiat fees from non-custodial sell";
                default -> "Fiat fees from " + type;
            };
        }
        return switch (type) {
            case "Buy", "Savings" -> "Fiat fees from custodial buy";
            case "Sell" -> "Fiat fees from custodial sell";
            default -> "Fiat fees from " + type;
        };
    }
}
