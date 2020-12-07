package io.everytrade.server.parser.exchange;

import com.okcoin.commons.okex.open.api.bean.spot.result.OrderInfo;
import com.univocity.parsers.common.DataValidationException;
import io.everytrade.server.model.Currency;
import io.everytrade.server.model.CurrencyPair;
import io.everytrade.server.model.TransactionType;
import io.everytrade.server.plugin.api.parser.BuySellImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.TransactionCluster;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class OkexApiTransactionBean {
    public static final int DECIMAL_SCALE = 10;
    public static final String FULLY_FILLED = "2";
    private final String orderId;
    private final Instant timeStamp;
    private final TransactionType side;
    private final Currency instrumentIdBase;
    private final Currency instrumentIdQuote;
    private final BigDecimal filledSize;
    private final BigDecimal priceAvg;
    private final Currency feeCurrency;
    private final BigDecimal fee;
    private static final Map<String, CurrencyPair> FAST_CURRENCY_PAIRS;
    static {
        FAST_CURRENCY_PAIRS = new HashMap<>();
        for (CurrencyPair currencyPair : CurrencyPair.getTradeablePairs()) {
            FAST_CURRENCY_PAIRS.put(
                String.format("%s-%s", currencyPair.getBase().name(), currencyPair.getQuote().name()), currencyPair
            );
        }
    }

    public OkexApiTransactionBean(OrderInfo orderInfo) {
        Objects.requireNonNull(orderInfo);
        if (!orderInfo.getState().equals(FULLY_FILLED)) {
            throw new DataValidationException(String.format("Unsupported status type '%s'.", orderInfo.getState()));
        }
        orderId = orderInfo.getOrder_id();
        timeStamp = Instant.parse(orderInfo.getTimestamp());
        side = TransactionType.valueOf(orderInfo.getSide().toUpperCase());
        instrumentIdBase = toCurrencyPair(orderInfo.getInstrument_id()).getBase();
        instrumentIdQuote = toCurrencyPair(orderInfo.getInstrument_id()).getQuote();
        feeCurrency = Currency.valueOf(orderInfo.getFee_currency());
        filledSize = new BigDecimal(orderInfo.getFilled_size());
        priceAvg = new BigDecimal(orderInfo.getPrice_avg());
        fee = new BigDecimal(orderInfo.getFee());
    }

    public TransactionCluster toTransactionCluster() {
        try {
            new CurrencyPair(instrumentIdBase, instrumentIdQuote);
        } catch (CurrencyPair.FiatCryptoCombinationException e) {
            throw new DataValidationException(e.getMessage());
        }
        final BigDecimal convertedFee;
        if (TransactionType.BUY.equals(side)) {
            if (!feeCurrency.equals(instrumentIdBase)) {
                throw new DataValidationException(
                    String.format("Fee currency '%s' differ to base currency '%s'.",  feeCurrency, instrumentIdBase)
                );
            }
            convertedFee = priceAvg.multiply(fee).setScale(DECIMAL_SCALE, RoundingMode.HALF_UP).abs();
        } else {
            if (!feeCurrency.equals(instrumentIdQuote)) {
                throw new DataValidationException(
                    String.format("Fee currency '%s' differ to quote currency '%s'.",  feeCurrency, instrumentIdQuote)
                );
            }
            convertedFee = fee.abs();
        }
        final BuySellImportedTransactionBean buySellImportedTransactionBean = new BuySellImportedTransactionBean(
            orderId,             //uuid
            timeStamp,           //executed
            instrumentIdBase,    //base
            instrumentIdQuote,   //quote
            side,                //action
            filledSize,          //base quantity
            priceAvg//,           //unit price
            //convertedFee   //fee quote
        );
        return new TransactionCluster(
            buySellImportedTransactionBean,
            Collections.emptyList()  //TODO: ET-700 - mcharvat - add related transactions (e.g. fees)
        );
    }

    private CurrencyPair toCurrencyPair(String instrumentId) {
        final CurrencyPair currencyPair = FAST_CURRENCY_PAIRS.get(instrumentId);
        if (currencyPair == null) {
            throw new DataValidationException(String.format("Unsupported currency pair '%s'.", instrumentId));
        }
        return currencyPair;
    }
}