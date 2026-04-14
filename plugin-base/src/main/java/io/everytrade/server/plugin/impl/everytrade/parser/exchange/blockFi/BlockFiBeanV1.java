package io.everytrade.server.plugin.impl.everytrade.parser.exchange.blockFi;

import com.univocity.parsers.annotations.Format;
import com.univocity.parsers.annotations.Parsed;
import com.univocity.parsers.common.DataValidationException;
import io.everytrade.server.model.Currency;
import io.everytrade.server.model.TransactionType;
import io.everytrade.server.plugin.api.parser.ImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.TransactionCluster;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.ExchangeBean;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Date;

import static java.util.Collections.emptyList;

@EqualsAndHashCode(callSuper = true)
@Data
public class BlockFiBeanV1 extends ExchangeBean {
    private String cryptocurrency;
    private BigDecimal amount;
    private String txType;
    private Instant confirmedAt;

    private BlockFiOperationTypeV1 operationType;
    private TransactionType transactionType;
    private Currency baseCurrency;
    private BigDecimal baseAmount;
    private Currency quoteCurrency;
    private BigDecimal quoteAmount;
    private boolean unsupportedRow;
    private String message;

    @Parsed(field = "Cryptocurrency")
    public void setCryptocurrency(String cryptocurrency) {
        this.cryptocurrency = cryptocurrency;
    }

    @Parsed(field = "Amount")
    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    @Parsed(field = "Transaction Type")
    public void setTxType(String txType) {
        this.txType = txType;
        this.operationType = BlockFiOperationTypeV1.getEnum(txType);
    }

    @Parsed(field = "Confirmed At")
    @Format(formats = {"yyyy-MM-dd HH:mm:ss"}, options = {"locale=EN", "timezone=UTC"})
    public void setConfirmedAt(Date confirmedAt) {
        this.confirmedAt = confirmedAt.toInstant();
    }

    public void setConfirmedAt(Instant confirmedAt) {
        this.confirmedAt = confirmedAt;
    }

    @Override
    public TransactionCluster toTransactionCluster() {
        if (unsupportedRow) {
            throw new DataValidationException(message);
        }
        switch (transactionType) {
            case BUY, SELL -> {
                return new TransactionCluster(
                    new ImportedTransactionBean(
                        null,
                        confirmedAt,
                        baseCurrency,
                        quoteCurrency,
                        transactionType,
                        baseAmount,
                        evalUnitPrice(quoteAmount.abs(), baseAmount),
                        null,
                        null
                    ),
                    emptyList()
                );
            }
            case WITHDRAWAL, DEPOSIT, EARNING, REWARD -> {
                return new TransactionCluster(
                    new ImportedTransactionBean(
                        null,
                        confirmedAt,
                        baseCurrency,
                        baseCurrency,
                        transactionType,
                        baseAmount,
                        null,
                        null,
                        null
                    ),
                    emptyList()
                );
            }
        }
        return null;
    }
}
