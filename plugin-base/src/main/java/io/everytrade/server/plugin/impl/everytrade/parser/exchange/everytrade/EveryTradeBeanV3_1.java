package io.everytrade.server.plugin.impl.everytrade.parser.exchange.everytrade;

import com.univocity.parsers.annotations.Format;
import com.univocity.parsers.annotations.Headers;
import com.univocity.parsers.annotations.Parsed;
import com.univocity.parsers.common.DataValidationException;
import io.everytrade.server.model.Currency;
import io.everytrade.server.model.CurrencyPair;
import io.everytrade.server.model.TransactionType;
import io.everytrade.server.plugin.api.parser.FeeRebateImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.ImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.TransactionCluster;
import io.everytrade.server.plugin.impl.everytrade.parser.EverytradeCSVParserValidator;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.ExchangeBean;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static io.everytrade.server.model.TransactionType.DEPOSIT;
import static io.everytrade.server.plugin.impl.everytrade.parser.ParserUtils.nullOrZero;
import static java.math.BigDecimal.ZERO;
import static lombok.AccessLevel.PRIVATE;

@Headers(sequence = {
    "UID", "DATE", "SYMBOL", "ACTION", "QUANTY", "PRICE", "FEE", "FEE_CURRENCY", "REBATE", "REBATE_CURRENCY", "ADDRESS_FROM", "ADDRESS_TO"
}, extract = true)
@FieldDefaults(level = PRIVATE)
public class EveryTradeBeanV3_1 extends ExchangeBean {

    @Parsed(field = "UID")
    String uid;
    Instant date;
    Currency symbolBase;
    Currency symbolQuote;
    BigDecimal price;
    BigDecimal quantity;
    BigDecimal fee;
    Currency feeCurrency;
    BigDecimal rebate;
    Currency rebateCurrency;
    TransactionType action;

    @Parsed(field = "QUANTY", defaultNullRead = "0")
    public void setQuanty(String value) {
        quantity = EverytradeCSVParserValidator.parserNumber(value);
    }

    @Parsed(field = "PRICE", defaultNullRead = "0")
    public void setPrice(String value) {
        price = EverytradeCSVParserValidator.parserNumber(value);
    }

    @Parsed(field = "FEE", defaultNullRead = "0")
    public void setFee(String value){
        fee = EverytradeCSVParserValidator.parserNumber(value);
    }

    @Parsed(field = "REBATE", defaultNullRead = "0")
    public void setRebate(String value){
        rebate = EverytradeCSVParserValidator.parserNumber(value);
    }

    @Parsed(field = "ADDRESS_FROM")
    String addressFrom;
    @Parsed(field = "ADDRESS_TO")
    String addressTo;

    @Parsed(field = "DATE")
    @Format(formats = {"dd.MM.yy HH:mm:ss", "yyyy-MM-dd HH:mm:ss"}, options = {"locale=US", "timezone=UTC"})
    public void setDate(Date value) {
        date = value.toInstant();
    }

    @Parsed(field = "SYMBOL")
    public void setSymbol(String value) {
        CurrencyPair symbolParts = EverytradeCSVParserValidator.parseSymbol(value);
        symbolBase = symbolParts.getBase();
        symbolQuote = symbolParts.getQuote();
    }

    @Parsed(field = "ACTION")
    public void setAction(String value) {
        action = detectTransactionType(value);
    }

    @Parsed(field = "FEE_CURRENCY")
    public void setFeeCurrency(String value) {
        feeCurrency = value == null ? null : Currency.fromCode(EverytradeCSVParserValidator.correctCurrency(value));
    }

    @Parsed(field = "REBATE_CURRENCY")
    public void setRebateCurrency(String value) {
        rebateCurrency = value == null ? null : Currency.fromCode(EverytradeCSVParserValidator.correctCurrency(value));
    }

    @Override
    public TransactionCluster toTransactionCluster() {
        validateCurrencyPair(symbolBase, symbolQuote, action);
        validatePositivity(quantity, price, fee, rebate);
        switch (action) {
            case BUY:
            case SELL:
                return createBuySellTransactionCluster();
            case DEPOSIT:
            case WITHDRAWAL:
                return createDepositOrWithdrawalTxCluster();
            case FEE:
                return new TransactionCluster(createFeeTransactionBean(true), List.of());
            case REBATE:
                try{
                    return new TransactionCluster(createRebateTransactionBean(true), List.of());
                } catch (Exception e) {
                    throw new DataValidationException(String.format("Wrong rebate data: %s", e.getMessage()));
                }
            default:
                throw new IllegalStateException(String.format("Unsupported transaction type %s.", action));
        }
    }

    private TransactionCluster createDepositOrWithdrawalTxCluster() {
        var tx = ImportedTransactionBean.createDepositWithdrawal(
            uid,
            date,
            symbolBase,
            symbolQuote,
            action,
            quantity,
            action == DEPOSIT ? addressFrom : addressTo
        );

        return new TransactionCluster(tx, getRelatedTxs());
    }

    private TransactionCluster createBuySellTransactionCluster() {
        if (quantity.compareTo(ZERO) == 0) {
            throw new DataValidationException("Quantity can not be zero.");
        }
        if (price.compareTo(ZERO) == 0) {
            throw new DataValidationException("Price can not be zero.");
        }

        var tx = new ImportedTransactionBean(
            uid,               //uuid
            date,               //executed
            symbolBase,         //base
            symbolQuote,        //quote
            action,             //action
            quantity,           //base quantity
            price              //unit price
        );

        TransactionCluster transactionCluster = new TransactionCluster(tx, getRelatedTxs());
        if (!nullOrZero(fee) && feeCurrency == null) {
            transactionCluster.setFailedFee(1, "Fee currency is null. ");
        }
        return transactionCluster;
    }

    private List<ImportedTransactionBean> getRelatedTxs() {
        var related = new ArrayList<ImportedTransactionBean>();
        if (!nullOrZero(fee) && feeCurrency != null) {
            related.add(createFeeTransactionBean(false));
        }

        if (!nullOrZero(rebate) && rebateCurrency != null) {
            related.add(createRebateTransactionBean(false));
        }
        return related;
    }

    private FeeRebateImportedTransactionBean createFeeTransactionBean(boolean unrelated) {
        return new FeeRebateImportedTransactionBean(
            unrelated ? uid : uid + FEE_UID_PART,
            date,
            feeCurrency != null ? feeCurrency : symbolBase,
            feeCurrency != null ? feeCurrency : symbolQuote,
            TransactionType.FEE,
            fee,
            feeCurrency
        );
    }

    private FeeRebateImportedTransactionBean createRebateTransactionBean(boolean unrelated) {
        return new FeeRebateImportedTransactionBean(
            unrelated ? uid : uid + FEE_UID_PART,
            date,
            rebateCurrency != null ? rebateCurrency : symbolBase,
            rebateCurrency != null ? rebateCurrency : symbolQuote,
            TransactionType.REBATE,
            rebate,
            rebateCurrency
        );
    }
}
