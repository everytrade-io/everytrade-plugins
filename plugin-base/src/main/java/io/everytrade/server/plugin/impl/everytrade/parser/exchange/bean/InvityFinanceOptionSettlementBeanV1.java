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

import static io.everytrade.server.model.TransactionType.BUY;
import static io.everytrade.server.model.TransactionType.SELL;
import static lombok.AccessLevel.PRIVATE;

@Getter
@Setter
@FieldDefaults(level = PRIVATE)
public class InvityFinanceOptionSettlementBeanV1 extends BaseTransactionMapper {

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
    BigDecimal collateralCryptoAmount;
    BigDecimal optionValueCryptoAmount;
    BigDecimal optionValueFiatAmount;
    BigDecimal customerCryptoAmount;
    BigDecimal customerFiatAmount;
    BigDecimal settlementCryptoAmount;
    BigDecimal settlementFiatAmount;

    boolean sellMode;

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

    @Parsed(field = "optionValueCryptoAmount")
    public void setOptionValueCryptoAmount(BigDecimal optionValueCryptoAmount) {
        this.optionValueCryptoAmount = optionValueCryptoAmount;
    }

    @Parsed(field = "optionValueFiatAmount")
    public void setOptionValueFiatAmount(BigDecimal optionValueFiatAmount) {
        this.optionValueFiatAmount = optionValueFiatAmount;
    }

    @Parsed(field = "customerCryptoAmount")
    public void setCustomerCryptoAmount(BigDecimal customerCryptoAmount) {
        this.customerCryptoAmount = customerCryptoAmount;
    }

    @Parsed(field = "customerFiatAmount")
    public void setCustomerFiatAmount(BigDecimal customerFiatAmount) {
        this.customerFiatAmount = customerFiatAmount;
    }

    @Parsed(field = "settlementCryptoAmount")
    public void setSettlementCryptoAmount(BigDecimal settlementCryptoAmount) {
        this.settlementCryptoAmount = settlementCryptoAmount;
    }

    @Parsed(field = "settlementFiatAmount")
    public void setSettlementFiatAmount(BigDecimal settlementFiatAmount) {
        this.settlementFiatAmount = settlementFiatAmount;
    }

    @Override
    protected TransactionType findTransactionType() {
        if (sellMode) {
            return SELL;
        }
        return BUY;
    }

    @Override
    protected BaseClusterData mapData() {
        return null;
    }

    @Override
    public TransactionCluster toTransactionCluster() {
        if (sellMode) {
            return createSellCluster();
        }
        return createBuyCluster();
    }

    private TransactionCluster createBuyCluster() {
        validateCurrencyPair(cryptoCurrency, fiatCurrency, BUY);
        var tx = new ImportedTransactionBean(
            id,
            finalizedAt,
            cryptoCurrency,
            fiatCurrency,
            BUY,
            settlementCryptoAmount,
            evalUnitPrice(settlementFiatAmount, settlementCryptoAmount),
            "Turbo buy - settlement - splátka Option value od zákazníka",
            null,
            null,
            identityId,
            null
        );
        return new TransactionCluster(tx, java.util.Collections.emptyList());
    }

    private TransactionCluster createSellCluster() {
        validateCurrencyPair(cryptoCurrency, fiatCurrency, SELL);
        var tx = new ImportedTransactionBean(
            id,
            finalizedAt,
            cryptoCurrency,
            fiatCurrency,
            SELL,
            optionValueCryptoAmount,
            evalUnitPrice(settlementFiatAmount, optionValueCryptoAmount),
            "Turbo buy - settlement - prodej Option value 60% BTC zákazníkovi",
            null,
            null,
            identityId,
            null
        );
        return new TransactionCluster(tx, java.util.Collections.emptyList());
    }
}
