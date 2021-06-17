package io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean;

import com.univocity.parsers.annotations.Headers;
import com.univocity.parsers.annotations.Parsed;
import com.univocity.parsers.annotations.Replace;
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
import java.util.List;

@Headers(
    sequence = {
        "Timestamp", "Transaction Type", "Asset", "Quantity Transacted", "Subtotal", "Fees", "Notes"
    },
    extract = true
)
public class CoinbaseBeanV1 extends ExchangeBean {
    private Instant timeStamp;
    private TransactionType transactionType;
    private Currency asset;
    private BigDecimal quantityTransacted;
    private BigDecimal subtotal;
    private BigDecimal fees;
    private String notes;

    @Parsed(field = "Timestamp")
    public void setTimeStamp(String value) {
        timeStamp = Instant.parse(value);
    }

    @Parsed(field = "Transaction Type")
    public void setTransactionType(String value) {
        transactionType = detectTransactionType(value);
    }

    @Parsed(field = "Asset")
    public void setAsset(String value) {
        asset = Currency.fromCode(value);
    }

    @Parsed(field = "Quantity Transacted")
    @Replace(expression = IGNORED_CHARS_IN_NUMBER, replacement = "")
    public void setQuantityTransacted(BigDecimal value) {
        quantityTransacted = value;
    }

    @Parsed(field = "Subtotal")
    @Replace(expression = IGNORED_CHARS_IN_NUMBER, replacement = "")
    public void setSubtotal(BigDecimal value) {
        subtotal = value;
    }

    @Parsed(field = "Fees")
    @Replace(expression = IGNORED_CHARS_IN_NUMBER, replacement = "")
    public void setFees(BigDecimal value) {
        fees = value;
    }

    @Parsed(field = "Notes")
    public void setNotes(String value) {
        notes = value;
    }

    @Override
    public TransactionCluster toTransactionCluster() {
        final Currency quoteCurrency = detectQuote(notes);

        validateCurrencyPair(asset, quoteCurrency);

        List<ImportedTransactionBean> related;
        if (ParserUtils.equalsToZero(fees)) {
            related = Collections.emptyList();
        } else {
            related = List.of(
                new FeeRebateImportedTransactionBean(
                    null,
                    timeStamp,
                    asset,
                    quoteCurrency,
                    TransactionType.FEE,
                    fees.setScale(ParserUtils.DECIMAL_DIGITS, RoundingMode.HALF_UP),
                    quoteCurrency
                )
            );
        }

        return new TransactionCluster(
            new BuySellImportedTransactionBean(
                null,
                timeStamp,
                asset,
                quoteCurrency,
                transactionType,
                quantityTransacted.abs().setScale(ParserUtils.DECIMAL_DIGITS, RoundingMode.HALF_UP),
                evalUnitPrice(subtotal, quantityTransacted)
            ),
            related
        );
    }

    private Currency detectQuote(String note) {
        if (note.endsWith("EUR")) {
            return Currency.EUR;
        } else if (note.endsWith("USD")) {
            return Currency.USD;
        } else {
            throw new DataValidationException("Unsupported quote currency in 'Notes': " + note);
        }
    }
}