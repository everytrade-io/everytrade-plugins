package io.everytrade.server.parser.exchange;

//import com.univocity.parsers.common.DataValidationException;
import com.univocity.parsers.common.DataValidationException;
import io.everytrade.server.model.CurrencyPair;
import io.everytrade.server.model.TransactionType;
import io.everytrade.server.model.Currency;
import io.everytrade.server.model.SupportedExchange;
import io.everytrade.server.plugin.api.parser.ImportedTransactionBean;
//import io.everytrade.server.parser.postparse.ConversionParams;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.trade.UserTrade;
import org.knowm.xchange.instrument.Instrument;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

public class XChangeApiTransactionBean /*extends ExchangeBean*/ {
    private final String id;
    private final Instant timestamp;
    private final TransactionType type;
    private final Currency base;
    private final Currency quote;
    private final Currency feeCurrency;
    private final BigDecimal originalAmount;
    private final BigDecimal price;
    private final BigDecimal feeAmount;

    public XChangeApiTransactionBean(UserTrade userTrade, SupportedExchange supportedExchange) {
//        super(supportedExchange);
        id = userTrade.getId();
        timestamp = userTrade.getTimestamp().toInstant();
        type = getTransactionType(userTrade.getType());

        final Instrument instrument = userTrade.getInstrument();
        if (!(instrument instanceof org.knowm.xchange.currency.CurrencyPair)) {
            throw new DataValidationException("Derivatives are not supported yet.");
        }
        org.knowm.xchange.currency.CurrencyPair currencyPair = (org.knowm.xchange.currency.CurrencyPair) instrument;
        base = convert(currencyPair.base);
        quote = convert(currencyPair.counter);
        originalAmount = userTrade.getOriginalAmount();
        price = userTrade.getPrice();
        feeAmount = userTrade.getFeeAmount();
        feeCurrency = convert(userTrade.getFeeCurrency());

    }

//    @Override
//    public ImportedTransactionBean toImportedTransactionBean(ConversionParams conversionParams) {
//        validateCurrencyPair(base, quote);
//
//        return new ImportedTransactionBean(
//            id,                             //uuid
//            timestamp,                       //executed
//            base,                            //base
//            quote,                           //quote
//            type,                           //action
//            originalAmount,                  //base quantity
//            price,                           //unit price
//            evalTransactionPrice(price, originalAmount),  //transaction price
//            feeAmount                        //fee quote
//        );
//    }
    public ImportedTransactionBean toImportedTransactionBean() {
        try {
            new CurrencyPair(base, quote);
        } catch (CurrencyPair.FiatCryptoCombinationException e) {
            throw new DataValidationException(e.getMessage());
        }


        return new ImportedTransactionBean(
            id,                             //uuid
            timestamp,                       //executed
            base,                            //base
            quote,                           //quote
            type,                           //action
            originalAmount,                  //base quantity
            price,                           //unit price
            price.divide(originalAmount, 10, RoundingMode.HALF_UP), //transaction price
            feeAmount                        //fee quote
        );
    }

    @Override
    public String toString() {
        return "KrakenApiTransactionBean{" +
            "id='" + id + '\'' +
            ", timestamp=" + timestamp +
            ", type=" + type +
            ", base=" + base +
            ", quote=" + quote +
            ", feeCurrency=" + feeCurrency +
            ", originalAmount=" + originalAmount +
            ", price=" + price +
            ", feeAmount=" + feeAmount +
            '}';
    }

    private TransactionType getTransactionType(Order.OrderType orderType) {
        switch (orderType) {
            case ASK:
                return TransactionType.SELL;
            case BID:
                return TransactionType.BUY;
            default:
                throw new DataValidationException(
                    "ExchangeBean.UNSUPPORTED_TRANSACTION_TYPE ".concat(orderType.name())
                );
        }
    }

    private Currency convert(org.knowm.xchange.currency.Currency currency) {
        try {
            return Currency.valueOf(currency.getCurrencyCode());
        } catch (IllegalArgumentException e) {
            final org.knowm.xchange.currency.Currency currencyConverted =
                org.knowm.xchange.currency.Currency.getInstance(currency.getCurrencyCode()).getCommonlyUsedCurrency();
            return Currency.valueOf(currencyConverted.getCurrencyCode());
        }
    }
}
