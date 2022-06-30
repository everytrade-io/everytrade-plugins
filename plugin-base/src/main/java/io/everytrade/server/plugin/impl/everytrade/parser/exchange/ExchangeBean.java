package io.everytrade.server.plugin.impl.everytrade.parser.exchange;

import com.univocity.parsers.common.DataValidationException;
import io.everytrade.server.model.Currency;
import io.everytrade.server.model.CurrencyPair;
import io.everytrade.server.model.TransactionType;
import io.everytrade.server.plugin.impl.everytrade.parser.IImportableBean;
import io.everytrade.server.plugin.impl.everytrade.parser.ParserUtils;
import io.everytrade.server.plugin.impl.everytrade.parser.exception.DataIgnoredException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public abstract class ExchangeBean implements IImportableBean {
    public static final String UNSUPPORTED_CURRENCY_PAIR = "Unsupported currency pair ";
    public static final String UNSUPPORTED_TRANSACTION_TYPE = "Unsupported transaction type ";
    public static final String UNSUPPORTED_STATUS_TYPE = "Unsupported status type ";
    public static final String IGNORED_CHARS_IN_NUMBER = "[,\\s\\$]";
    public static final String FEE_UID_PART = "-fee";
    public static final String REBATE_UID_PART = "-rebate";
    public static final String ILLEGAL_NEGATIVE_VALUES = "Illegal negative value(s) at index(es): ";

    private List<String> rowValues;
    private long rowNumber;

    public void setRowValues(String[] row) {
        rowValues = Arrays.asList(row);
    }

    public void setRowNumber(long rowNumber) {
        this.rowNumber = rowNumber;
    }

    public String rowToString() {
        return "line="
            .concat(String.valueOf(rowNumber))
            .concat(", ")
            .concat(String.join(", ", rowValues));
    }

    protected BigDecimal evalUnitPrice(BigDecimal transactionPrice, BigDecimal baseQuantity) {
        return transactionPrice.divide(baseQuantity, ParserUtils.DECIMAL_DIGITS, ParserUtils.ROUNDING_MODE);
    }

    protected static void validateCurrencyPair(Currency base, Currency quote) {
        try {
            var currencyPair = new CurrencyPair(base, quote);
            if (!CurrencyPair.getTradeablePairs().contains(currencyPair)) {
                throw new DataValidationException(UNSUPPORTED_CURRENCY_PAIR.concat(currencyPair.toString()));
            }
        } catch (CurrencyPair.FiatCryptoCombinationException | DataValidationException e) {
            throw new DataValidationException(e.getMessage());
        }
    }

    protected void validatePositivity(BigDecimal... values) {
        List<Integer> negativeValues = new ArrayList<>();
        for (int i = 0; i < values.length; i++) {
            BigDecimal value = values[i];
            if (value.compareTo(BigDecimal.ZERO) < 0) {
                negativeValues.add(i);
            }
        }
        if (!negativeValues.isEmpty()) {
            throw new DataValidationException(
                String.format(ILLEGAL_NEGATIVE_VALUES + "%s", negativeValues)
            );
        }
    }

    protected TransactionType detectTransactionType(Currency fromCurrency, Currency toCurrency) {
        final CurrencyPair tradablePairSell = findTradablePair(fromCurrency, toCurrency);
        final CurrencyPair tradablePairBuy = findTradablePair(toCurrency, fromCurrency);
        if (tradablePairBuy != null && tradablePairSell == null) {
            return TransactionType.BUY;
        } else if (tradablePairBuy == null && tradablePairSell != null) {
            return TransactionType.SELL;
        } else {
            throw new DataValidationException(
                UNSUPPORTED_CURRENCY_PAIR
                    .concat(fromCurrency.code())
                    .concat("/")
                    .concat(toCurrency.code())
            );
        }
    }

    protected static TransactionType detectTransactionType(String value) {
        TransactionType type;
        try {
            type =  TransactionType.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new DataIgnoredException(UNSUPPORTED_TRANSACTION_TYPE.concat(value));
        }
        return type;
    }

    private CurrencyPair findTradablePair(Currency baseCurrency, Currency quoteCurrency) {
        try {
            final CurrencyPair currencyPair = new CurrencyPair(baseCurrency, quoteCurrency);
            return CurrencyPair.getTradeablePairs().contains(currencyPair) ? currencyPair : null;
        } catch (CurrencyPair.FiatCryptoCombinationException e) {
            return null;
        }
    }
}
