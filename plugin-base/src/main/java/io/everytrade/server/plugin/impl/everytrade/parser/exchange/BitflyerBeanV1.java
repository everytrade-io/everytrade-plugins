package io.everytrade.server.plugin.impl.everytrade.parser.exchange;

import com.univocity.parsers.annotations.Format;
import com.univocity.parsers.annotations.Headers;
import com.univocity.parsers.annotations.Parsed;
import com.univocity.parsers.common.DataValidationException;
import io.everytrade.server.model.Currency;
import io.everytrade.server.model.SupportedExchange;
import io.everytrade.server.model.TransactionType;
import io.everytrade.server.plugin.api.parser.ExchangeBean;
import io.everytrade.server.plugin.api.parser.ImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.postparse.ConversionParams;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Date;

//MIN> BFY-001:|Trade Date|Trade Type|Traded Price|Currency 1|Amount (Currency 1)|Fee|Currency 2|Order ID|
//FULL> BFY-001:|Trade Date|Product|Trade Type|Traded Price|Currency 1|Amount (Currency 1)|Fee|USD Rate (Currency)
//|Currency 2|Amount (Currency 2)|Order ID|Details|
@Headers(sequence = {"Trade Date", "Trade Type", "Traded Price", "Currency 1", "Amount (Currency 1)", "Fee",
    "Currency 2", "Order ID"}, extract = true)
public class BitflyerBeanV1 extends ExchangeBean {
    private Instant tradeDate;
    private TransactionType tradeType;
    private BigDecimal tradedPrice;
    private Currency currency_1;
    private BigDecimal amountCurrency_1;
    private BigDecimal fee;
    private Currency currency_2;
    private String orderID;

    public BitflyerBeanV1() {
        super(SupportedExchange.BITFLYER);
    }

    @Parsed(field = "Trade Date")
    @Format(formats = {"yyyy/MM/dd HH:mm:ss"}, options = {"locale=US", "timezone=UTC"})
    public void setTradeDate(Date value) {
        tradeDate = value.toInstant();
    }

    @Parsed(field = "Trade Type")
    public void setTradeType(String value) {
        tradeType = detectTransactionType(value);
    }

    @Parsed(field = "Traded Price", defaultNullRead = "0")
    public void setTradedPrice(BigDecimal value) {
        this.tradedPrice = value;
    }

    @Parsed(field = "Currency 1")
    public void setCurrency_1(String value) {
        this.currency_1 = Currency.valueOf(value);
    }

    @Parsed(field = "Amount (Currency 1)", defaultNullRead = "0")
    public void setAmountCurrency_1(BigDecimal value) {
        if (value.compareTo(BigDecimal.ZERO) == 0) {
            throw new DataValidationException("Quantity can not be zero.");
        }
        this.amountCurrency_1 = value;
    }

    @Parsed(field = "Fee", defaultNullRead = "0")
    public void setFee(BigDecimal value) {
        this.fee = value;
    }

    @Parsed(field = "Currency 2")
    public void setCurrency_2(String value) {
        this.currency_2 = Currency.valueOf(value);
    }

    @Parsed(field = "Order ID")
    public void setOrderID(String value) {
        this.orderID = value;
    }

    @Override
    public ImportedTransactionBean toImportedTransactionBean(ConversionParams conversionParams) {
        validateCurrencyPair(currency_1, currency_2);

        return new ImportedTransactionBean(
            orderID,            //uuid
            tradeDate,          //executed
            currency_1,         //base
            currency_2,         //quote
            tradeType,          //action
            amountCurrency_1.abs(), //base quantity
            tradedPrice,        //unit price
            fee                //fee quote
        );
    }
}
