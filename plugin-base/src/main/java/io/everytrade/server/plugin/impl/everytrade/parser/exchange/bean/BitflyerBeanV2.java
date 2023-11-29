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
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.ExchangeBean;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import static io.everytrade.server.model.TransactionType.WITHDRAWAL;

@Getter
@Setter
@Headers(sequence = {"Trade Date", "Trade Type", "Traded Price", "Currency 1", "Amount (Currency 1)",
    "Amount (Currency 2)", "Fee", "Currency 2", "Order ID"}, extract = true)
public class BitflyerBeanV2 extends ExchangeBean {
    private Instant tradeDate;
    private String tradeType;
    private String tradedPrice;
    private String currency1;
    private String amountCurrency1;
    private String amountCurrency2;
    private String fee;
    private String currency2;
    private String orderID;

    private String feeCurrency;

    private String message;
    private boolean unsupportedRow;

    @Parsed(field = "Trade Date")
    @Format(formats = {"yyyy/MM/dd HH:mm:ss"}, options = {"locale=US", "timezone=UTC"})
    public void setTradeDate(Date value) {
        tradeDate = value.toInstant();
    }

    @Parsed(field = "Currency 2")
    public void setCurrency2(String value) {
        this.currency2 = value;
    }

    @Parsed(field = "Trade Type")
    public void setTradeType(String value) {
        tradeType = value;
    }

    @Parsed(field = "Traded Price", defaultNullRead = "0")
    public void setTradedPrice(String value) {
        this.tradedPrice = value;
    }

    @Parsed(field = "Currency 1")
    public void setCurrency1(String value) {
        this.currency1 = value;
    }

    @Parsed(field = "Amount (Currency 1)", defaultNullRead = "0")
    public void setAmountCurrency1(String value) {
        this.amountCurrency1 = value;
    }

    @Parsed(field = "Amount (Currency 2)")
    public void setAmountCurrency2(String value) {
        this.amountCurrency2 = value;
    }

    @Parsed(field = "Fee", defaultNullRead = "0")
    public void setFee(String value) {
        this.fee = value;
    }

    @Parsed(field = "Order ID")
    public void setOrderID(String value) {
        this.orderID = value;
    }

    @Override
    public TransactionCluster toTransactionCluster() {
        if(unsupportedRow == true) {
            throw new DataIgnoredException(getMessage());
        }
        if(this.orderID.equals("JOR20210601-134107-475324X")) {
            var test = 1;
            var test2 = test;
        }
        TransactionType type = detectTransactionType(tradeType);
        Currency currencyOne = Currency.valueOf(currency1);
        Currency feeCurrency = this.feeCurrency != null ? Currency.valueOf(this.feeCurrency) : currencyOne;
        BigDecimal amountCurrencyOne = setAmountFromString(amountCurrency1);
        BigDecimal amountCurrencyTwo = setAmountFromString(amountCurrency2);
        BigDecimal feeAmount = setAmountFromString(this.fee);

        TransactionCluster cluster;

        List<ImportedTransactionBean> related;
        if (feeAmount == null | ParserUtils.equalsToZero(feeAmount)) {
            related = Collections.emptyList();
        } else {
            related = List.of(new FeeRebateImportedTransactionBean(
                    orderID + FEE_UID_PART,
                    tradeDate,
                    feeCurrency,
                    feeCurrency,
                    TransactionType.FEE,
                    feeAmount,
                    feeCurrency
                )
            );
        }
        switch (type) {
            case BUY:
            case SELL:
                Currency currencyTwo = Currency.valueOf(currency2);
                validateCurrencyPair(currencyOne, currencyTwo);

                cluster = new TransactionCluster(
                    new ImportedTransactionBean(
                        orderID,
                        tradeDate,
                        currencyOne,
                        currencyTwo,
                        type,
                        amountCurrencyOne.setScale(ParserUtils.DECIMAL_DIGITS, ParserUtils.ROUNDING_MODE),
                        evalUnitPrice(amountCurrencyTwo, amountCurrencyOne).setScale(ParserUtils.DECIMAL_DIGITS, ParserUtils.ROUNDING_MODE)
                    ),
                    related
                );
                break;
            case DEPOSIT:
            case WITHDRAWAL:
                cluster = new TransactionCluster(
                    ImportedTransactionBean.createDepositWithdrawal(
                        orderID,
                        tradeDate,
                        currencyOne,
                        currencyOne,
                        type,
                        amountCurrencyOne.setScale(ParserUtils.DECIMAL_DIGITS, ParserUtils.ROUNDING_MODE),
                        null
                    ),
                    related
                );
                break;
            case FEE:
                cluster = new TransactionCluster(
                    new FeeRebateImportedTransactionBean(
                        orderID + FEE_UID_PART,
                        tradeDate,
                        currencyOne,
                        currencyOne,
                        TransactionType.FEE,
                        amountCurrencyOne,
                        currencyOne),
                    related
                );
                break;
            default:
                throw new DataValidationException("Unsupported transaction ");
        }
        return cluster;

    }

    protected static TransactionType detectTransactionType(String value) {
        try {
            if (value != null) {
                value = value.toUpperCase();
            }
            if (value.contains("WITHDRAW")) {
                return WITHDRAWAL;
            }
            List<String> types = Arrays.stream(TransactionType.values()).map(e -> e.toString().toUpperCase()).collect(Collectors.toList());
            String finalValue = value;
            String type = types.stream().filter(
                t -> finalValue.contains(t)
            ).findFirst().get();
            return TransactionType.valueOf(type);
        } catch (Exception e) {
            throw new DataIgnoredException(String.format("Cannot detect transaction type %s. ", value));
        }
    }

    public void setMessage(String mess) {
        this.message += mess + "; ";
    }

}
