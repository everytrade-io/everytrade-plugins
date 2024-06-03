package io.everytrade.server.plugin.impl.everytrade.parser.exchange.okx;

import com.univocity.parsers.annotations.Format;
import com.univocity.parsers.annotations.Parsed;
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
import java.util.Date;
import java.util.List;

import static io.everytrade.server.model.TransactionType.BUY;
import static io.everytrade.server.model.TransactionType.FEE;
import static io.everytrade.server.model.TransactionType.SELL;

@EqualsAndHashCode(callSuper = true)
@Data
public class OkxBeanV2 extends ExchangeBean implements Cloneable{
    private String id;
    private String orderId;
    private Instant time;
    private String tradeType;
    private String symbol;
    private String action;
    private BigDecimal amount;
    private String tradingUnit;
    private BigDecimal filledPrice;
    private Currency filledPriceUnit;
    private BigDecimal pnl;
    private BigDecimal fee;
    private Currency feeUnit;
    private BigDecimal positionChange;
    private BigDecimal positionBalance;
    private Currency positionUnit;
    private BigDecimal balanceChange;
    private BigDecimal balance;
    private Currency balanceUnit;

    private TransactionType transactionType;
    private Currency baseCurrency;
    private BigDecimal baseAmount;
    private Currency quoteCurrency;
    private BigDecimal quoteAmount;
    private BigDecimal feeAmount;
    private List<OkxBeanV2> feeTransactions = new ArrayList<>();
    private boolean unsupportedRow;
    private String message;
    private Currency feeCurrency;

    @Parsed(field = "id")
    public void setId(String id) {
        this.id = id;
    }

    @Parsed(field = "Order id")
    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    @Parsed(field = "Time")
    @Format(formats = {"yyyy-MM-dd HH:mm:ss"}, options = {"locale=EN", "timezone=UTC"})
    public void setTime(Date time) {
        this.time = time.toInstant();
    }

    @Parsed(field = "Trade Type")
    public void setTradeType(String tradeType) {
        this.tradeType = tradeType;
    }

    @Parsed(field = "Symbol")
    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    @Parsed(field = "Action")
    public void setAction(String action) {
        this.action = action;
    }

    @Parsed(field = "Amount")
    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    @Parsed(field = "Trading Unit")
    public void setTradingUnit(String tradingUnit) {
        this.tradingUnit = tradingUnit;
    }

    @Parsed(field = "Filled Price")
    public void setFilledPrice(BigDecimal filledPrice) {
        this.filledPrice = filledPrice;
    }

    @Parsed(field = "Filled Price Unit")
    public void setFilledPriceUnit(Currency filledPriceUnit) {
        this.filledPriceUnit = filledPriceUnit;
    }

    @Parsed(field = "PnL")
    public void setPnl(BigDecimal pnl) {
        this.pnl = pnl;
    }

    @Parsed(field = "Fee")
    public void setFee(BigDecimal fee) {
        this.fee = fee;
    }

    @Parsed(field = "Fee Unit")
    public void setFeeUnit(Currency feeUnit) {
        this.feeUnit = feeUnit;
    }

    @Parsed(field = "Position Change")
    public void setPositionChange(BigDecimal positionChange) {
        this.positionChange = positionChange;
    }

    @Parsed(field = "Position Balance")
    public void setPositionBalance(BigDecimal positionBalance) {
        this.positionBalance = positionBalance;
    }

    @Parsed(field = "Position Unit")
    public void setPositionUnit(Currency positionUnit) {
        this.positionUnit = positionUnit;
    }

    @Parsed(field = "Balance Change")
    public void setBalanceChange(BigDecimal balanceChange) {
        this.balanceChange = balanceChange;
    }

    @Parsed(field = "Balance")
    public void setBalance(BigDecimal balance) {
        this.balance = balance;
    }

    @Parsed(field = "Balance Unit")
    public void setBalanceUnit(Currency balanceUnit) {
        this.balanceUnit = balanceUnit;
    }



    @Override
    public TransactionCluster toTransactionCluster() {
        List<ImportedTransactionBean> related = new ArrayList<>();
        boolean isFailedFee = false;
        String failedFeeMessage = "";

        if (isUnsupportedRow()) {
            throw new DataIgnoredException(getMessage());
        }

        if (feeTransactions.size() > 0) {
            try {
                for (OkxBeanV2 fee : feeTransactions) {
                    var feeTxs = new FeeRebateImportedTransactionBean(
                        FEE_UID_PART,
                        fee.getTime(),
                        fee.getFeeCurrency(),
                        fee.getFeeCurrency(),
                        FEE,
                        fee.getFeeAmount(),
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
                    time,
                    baseCurrency,
                    quoteCurrency,
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
                    time,
                    baseCurrency,
                    quoteCurrency,
                    SELL,
                    baseAmount,
                    evalUnitPrice(quoteAmount, baseAmount),
                    null,
                    null
                ),
                related
            );
        }
        return null;
    }

    @Override
    public Object clone() throws CloneNotSupportedException {
        OkxBeanV2 cloned = (OkxBeanV2) super.clone();
        cloned.feeTransactions = new ArrayList<>();
        return cloned;
    }
}
