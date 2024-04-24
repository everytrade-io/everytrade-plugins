package io.everytrade.server.plugin.impl.everytrade.parser.exchange.coinbank;

import com.univocity.parsers.annotations.Parsed;
import com.univocity.parsers.common.DataValidationException;
import io.everytrade.server.model.Currency;
import io.everytrade.server.model.TransactionType;
import io.everytrade.server.plugin.api.parser.FeeRebateImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.ImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.TransactionCluster;
import io.everytrade.server.plugin.impl.everytrade.parser.exception.DataIgnoredException;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.ExchangeBean;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static io.everytrade.server.model.TransactionType.BUY;
import static io.everytrade.server.model.TransactionType.DEPOSIT;
import static io.everytrade.server.model.TransactionType.FEE;
import static io.everytrade.server.model.TransactionType.SELL;
import static io.everytrade.server.model.TransactionType.WITHDRAWAL;
import static java.util.Collections.emptyList;

@EqualsAndHashCode(callSuper = true)
@Data
public class CoinbankBeanV1 extends ExchangeBean implements Cloneable {

    // Buy/Sell
    private String currency;
    private Currency symbol;
    private Instant date;
    private CoinbankOperationTypeV1 operationType;
    private BigDecimal paid;
    private BigDecimal received;
    private String rate;
    private String fee;
    private Currency sourceCurrency;
    private Currency targetCurrency;

    // Deposit/Withdrawal
    private String status;
    private BigDecimal amount;
    private String address;
    private String account;
    private String tag;
    private String statusId;
    private CoinbankStatus statusEnum;

    // Fields for TransactionCluster
    private TransactionType transactionType;
    private Currency marketBase;
    private BigDecimal baseAmount;
    private Currency marketQuote;
    private BigDecimal quoteAmount;
    private List<CoinbankBeanV1> feeTransactions = new ArrayList<>();
    private boolean unsupportedRow;
    private String message;
    private Currency feeCurrency;

    @Parsed(field = "Měna")
    public void setCurrency(String currency) {
        this.currency = currency;
    }

    @Parsed(field = "Symbol")
    public void setSymbol(String symbol) {
        this.symbol = Currency.fromCode(symbol);
    }

    @Parsed(field = "Datum")
    public void setDate(String date) {
        this.date = Instant.parse(date + "Z");
    }

    @Parsed(field = {"Směr", "Operace"})
    public void setOperation(String operation) {
        this.operationType = CoinbankOperationTypeV1.getEnum(operation);
    }

    @Parsed(field = "Zaplaceno")
    public void setPaid(String paid) {
        this.paid = new BigDecimal(paid);
    }

    @Parsed(field = "Získáno")
    public void setReceived(String received) {
        this.received = new BigDecimal(received);
    }

    @Parsed(field = "Kurz")
    public void setRate(String rate) {
        this.rate = rate;
    }

    @Parsed(field = "Poplatek")
    public void setFee(String fee) {
        this.fee = fee;
    }

    @Parsed(field = "Zdrojová měna")
    public void setSourceCurrency(Currency sourceCurrency) {
        this.sourceCurrency = sourceCurrency;
    }

    @Parsed(field = "Cílová měna")
    public void setTargetCurrency(Currency targetCurrency) {
        this.targetCurrency = targetCurrency;
    }

    @Parsed(field = "Částka")
    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    @Parsed(field = "Stav")
    public void setStatus(String status) {
        this.status = status;
    }

    @Parsed(field = "Adresa")
    public void setAddress(String address) {
        this.address = address;
    }

    @Parsed(field = "Účet")
    public void setAccount(String account) {
        this.account = account;
    }

    @Parsed(field = "ID Stavu")
    public void setStatusId(String statusId) {
        this.statusId = statusId;
        if (statusId != null) {
            this.statusEnum = CoinbankStatus.getEnum(Integer.parseInt(statusId));
            if (statusEnum == null) {
                throw new DataValidationException("Wrong status id");
            }
        }
    }

    @Override
    public TransactionCluster toTransactionCluster() {
        final List<ImportedTransactionBean> related = new ArrayList<>();
        if (isUnsupportedRow()) {
            throw new DataIgnoredException(getMessage());
        }
        if (feeTransactions.size() > 0) {
            for (CoinbankBeanV1 fee : feeTransactions) {
                var feeTxs = new FeeRebateImportedTransactionBean(
                    FEE_UID_PART,
                    fee.getDate(),
                    fee.getFeeCurrency(),
                    fee.getFeeCurrency(),
                    FEE,
                    new BigDecimal(fee.getFee()),
                    fee.getFeeCurrency()
                );
                related.add(feeTxs);
            }
        }
        if (transactionType.equals(BUY)) {
            return new TransactionCluster(
                new ImportedTransactionBean(
                    null,
                    date,
                    marketBase,
                    marketQuote,
                    BUY,
                    baseAmount,
                    evalUnitPrice(quoteAmount, baseAmount),
                    null,
                    null
                ),
                related
            );
        }
        if (transactionType.equals(SELL)) {
            return new TransactionCluster(
                new ImportedTransactionBean(
                    null,
                    date,
                    marketBase,
                    marketQuote,
                    SELL,
                    baseAmount,
                    evalUnitPrice(quoteAmount, baseAmount),
                    null,
                    null
                ),
                related
            );
        }
        if (transactionType.equals(WITHDRAWAL)) {
            return new TransactionCluster(
                new ImportedTransactionBean(
                    null,
                    date,
                    marketBase,
                    marketBase,
                    WITHDRAWAL,
                    baseAmount,
                    null,
                    null,
                    address
                ),
                emptyList()
            );
        }
        if (transactionType.equals(DEPOSIT)) {
            return new TransactionCluster(
                new ImportedTransactionBean(
                    null,
                    date,
                    marketBase,
                    marketBase,
                    DEPOSIT,
                    baseAmount,
                    null,
                    null,
                    address
                ),
                emptyList()
            );
        }
        return null;
    }

    @Override
    public Object clone() throws CloneNotSupportedException {
        return super.clone();
    }
}
