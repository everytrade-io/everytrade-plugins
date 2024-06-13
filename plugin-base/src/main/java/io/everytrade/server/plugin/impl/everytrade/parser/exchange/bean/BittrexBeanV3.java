package io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean;

import com.univocity.parsers.annotations.Format;
import com.univocity.parsers.annotations.Headers;
import com.univocity.parsers.annotations.Parsed;
import com.univocity.parsers.common.DataValidationException;
import io.everytrade.server.model.Currency;
import io.everytrade.server.model.TransactionType;
import io.everytrade.server.plugin.api.parser.FeeRebateImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.ImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.TransactionCluster;
import io.everytrade.server.plugin.impl.everytrade.parser.ParserUtils;
import io.everytrade.server.plugin.impl.everytrade.parser.exception.DataIgnoredException;
import io.everytrade.server.plugin.impl.everytrade.parser.exception.ParserErrorCurrencyException;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.ExchangeBean;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import static io.everytrade.server.model.TransactionType.BUY;
import static io.everytrade.server.model.TransactionType.SELL;

@Headers(sequence = {"Uuid", "Exchange", "OrderType", "Quantity", "Commission", "PricePerUnit", "Closed"}, extract =
    true)
public class BittrexBeanV3 extends ExchangeBean {
    private String uuid;
    private Currency exchangeQuote;
    private Currency exchangeBase;
    private TransactionType orderType;
    private BigDecimal quantity;
    private BigDecimal commission;
    private BigDecimal pricePerUnit;
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
        try {
            exchangeQuote = Currency.fromCode(mQuote);
            exchangeBase = Currency.fromCode(mBase);
        } catch (IllegalArgumentException e) {
            throw new ParserErrorCurrencyException("Unknown currency pair: " + exchange);
        }
    }

    @Parsed(field = "OrderType")
    public void setOrderType(String orderType) {
        if ("LIMIT_BUY".equalsIgnoreCase(orderType) || "CEILING_MARKET_BUY".equalsIgnoreCase(orderType)
            || "MARKET_BUY".equalsIgnoreCase(orderType)) {
            this.orderType = BUY;
        } else if ("LIMIT_SELL".equalsIgnoreCase(orderType) || "MARKET_SELL".equalsIgnoreCase(orderType)) {
            this.orderType = SELL;
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
    public void setCommission(BigDecimal commission) {
        this.commission = commission;
    }

    @Parsed(field = "PricePerUnit", defaultNullRead = "0")
    public void setPricePerUnit(BigDecimal value) {
        this.pricePerUnit = value;
    }

    @Parsed(field = "Closed")
    @Format(formats = {"MM/dd/yy hh:mm:ss a"}, options = {"locale=US", "timezone=UTC"})
    public void setClosed(Date closed) {
        this.closed = closed.toInstant();
    }

    @Override
    public TransactionCluster toTransactionCluster() {
        validateCurrencyPair(exchangeBase, exchangeQuote);
        validatePositivity(quantity, pricePerUnit, commission);

        List<ImportedTransactionBean> related;
        if (ParserUtils.equalsToZero(commission)) {
            related = Collections.emptyList();
        } else {
            related = List.of(
                new FeeRebateImportedTransactionBean(
                    uuid + FEE_UID_PART,
                    closed,
                    exchangeQuote,
                    exchangeQuote,
                    TransactionType.FEE,
                    commission.setScale(ParserUtils.DECIMAL_DIGITS, ParserUtils.ROUNDING_MODE),
                    exchangeQuote
                )
            );
        }

        return new TransactionCluster(
            new ImportedTransactionBean(
                uuid,               //uuid
                closed,             //executed
                exchangeBase,       //base
                exchangeQuote,      //quote
                orderType,          //action
                quantity.setScale(ParserUtils.DECIMAL_DIGITS, ParserUtils.ROUNDING_MODE),    //base quantity
                pricePerUnit.setScale(ParserUtils.DECIMAL_DIGITS, ParserUtils.ROUNDING_MODE) //unit price
            ),
            related
        );
    }
}
