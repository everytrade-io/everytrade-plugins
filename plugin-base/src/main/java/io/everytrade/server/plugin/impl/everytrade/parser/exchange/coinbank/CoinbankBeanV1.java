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
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.coinbank.CoinbankCurrencySwitcher.SWITCHER;
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
    private BigDecimal rate;
    private BigDecimal fee;
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
        if (SWITCHER.containsKey(symbol)) {
            this.symbol = SWITCHER.get(symbol);
        } else {
            this.symbol = Currency.fromCode(symbol);
        }
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
    public void setPaid(BigDecimal paid) {
        this.paid = paid;
    }

    @Parsed(field = "Získáno")
    public void setReceived(BigDecimal received) {
        this.received = received;
    }

    @Parsed(field = "Kurz")
    public void setRate(BigDecimal rate) {
        this.rate = rate;
    }

    @Parsed(field = "Poplatek")
    public void setFee(BigDecimal fee) {
        this.fee = fee;
    }

    @Parsed(field = "Zdrojová měna")
    public void setSourceCurrency(String sourceCurrency) {
        if (SWITCHER.containsKey(sourceCurrency)) {
            this.sourceCurrency = SWITCHER.get(sourceCurrency);
        } else {
            this.sourceCurrency = Currency.fromCode(sourceCurrency);
        }
    }

    @Parsed(field = "Cílová měna")
    public void setTargetCurrency(String targetCurrency) {
        if (SWITCHER.containsKey(targetCurrency)) {
            this.targetCurrency = SWITCHER.get(targetCurrency);
        } else {
            this.targetCurrency = Currency.fromCode(targetCurrency);
        }
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
                throw new DataValidationException("Unsupported status id");
            }
        }
    }

    @Override
    public TransactionCluster toTransactionCluster() {

        List<ImportedTransactionBean> related = new ArrayList<>();
        boolean isFailedFee = false;
        String failedFeeMessage = "";

        if (isUnsupportedRow()) {
            throw new DataIgnoredException(getMessage());
        }

        if (feeTransactions.size() > 0){
            try {
                for (CoinbankBeanV1 fee : feeTransactions) {
                        var feeTxs = new FeeRebateImportedTransactionBean(
                            FEE_UID_PART,
                            fee.getDate(),
                            fee.getFeeCurrency(),
                            fee.getFeeCurrency(),
                            FEE,
                            fee.getFee(),
                            fee.getFeeCurrency()
                        );
                        related.add(feeTxs);
                }
            } catch (NullPointerException e) {
                isFailedFee = true;
                failedFeeMessage = "unsupported fee currency";
            } catch (Exception e) {
                isFailedFee = true;
                failedFeeMessage = e.getMessage();
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
            var cluster = new TransactionCluster(
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
            if (isFailedFee) {
                cluster.setFailedFee(1, String.format("Fee transaction failed - %s", failedFeeMessage));
            }
            return cluster;
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
        CoinbankBeanV1 cloned = (CoinbankBeanV1) super.clone();
        cloned.feeTransactions = new ArrayList<>();
        return cloned;
    }
}
