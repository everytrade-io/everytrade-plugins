package io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean;

import com.univocity.parsers.annotations.Format;
import com.univocity.parsers.annotations.Headers;
import com.univocity.parsers.annotations.Parsed;
import com.univocity.parsers.common.DataValidationException;
import io.everytrade.server.model.Currency;
import io.everytrade.server.model.TransactionType;
import io.everytrade.server.plugin.api.parser.ImportedTransactionBean;
import io.everytrade.server.plugin.impl.everytrade.parser.exception.DataIgnoredException;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.ExchangeBean;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Date;

//MIN> BTX-001:|OrderUuid|Exchange|Type|Quantity|CommissionPaid|Price|Closed|
//MAX> BTX-001:|OrderUuid|Exchange|Type|Quantity|Limit|CommissionPaid|Price|Opened|Closed|
@Headers(sequence = {"OrderUuid", "Exchange", "Type", "Quantity", "CommissionPaid", "Price", "Closed"}, extract = true)
public class BittrexBeanV1 extends ExchangeBean {
    private String orderUuid;
    private Currency exchangeQuote;
    private Currency exchangeBase;
    private TransactionType type;
    private BigDecimal quantity;
    private BigDecimal comissionPaid;
    private BigDecimal price;
    private Instant closed;

    @Parsed(field = "OrderUuid")
    public void setOrderUuid(String orderUuid) {
        this.orderUuid = orderUuid;
    }

    @Parsed(field = "Exchange")
    public void setExchange(String exchange) {
        String[] exchangeParts = exchange.split("-");
        String mQuote = exchangeParts[0];
        String mBase = exchangeParts[1];
        exchangeQuote = Currency.valueOf(mQuote);
        exchangeBase = Currency.valueOf(mBase);
    }

    @Parsed(field = "Type")
    public void setType(String type) {
        if ("LIMIT_BUY".equals(type)) {
            this.type = TransactionType.BUY;
        } else if("LIMIT_SELL".equals(type)) {
            this.type = TransactionType.SELL;
        } else {
            throw new DataIgnoredException(UNSUPPORTED_TRANSACTION_TYPE.concat(type));
        }
    }

    @Parsed(field = "Quantity", defaultNullRead = "0")
    public void setQuantity(BigDecimal quantity) {
        if (quantity.compareTo(BigDecimal.ZERO) == 0) {
            throw new DataValidationException("Quantity can not be zero.");
        }
        this.quantity = quantity;
    }

    @Parsed(field = "CommissionPaid", defaultNullRead = "0")
    public void setComissionPaid(BigDecimal comissionPaid) {
        this.comissionPaid = comissionPaid;
    }

    @Parsed(field = "Price", defaultNullRead = "0")
    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    @Parsed(field = "Closed")
    @Format(formats = {"MM/dd/yy HH:mm"}, options = {"locale=US", "timezone=UTC"})
    public void setDate(Date date) {
        closed = date.toInstant();
    }


    @Override
    public ImportedTransactionBean toImportedTransactionBean() {
        validateCurrencyPair(exchangeBase, exchangeQuote);

        return new ImportedTransactionBean(
            orderUuid,          //uuid
            closed,             //executed
            exchangeBase,       //base
            exchangeQuote,      //quote
            type,               //action
            quantity,           //base quantity
            evalUnitPrice(price, quantity), //unit price
            comissionPaid       //fee quote
        );
    }
}
