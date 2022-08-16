package io.everytrade.server.parser.exchange;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.univocity.parsers.common.DataValidationException;
import io.everytrade.server.model.Currency;
import io.everytrade.server.model.CurrencyPair;
import io.everytrade.server.model.TransactionType;
import io.everytrade.server.plugin.api.parser.FeeRebateImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.ImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.TransactionCluster;
import io.everytrade.server.plugin.impl.everytrade.parser.exception.DataIgnoredException;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import static io.everytrade.server.plugin.impl.everytrade.parser.ParserUtils.equalsToZero;
import static io.everytrade.server.plugin.impl.everytrade.parser.ParserUtils.nullOrZero;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.ExchangeBean.FEE_UID_PART;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.ExchangeBean.UNSUPPORTED_TRANSACTION_TYPE;
import static io.everytrade.server.plugin.impl.generalbytes.GbPlugin.parseGbCurrency;

@JsonFormat(shape = JsonFormat.Shape.ARRAY)
@JsonPropertyOrder(
    {
        "uid",
        "timestamp",
        "base",
        "quote",
        "action",
        "quantity",
        "volume",
        "expense",
        "expenseCurrency",
        "status"
    }
)
@Data
@NoArgsConstructor
public class GbApiTransactionBean {

    private static final String WITHDRAWAL_ACTION = "WITHDRAW";

    private String uid;
    private Instant timestamp;
    private String base;
    private String quote;
    private String action;
    private BigDecimal quantity;
    private BigDecimal volume;
    private BigDecimal expense;
    private String expenseCurrency;
    private String status;

    public void setTimestamp(Date timestamp) {
        this.timestamp = timestamp.toInstant();
    }

    public TransactionCluster toTransactionCluster() {
        final Currency baseCurrency = parseGbCurrency(base);
        final Currency quoteCurrency = parseGbCurrency(quote);
        try {
            new CurrencyPair(baseCurrency, quoteCurrency);
        } catch (CurrencyPair.FiatCryptoCombinationException e) {
            throw new DataValidationException(e.getMessage());
        }
        final boolean isIncorrectFee = !(base.equals(expenseCurrency) || quote.equals(expenseCurrency));
        List<ImportedTransactionBean> related;
        if (equalsToZero(expense) || isIncorrectFee) {
            related = Collections.emptyList();
        } else {
            related = List.of(new FeeRebateImportedTransactionBean(
                    uid + FEE_UID_PART,
                    timestamp,
                    baseCurrency,
                    quoteCurrency,
                    TransactionType.FEE,
                    expense,
                    parseGbCurrency(expenseCurrency),
                    getRemoteUid()
                )
            );
        }

        TransactionCluster cluster = new TransactionCluster(
            new ImportedTransactionBean(
                uid,
                timestamp,
                baseCurrency,
                quoteCurrency,
                actionToTransactionType(),
                quantity,
                volume.divide(quantity, 10, RoundingMode.HALF_UP),
                getRemoteUid(),
                null
            ),
            related
        );
        if (isIncorrectFee) {
            cluster.setFailedFee(1, "Fee " + expenseCurrency + " currency is neither base or quote");
        }
        if (nullOrZero(expense)) {
//            cluster.setIgnoredFee(1, "Fee amount is 0 " + expenseCurrency);
        }
        return cluster;
    }

    public boolean isImportable() {
        return (status == null || isImportable(status)) && isActionImportable();
    }

    public boolean isActionImportable() {
        return action != null && !WITHDRAWAL_ACTION.equals(action);
    }

    public static boolean isImportable(String status) {
        return status.startsWith("COMPLETED") ||
            status.contains("PAYMENT ARRIVED") ||
            status.contains("ERROR (EXCHANGE PURCHASE)");
    }

    public boolean isIgnored() {
        return !isActionImportable();
    }

    public String getIgnoreReason() {
        if (WITHDRAWAL_ACTION.equals(action)) {
            return WITHDRAWAL_ACTION + " TRANSATION";
        }
        return null;
    }

    @Override
    public String toString() {
        return "GbApiTransactionBean{" +
            "uid='" + uid + '\'' +
            ", timestamp=" + timestamp +
            ", base='" + base + '\'' +
            ", quote='" + quote + '\'' +
            ", action='" + action + '\'' +
            ", quantity=" + quantity +
            ", volume=" + volume +
            ", expense=" + expense +
            ", expenseCurrency=" + expenseCurrency +
            ", status=" + status +
            '}';
    }

    private String getRemoteUid() {
        if (uid == null) {
            return null;
        }
        final String[] split = uid.split("-");
        if (split.length != 2) {
            return null;
        }
        return split[1];
    }

    private TransactionType actionToTransactionType() {
        if ("SELL".equals(action)) {
            return TransactionType.BUY;
        } else if("BUY".equals(action)) {
            return TransactionType.SELL;
        } else {
            throw new DataIgnoredException(UNSUPPORTED_TRANSACTION_TYPE.concat(action));
        }
    }
}
