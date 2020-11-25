package io.everytrade.server.plugin.impl.everytrade.parser.exchange;

import com.univocity.parsers.annotations.Format;
import com.univocity.parsers.annotations.Headers;
import com.univocity.parsers.annotations.Parsed;
import com.univocity.parsers.annotations.Replace;
import io.everytrade.server.model.Currency;
import io.everytrade.server.model.SupportedExchange;
import io.everytrade.server.model.TransactionType;
import io.everytrade.server.plugin.api.parser.ExchangeBean;
import io.everytrade.server.plugin.api.parser.ImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.postparse.ConversionParams;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Date;

//MIN> CSQ-001:|date|action|currency|base_currency|amount|base_amount|
//FULL> CSQ-001:|date|action|currency|base_currency|price|amount|base_amount|
@Headers(sequence = {"date","action","currency","base_currency","amount","base_amount"}, extract = true)
public class CoinsquareBeanV1 extends ExchangeBean {
    private Instant date;
    private TransactionType action;
    private Currency currenncy;
    private Currency baseCurrenncy;
    private BigDecimal amount;
    private BigDecimal baseAmount;

    public CoinsquareBeanV1() {
        super(SupportedExchange.COINSQUARE);
    }

    @Parsed(field = "date")
    @Format(formats = {"dd-MM-yy"}, options = {"locale=EN", "timezone=UTC"})
    public void setDate(Date value) {
        date = value.toInstant();
    }

    @Parsed(field = "action")
    public void setAction(String value){
        action = detectTransactionType(value);
    }

    @Parsed(field = "currency")
    public void setCurrency(String value) {
        currenncy = Currency.valueOf(value);
    }

    @Parsed(field = "base_currency")
    public void setBaseCurrency(String value) {
        baseCurrenncy = Currency.valueOf(value);
    }

    @Parsed(field = "amount")
    @Replace(expression = IGNORED_CHARS_IN_NUMBER, replacement = "")
    public void setAmount(BigDecimal value) {
        amount = value;
    }

    @Parsed(field = "base_amount")
    @Replace(expression = IGNORED_CHARS_IN_NUMBER, replacement = "")
    public void setBaseAmount(BigDecimal value) {
        baseAmount = value;
    }

    @Override
    public ImportedTransactionBean toImportedTransactionBean(ConversionParams conversionParams) {
        validateCurrencyPair(baseCurrenncy, currenncy);

        return new ImportedTransactionBean(
            null,         //uuid
            date,              //executed
            baseCurrenncy,     //base
            currenncy,         //quote
            action,            //action
            baseAmount.abs(),  //base quantity
            evalUnitPrice(amount.abs(), baseAmount.abs()),   //unit price
            BigDecimal.ZERO    //fee quote
        );
    }
}
