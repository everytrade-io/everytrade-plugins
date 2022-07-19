package io.everytrade.server.plugin.impl.everytrade.parser.exchange.coibasePro.v2;

import com.univocity.parsers.annotations.Format;
import com.univocity.parsers.annotations.Headers;
import com.univocity.parsers.annotations.Parsed;
import com.univocity.parsers.annotations.Replace;
import com.univocity.parsers.common.DataValidationException;
import io.everytrade.server.model.Currency;
import io.everytrade.server.model.TransactionType;
import io.everytrade.server.plugin.api.parser.BuySellImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.DepositWithdrawalImportedTransaction;
import io.everytrade.server.plugin.api.parser.FeeRebateImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.ImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.TransactionCluster;
import io.everytrade.server.plugin.impl.everytrade.parser.ParserUtils;
import io.everytrade.server.plugin.impl.everytrade.parser.exception.DataIgnoredException;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.ExchangeBean;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import static java.math.BigDecimal.ZERO;

@EqualsAndHashCode(callSuper = true)
@Headers(
    sequence = {
        "portfolio", "type", "time", "amount", "balance",
        "amount/balance unit", "transfer id", "trade id", "order id"
    },
    extract = true
)
@Data
public class CoinbaseProBeanV2 extends ExchangeBean {

    private static final String ILLEGAL_ZERO_VALUE_OF_AMOUNT = "illegal zero value";

    private String portfolio;
    private String type;
    private Instant time;
    private String amount;
    private String balance;
    private String amountBalanceUnit;
    private Currency currency;
    private String transferId;
    private String tradeId;
    private String orderId;

    public List<Integer> usedIds = new ArrayList<>();
    String message;
    boolean unsupportedRow;
    boolean failedDataRow;
    String groupRefId;
    private List<CoinbaseProBeanV2> fees = new ArrayList<>();

    private Currency base;
    private Currency quote;
    private TransactionType transactionType;
    private BigDecimal baseAmount;
    private BigDecimal quoteAmount;
    private BigDecimal fee;
    private BigDecimal price;

    @Parsed(field = "portfolio")
    public void setPortfolio(String value) {
        portfolio = value;
    }

    @Parsed(field = "type")
    public void setType(String value) {
        if (!CoinbaseProSupportedTypes.SUPPORTED_TYPES.contains(value)) {
            setUnsupportedRow(true);
        }
        if ("deposit".equals(value)) {
            transactionType = TransactionType.DEPOSIT;
        } else if ("withdrawal".equals(value)) {
            transactionType = TransactionType.WITHDRAWAL;
        }
        type = value;
    }

    @Parsed(field = "time")
    @Format(formats = {"yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"}, options = {"locale=EN", "timezone=UTC"})
    public void setTime(Date value) {
        time = value.toInstant();
    }

    public void setTime(Instant value) {
        time = value;
    }

    @Parsed(field = "amount")
    @Replace(expression = "[,]", replacement = ".")
    public void setAmount(String value) {
        amount = value.replace(" ", "");
    }

    public void setPrice(String amount) {
        try {
            price = new BigDecimal(amount);
        } catch (NumberFormatException e) {
            throw new DataValidationException(String.format("Invalid price value %s", amount));
        }
    }

    @Parsed(field = "balance")
    public void setBalance(String value) {
        balance = value;
    }

    @Parsed(field = "amount/balance unit")
    public void setAmountBalanceUnit(String value) {
        amountBalanceUnit = value;
    }

    public void setCurrency(String amountBalanceUnit) {
        try {
            currency = Currency.fromCode(amountBalanceUnit);
        } catch (IllegalArgumentException e) {
            throw new DataValidationException(String.format("Unsupported currency: %s; ", amountBalanceUnit));
        }
    }

    public void setCurrency(Currency value) {
        currency = value;
    }

    @Parsed(field = "transfer id")
    public void setTransferId(String value) {
        transferId = value;
    }

    @Parsed(field = "trade id", defaultNullRead = "")
    public void setTradeId(String value) {
        tradeId = value;
    }

    @Parsed(field = "orderId")
    public void setOrderId(String value) {
        orderId = value;
    }

    public void setMessage(String message) {
        String sub = (this.message == null) ? String.format("Line: %s; ", this.getRowId()) : (this.message + "; ");
        this.message = sub + message;
    }

    @Override
    public TransactionCluster toTransactionCluster() {
        if (unsupportedRow) {
            throw new DataIgnoredException(message);
        }
        if (failedDataRow) {
            throw new DataValidationException(message);
        }
        if (getCurrency() == null) {
            setCurrency(getAmountBalanceUnit());
        }
        if (getPrice() == null) {
            setPrice(amount);
        }

        TransactionCluster transactionCluster;

        List<ImportedTransactionBean> related = Collections.emptyList();
        boolean ignoredFee;
        boolean failedFeeTransaction;

        if (transactionType.isBuyOrSell()) {
            transactionCluster = new TransactionCluster(
                new BuySellImportedTransactionBean(
                    tradeId + " " + orderId,             //uuid
                    time,           //executed
                    base,         //base
                    quote,        //quote
                    transactionType,                //action
                    baseAmount.abs().setScale(ParserUtils.DECIMAL_DIGITS, RoundingMode.HALF_UP),         //base quantity
                    evalUnitPrice(quoteAmount.abs(), baseAmount.abs())   //unit price
                ),
                related
            );
        } else if (transactionType.isDepositOrWithdrawal()) {
            transactionCluster = new TransactionCluster(
                new DepositWithdrawalImportedTransaction(
                    transferId,
                    time,
                    currency,
                    currency,
                    transactionType,
                    price.abs(),
                    null
                ),
                related
            );
        } else {
            throw new DataValidationException(String.format("Unsupported type %s; ", transactionType));
        }

        if (fees.size() > 0) {
            for (CoinbaseProBeanV2 fee : fees) {
                try {
                    fee.setPrice(fee.getAmount());
                } catch (DataValidationException e) {
                    transactionCluster.setFailedFee(fees.size(), String.format("Fee %s ;", e.getMessage()));
                }
                if (ZERO.compareTo(fee.getPrice()) == 0) {
                    transactionCluster.setIgnoredFee(fees.size(), "Fee amount is 0 " + (currency != null ? currency.code() : ""));
                }
                try {
                    var rebate = new FeeRebateImportedTransactionBean(
                        fee.getTradeId() + " " + fee.getOrderId(),
                        fee.getTime(),
                        fee.getCurrency(),
                        currency,
                        TransactionType.FEE,
                        fee.getPrice().setScale(ParserUtils.DECIMAL_DIGITS, RoundingMode.HALF_UP).abs(),
                        fee.getCurrency()
                    );
                    related.add(rebate);
                } catch (DataValidationException e) {
                    transactionCluster.setFailedFee(fees.size(), String.format("Fee %s ;", e.getMessage()));
                }
            }
        }
        return transactionCluster;
    }
}
