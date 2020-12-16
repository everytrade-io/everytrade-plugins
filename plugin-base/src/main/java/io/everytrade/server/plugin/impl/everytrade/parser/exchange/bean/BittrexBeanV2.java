package io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean;

import com.univocity.parsers.annotations.Format;
import com.univocity.parsers.annotations.Headers;
import com.univocity.parsers.annotations.Parsed;
import com.univocity.parsers.common.DataValidationException;
import io.everytrade.server.model.Currency;
import io.everytrade.server.model.TransactionType;
import io.everytrade.server.plugin.api.parser.BuySellImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.FeeRebateImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.ImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.TransactionCluster;
import io.everytrade.server.plugin.impl.everytrade.parser.ParserUtils;
import io.everytrade.server.plugin.impl.everytrade.parser.exception.DataIgnoredException;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.ExchangeBean;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.Date;
import java.util.List;

//MIN> BTX-002:|Uuid|Exchange|OrderType|Quantity|Commission|Price|Closed|
//MAX> BTX-002:|Uuid|Exchange|TimeStamp|OrderType|Limit|Quantity|QuantityRemaining|Commission|Price|PricePerUnit|
//              IsConditional|Condition|ConditionTarget|ImmediateOrCancel|Closed|
@Headers(sequence = {"Uuid", "Exchange", "OrderType", "Quantity", "Commission", "Price", "Closed"}, extract = true)
public class BittrexBeanV2 extends ExchangeBean {
    private String uuid;
    private Currency exchangeQuote;
    private Currency exchangeBase;
    private TransactionType orderType;
    private BigDecimal quantity;
    private BigDecimal comission;
    private BigDecimal price;
    private Instant closed;

    @Parsed(field = "Uuid")
    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    @Parsed(field = "Exchange")
    public void setEchange(String exchange) {
        String[] exchangeParts = exchange.split("-");
        String mQuote = exchangeParts[0];
        String mBase = exchangeParts[1];
        exchangeQuote = Currency.valueOf(mQuote);
        exchangeBase = Currency.valueOf(mBase);
    }

    @Parsed(field = "OrderType")
    public void setOrderType(String orderType) {
        if ("LIMIT_BUY".equals(orderType)) {
            this.orderType = TransactionType.BUY;
        } else if ("LIMIT_SELL".equals(orderType)) {
            this.orderType = TransactionType.SELL;
        } else {
            throw new DataIgnoredException(UNSUPPORTED_TRANSACTION_TYPE.concat(orderType));
        }
    }

    @Parsed(field = "Quantity", defaultNullRead = "0")
    public void setQuantity(BigDecimal quantity) {
        if (quantity.compareTo(BigDecimal.ZERO) == 0) {
            throw new DataValidationException("Quantity can not be zero.");
        }
        this.quantity = quantity;
    }

    @Parsed(field = "Commission", defaultNullRead = "0")
    public void setComission(BigDecimal comission) {
        this.comission = comission;
    }

    @Parsed(field = "Price", defaultNullRead = "0")
    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    @Parsed(field = "Closed")
    @Format(formats = {"MM/dd/yy hh:mm:ss a"}, options = {"locale=US", "timezone=UTC"})
    public void setClosed(Date closed) {
        this.closed = closed.toInstant();
    }

    @Override
    public TransactionCluster toTransactionCluster() {
        validateCurrencyPair(exchangeBase, exchangeQuote);

        List<ImportedTransactionBean> related;
        if (ParserUtils.equalsToZero(comission)) {
            related = Collections.emptyList();
        } else {
            related = List.of(
                new FeeRebateImportedTransactionBean(
                    uuid + FEE_UID_PART,
                    closed,
                    exchangeBase,
                    exchangeQuote,
                    TransactionType.FEE,
                    comission,
                    exchangeQuote
                )
            );
        }

        return new TransactionCluster(
            new BuySellImportedTransactionBean(
                uuid,               //uuid
                closed,             //executed
                exchangeBase,       //base
                exchangeQuote,      //quote
                orderType,          //action
                quantity,           //base quantity
                evalUnitPrice(price, quantity) //unit price
            ),
            related
        );
    }

}
