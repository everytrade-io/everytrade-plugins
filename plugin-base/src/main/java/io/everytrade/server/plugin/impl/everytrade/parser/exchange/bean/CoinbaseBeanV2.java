package io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean;

import com.univocity.parsers.annotations.Headers;
import com.univocity.parsers.annotations.Parsed;
import com.univocity.parsers.annotations.Replace;
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
        "Timestamp", "Transaction Type", "Asset", "Quantity Transacted", "USD Subtotal", "USD Fees"
    },
    extract = true
)
public class CoinbaseBeanV2 extends ExchangeBean {
    private Instant timeStamp;
    private TransactionType transactionType;
    private Currency asset;
    private BigDecimal quantityTransacted;
    private BigDecimal usdSubtotal;
    private BigDecimal usdFees;

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
        asset = Currency.valueOf(value);
    }

    @Parsed(field = "Quantity Transacted")
    @Replace(expression = IGNORED_CHARS_IN_NUMBER, replacement = "")
    public void setQuantityTransacted(BigDecimal value) {
        quantityTransacted = value;
    }

    @Parsed(field = "USD Subtotal")
    @Replace(expression = IGNORED_CHARS_IN_NUMBER, replacement = "")
    public void setUsdSubtotal(BigDecimal value) {
        usdSubtotal = value;
    }

    @Parsed(field = "USD Fees")
    @Replace(expression = IGNORED_CHARS_IN_NUMBER, replacement = "")
    public void setUsdFees(BigDecimal value) {
        usdFees = value;
    }

    @Override
    public TransactionCluster toTransactionCluster() {
        final Currency quoteCurrency = Currency.USD;

        validateCurrencyPair(asset, quoteCurrency);

        List<ImportedTransactionBean> related;
        if (ParserUtils.equalsToZero(usdFees)) {
            related = Collections.emptyList();
        } else {
            related = List.of(
                new FeeRebateImportedTransactionBean(
                    null,
                    timeStamp,
                    asset,
                    quoteCurrency,
                    TransactionType.FEE,
                    usdFees.setScale(ParserUtils.DECIMAL_DIGITS, RoundingMode.HALF_UP),
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
                evalUnitPrice(usdSubtotal, quantityTransacted)
            ),
            related
        );
    }
}
