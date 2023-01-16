package io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean;

import com.univocity.parsers.annotations.Format;
import com.univocity.parsers.annotations.Headers;
import com.univocity.parsers.annotations.Parsed;
import io.everytrade.server.model.Currency;
import io.everytrade.server.model.TransactionType;
import io.everytrade.server.plugin.api.parser.ImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.TransactionCluster;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.ExchangeBean;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Date;

import static io.everytrade.server.model.TransactionType.EARNING;
import static java.util.Collections.emptyList;

/**
 * The bean is created only for EARNING transaction type.
 * Others types are not required
 */
@Headers(sequence = {"Date(UTC)", "Type", "Product Name", "Coin", "Amount"}, extract = true)
public class BinanceBeanV5 extends ExchangeBean {
    private Instant date;
    private String productName;
    private Currency coin;
    private BigDecimal amount;
    private TransactionType type = EARNING;

    @Parsed(field = "Date(UTC)")
    @Format(formats = {"yyyy-MM-dd HH:mm:ss"}, options = {"locale=EN", "timezone=UTC"})
    public void setDate(Date value) {
        date = value.toInstant();
    }

    @Parsed(field = "Product Name")
    public void setProductName(String value) {
        productName = value;
    }

    @Parsed(field = "Coin")
    public void setCoin(String value) {
        coin = Currency.fromCode(value);
    }

    @Parsed(field = "Amount")
    public void setCoin(BigDecimal value) {
        amount = value.abs();
    }

    @Override
    public TransactionCluster toTransactionCluster() {
        var tx = new ImportedTransactionBean(
            null,
            date,
            coin,
            coin,
            type,
            amount,
            null
        );
        return new TransactionCluster(tx, emptyList());
    }
}
