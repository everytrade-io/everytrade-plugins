package io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean;

import com.univocity.parsers.annotations.Format;
import com.univocity.parsers.annotations.Parsed;
import io.everytrade.server.model.Currency;
import io.everytrade.server.model.TransactionType;
import io.everytrade.server.plugin.impl.everytrade.parser.exception.DataIgnoredException;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.kuCoin.BaseTransactionMapper;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Date;
import java.util.Map;

import static io.everytrade.server.model.TransactionType.BUY;
import static io.everytrade.server.model.TransactionType.DEPOSIT;
import static io.everytrade.server.model.TransactionType.REWARD;
import static io.everytrade.server.model.TransactionType.SELL;
import static io.everytrade.server.model.TransactionType.WITHDRAWAL;
import static lombok.AccessLevel.PRIVATE;

@FieldDefaults(level = PRIVATE)
public class ChangeInvestBeanV1 extends BaseTransactionMapper {

    Instant createdTime;
    String id;
    String orderType;
    Currency fromCurrency;
    BigDecimal fromAmount;
    Currency toCurrency;
    BigDecimal toAmount;
    BigDecimal fee;
    String executionPrice;
    Instant completedTime;

    private static final Map<String, Currency> IDENTIFIED_SYMBOLS = Map.of("EURT", Currency.EUR);

    @Parsed(field = "created_time")
    @Format(formats = {"yyyy-MM-dd HH:mm:ss"}, options = {"locale=EN", "timezone=UTC"})
    public void setCreatedTime(Date createdTime) {
        this.createdTime = createdTime.toInstant();
    }

    @Parsed(field = "id")
    public void setId(String id) {
        this.id = id;
    }

    @Parsed(field = "order_type")
    public void setOrderType(String orderType) {
        this.orderType = orderType.toUpperCase();
    }

    @Parsed(field = "from_currency")
    public void setFromCurrency(String fromCurrency) {
        Currency mappedCurrency = IDENTIFIED_SYMBOLS.get(fromCurrency);
        this.fromCurrency = (mappedCurrency != null) ? mappedCurrency : Currency.fromCode(fromCurrency);
    }

    @Parsed(field = "from_amount")
    public void setFromAmount(String fromAmount) {
        this.fromAmount = new BigDecimal(fromAmount.replace(",", "."));
    }

    @Parsed(field = "to_currency")
    public void setToCurrency(String toCurrency) {
        Currency mappedCurrency = IDENTIFIED_SYMBOLS.get(toCurrency);
        this.toCurrency = (mappedCurrency != null) ? mappedCurrency : Currency.fromCode(toCurrency);
    }

    @Parsed(field = "to_amount")
    public void setToAmount(String toAmount) {
        this.toAmount = new BigDecimal(toAmount.replace(",", "."));
    }

    @Parsed(field = "fee")
    public void setFee(BigDecimal fee) {
        this.fee = fee;
    }

    @Parsed(field = "execution_price")
    public void setExecutionPrice(String executionPrice) {
        this.executionPrice = executionPrice;
    }

    @Parsed(field = "completed_time")
    @Format(formats = {"yyyy-MM-dd HH:mm:ss"}, options = {"locale=EN", "timezone=UTC"})
    public void setCompletedTime(Date completedTime) {
        if (completedTime == null) {
            throw new DataIgnoredException("completedTime is null");
        }
        this.completedTime = completedTime.toInstant();
    }

    @Override
    protected TransactionType findTransactionType() {
        return switch (orderType) {
            case "FIAT_DEPOSIT","CARD_DEPOSIT","CRYPTO_DEPOSIT" -> DEPOSIT;
            case "CAMPAIGN_BONUS" -> REWARD;
            case "BUY" -> BUY;
            case "SELL" -> {
                var tempCurrency = fromCurrency;
                fromCurrency = toCurrency;
                toCurrency = tempCurrency;
                var tempAmount = fromAmount;
                fromAmount = toAmount;
                toAmount = tempAmount;
                yield SELL;
            }
            case "CRYPTO_WITHDRAW", "FIAT_WITHDRAW" ->  {
                fee = fromAmount.subtract(toAmount);
                yield WITHDRAWAL;
            }
            default -> TransactionType.UNKNOWN;
        };
    }

    @Override
    protected BaseClusterData mapData() {
        return BaseClusterData.builder()
            .transactionType(findTransactionType())
            .executed(completedTime)
            .base(toCurrency)
            .quote(fromCurrency)
            .unitPrice(evalUnitPrice(fromAmount, toAmount))
            .volume(toAmount)
            .fee(fromCurrency.toString())
            .feeAmount(fee)
            .build();
    }
}
