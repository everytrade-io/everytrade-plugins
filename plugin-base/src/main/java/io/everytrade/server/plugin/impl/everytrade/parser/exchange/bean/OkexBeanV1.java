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
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.ExchangeBean;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Collections;
import java.util.Date;
import java.util.List;

//'Order ID' values start with BOM (\uFEFF) symbol.
@Headers(sequence = {"\uFEFFTrade ID","\uFEFFTrade Time","\uFEFFPairs","\uFEFFAmount","\uFEFFPrice","\uFEFFTotal",
    "\uFEFFFee", "\uFEFFunit\r"}, extract = true)
public class OkexBeanV1 extends ExchangeBean {
    private String tradeID;
    private Instant tradeTime;
    private Currency pairsBase;
    private Currency pairsQuote;
    private Currency feeCurrency;
    private BigDecimal amount;
    private BigDecimal price;
    private Currency totalCurrency;
    private BigDecimal fee;
    private Currency unit;

    @Parsed(field = "\uFEFFTrade ID")
    public void setTradeID(String value) {
        this.tradeID = value;
    }

    @Parsed(field = "\uFEFFTrade Time")
    @Format(formats = {"yyyy-MM-dd HH:mm:ss"}, options = {"locale=US", "timezone=UTC"})
    public void setTradeTime(Date value) {
        tradeTime = value.toInstant();
    }

    @Parsed(field = "\uFEFFPairs")
    public void setSymbol(String value) {
        final String[] pair = value.split("_");
        pairsBase = Currency.valueOf(pair[0].toUpperCase());
        pairsQuote = Currency.valueOf(pair[1].toUpperCase());
    }

    @Parsed(field = "\uFEFFAmount", defaultNullRead = "0")
    public void setAmount(BigDecimal value) {
        amount = value;
    }

    @Parsed(field = "\uFEFFPrice", defaultNullRead = "0")
    public void setPrice(BigDecimal value) {
        price = value;
    }

    @Parsed(field = "\uFEFFTotal")
    public void setTotalCurrency(String value) {
        final String[] split = value.split(" ");
        totalCurrency = Currency.valueOf(split[1]);
    }

    @Parsed(field = "\uFEFFFee")
    public void setFee(String value) {
        final String[] split = value.split(" ");
        fee = new BigDecimal(split[0]);
        feeCurrency = Currency.valueOf(split[1].toUpperCase());
    }

    @Parsed(field = "\uFEFFunit\r")
    public void setUnit(String value) {
        unit = Currency.valueOf(value);
    }

    @Override
    public TransactionCluster toTransactionCluster() {
        validateCurrencyPair(pairsBase, pairsQuote);

        if (!pairsBase.equals(unit)) {
            throw new DataValidationException(String.format(
                "Pairs-base currency '%s' differs from unit currency '%s'", pairsBase, unit)
            );
        }
        if (!pairsQuote.equals(totalCurrency)) {
            throw new DataValidationException(String.format(
                "Pairs-quote currency '%s' differs from Total currency '%s'", pairsQuote, totalCurrency)
            );
        }
        final TransactionType action = amount.compareTo(BigDecimal.ZERO) > 0 ? TransactionType.BUY :
            TransactionType.SELL;
        final BigDecimal feeConverted;
        if (action.equals(TransactionType.BUY)) {
            if (!feeCurrency.equals(pairsBase)) {
                throw new DataValidationException(
                    String.format("Fee currency '%s' differ to base currency '%s'.",  feeCurrency, pairsBase)
                );
            }
            feeConverted = price.multiply(fee).abs().setScale(10, RoundingMode.HALF_UP);
        } else {
            if (!feeCurrency.equals(pairsQuote)) {
                throw new DataValidationException(
                    String.format("Fee currency '%s' differ to quote currency '%s'.",  feeCurrency, pairsQuote)
                );
            }
            feeConverted = fee.abs();
        }

        List<ImportedTransactionBean> related;
        if (ParserUtils.equalsToZero(feeConverted)) {
            related = Collections.emptyList();
        } else {
            related = List.of(
                new FeeRebateImportedTransactionBean(
                    tradeID + FEE_UID_PART,
                    tradeTime,
                    pairsBase,
                    pairsQuote,
                    TransactionType.FEE,
                    feeConverted,
                    feeCurrency
                )
            );
        }

        return new TransactionCluster(
            new BuySellImportedTransactionBean(
                tradeID,             //uuid
                tradeTime,           //executed
                pairsBase,           //base
                pairsQuote,          //quote
                action,              //action
                amount.abs(),        //base quantity
                price                //unit price
            ),
            related
        );
    }

}
