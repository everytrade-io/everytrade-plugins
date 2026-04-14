package io.everytrade.server.plugin.impl.everytrade.parser.exchange.kuCoin;

import com.univocity.parsers.annotations.Format;
import com.univocity.parsers.annotations.Parsed;
import com.univocity.parsers.common.DataValidationException;
import io.everytrade.server.model.Currency;
import io.everytrade.server.model.TransactionType;
import io.everytrade.server.plugin.impl.everytrade.parser.exception.DataIgnoredException;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean.BaseClusterData;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Date;

import static io.everytrade.server.plugin.impl.everytrade.parser.ParserUtils.DECIMAL_DIGITS;
import static io.everytrade.server.plugin.impl.everytrade.parser.ParserUtils.ROUNDING_MODE;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.kuCoin.KuCoinCurrencySwitcher.SWITCHER;

public class KuCoinBuySellV3 extends BaseTransactionMapper {

    String orderId;
    String paymentAccount;
    BigDecimal sellAmount;
    BigDecimal buyAmount;
    Instant timeOfUpdate;
    KuCoinStatus status;
    BigDecimal unitPrice;

    Currency baseCurrency;
    Currency quoteCurrency;

    @Parsed(field = "UID")
    public void setPair(String orderId) {
        this.orderId = orderId;
    }

    @Parsed(field = "Time of Update(UTC+02:00)")
    @Format(formats = {"dd.MM.yyyy HH:mm:ss", "yyyy-MM-dd HH:mm:ss"}, options = {"locale=US", "timezone=UTC"})
    public void setTimeOfUpdate(Date date) {
        timeOfUpdate = date.toInstant();
    }

    @Parsed(field = "Sell")
    public void setSellAmount(String symbol) {
        try {
            String[] symbolParts = parsePair(symbol);
            this.sellAmount = new BigDecimal(symbolParts[0]);
            this.quoteCurrency = SWITCHER.containsKey(symbolParts[1]) ? SWITCHER.get(symbolParts[1]) : Currency.fromCode(symbolParts[1]);
        } catch (Exception e) {
            throw new DataValidationException(UNSUPPORTED_CURRENCY_PAIR + symbol);
        }
    }

    @Parsed(field = "Buy")
    public void setBuyAmount(String symbol) {
        try {
            String[] symbolParts = parsePair(symbol);
            this.buyAmount = new BigDecimal(symbolParts[0]);
            this.baseCurrency = SWITCHER.containsKey(symbolParts[1]) ? SWITCHER.get(symbolParts[1]) : Currency.fromCode(symbolParts[1]);
        } catch (Exception e) {
            throw new DataValidationException(UNSUPPORTED_CURRENCY_PAIR + symbol);
        }
    }

    @Parsed(field = "Payment Account")
    public void setPaymentAccount(String paymentAccount) {
        this.paymentAccount = paymentAccount;
    }

    @Parsed(field = "Price")
    public void setUnitPrice(String price) {
        String priceStr = price.substring(price.indexOf("=") + 1, price.lastIndexOf(" "));
        this.unitPrice = BigDecimal.ONE.divide(new BigDecimal(priceStr), DECIMAL_DIGITS, ROUNDING_MODE);
    }

    @Parsed(field = "Status")
    public void setStatus(String status) {
        try {
            this.status = KuCoinStatus.valueOf(status);
        } catch (Exception e) {
            throw new DataIgnoredException("Status is not success");
        }
    }


    private static String[] parsePair(String value) {
        return value.split(" ");
    }

    @Override
    protected TransactionType findTransactionType() {
        return TransactionType.BUY;
    }

    @Override
    protected BaseClusterData mapData() {
        return BaseClusterData.builder()
            .transactionType(findTransactionType())
            .uid(orderId)
            .executed(timeOfUpdate)
            .unitPrice(unitPrice)
            .base(baseCurrency)
            .quote(quoteCurrency)
            .quoteAmount(sellAmount)
            .volume(buyAmount)
            .build();
    }
}
