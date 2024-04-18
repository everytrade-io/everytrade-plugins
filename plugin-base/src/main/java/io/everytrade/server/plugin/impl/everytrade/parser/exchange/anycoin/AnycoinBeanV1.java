package io.everytrade.server.plugin.impl.everytrade.parser.exchange.anycoin;

import com.univocity.parsers.annotations.Headers;
import com.univocity.parsers.annotations.Parsed;
import io.everytrade.server.model.Currency;
import io.everytrade.server.model.TransactionType;
import io.everytrade.server.plugin.api.parser.ImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.TransactionCluster;
import io.everytrade.server.plugin.impl.everytrade.parser.exception.DataIgnoredException;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.ExchangeBean;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static io.everytrade.server.model.TransactionType.BUY;
import static io.everytrade.server.model.TransactionType.DEPOSIT;
import static io.everytrade.server.model.TransactionType.SELL;
import static io.everytrade.server.model.TransactionType.STAKE;
import static io.everytrade.server.model.TransactionType.STAKING_REWARD;
import static io.everytrade.server.model.TransactionType.UNSTAKE;
import static io.everytrade.server.model.TransactionType.WITHDRAWAL;
import static io.everytrade.server.plugin.impl.everytrade.parser.ParserUtils.DECIMAL_DIGITS;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.anycoin.AnycoinCurrencySwitcher.SWITCHER;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.anycoin.AnycoinSupportedOperations.UNSUPPORTED_OPERATION_TYPES;

@EqualsAndHashCode(callSuper = true)
@Data
@Headers(sequence = {"Date", "Type", "Amount", "Currency", "Order ID"}, extract = true)
public class AnycoinBeanV1 extends ExchangeBean {

    private Instant date;
    private String type;
    private BigDecimal amount;
    private Currency coin;
    private String orderId;

    Currency marketBase;
    BigDecimal baseAmount;
    Currency marketQuote;
    BigDecimal quoteAmount;
    TransactionType transactionType;
    AnycoinOperationTypeV1 operationType;
    String currencyEndsWithS;
    boolean unsupportedRow;
    String message;


    public String getOrderId() {
        return orderId;
    }

    @Parsed(field = "Date")
    public void setDate(String date) {
        this.date = Instant.parse(date);
    }

    @Parsed(field = "Type")
    public void setType(String type) {
        if (type == null) {
           return;
        }
        this.operationType = AnycoinOperationTypeV1.getEnum(type.toUpperCase());
        this.type = type;
    }

    @Parsed(field = "Amount")
    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    @Parsed(field = "Currency")
    public void setCoin(String coin) {

        if (coin.endsWith(".S")) {
            currencyEndsWithS = coin;
            coin = coin.substring(0, coin.length() - 2);
        }
        if (SWITCHER.containsKey(coin)) {
            this.coin = SWITCHER.get(coin);
        } else {
            this.coin = Currency.fromCode(coin);
        }
    }

    @Parsed(field = "Order ID")
    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    @Override
    public TransactionCluster toTransactionCluster() {
        List<ImportedTransactionBean> related = new ArrayList<>();

        if (operationType != null){
            if (UNSUPPORTED_OPERATION_TYPES.contains(operationType.code)) {
                this.setUnsupportedRow(true);
                this.setMessage("Unsupported type of operation " + type);
                throw new DataIgnoredException("Ignored operation type: " + type);
            }
        }

        if (BUY.equals(getTransactionType())) {
            return new TransactionCluster(
                    new ImportedTransactionBean(
                            orderId,
                            date,
                            marketBase,
                            marketQuote,
                            BUY,
                            baseAmount,
                            quoteAmount.abs().divide(baseAmount, DECIMAL_DIGITS, RoundingMode.HALF_UP)
                    ),
                    related
            );
        }
        if (SELL.equals(getTransactionType())) {

            return new TransactionCluster(
                    new ImportedTransactionBean(
                            orderId,
                            date,
                            marketBase, //base = trade payment
                            marketQuote, //quote = trade fill
                            SELL,
                            quoteAmount,
                            quoteAmount.divide(baseAmount.abs(), DECIMAL_DIGITS, RoundingMode.HALF_UP)
                    ),
                    related
            );
        }

        if (DEPOSIT.equals(getTransactionType())) {
            return new TransactionCluster(
                    ImportedTransactionBean.createDepositWithdrawal(
                            orderId,
                            date,
                            marketBase,
                            marketQuote,
                            DEPOSIT,
                            baseAmount,
                            null
                    ),
                    related
            );
        }
        if (WITHDRAWAL.equals(getTransactionType())) {
            return new TransactionCluster(
                    ImportedTransactionBean.createDepositWithdrawal(
                            orderId,
                            date,
                            marketBase,
                            marketQuote,
                            WITHDRAWAL,
                            baseAmount.abs(),
                            null
                    ),
                    related
            );
        }
        if (STAKE.equals(getTransactionType())) {
            return new TransactionCluster(
                    new ImportedTransactionBean(
                            orderId,
                            date,
                            marketBase, //potrebuju zde trade fill
                            marketBase, //a tady trade payment
                            STAKE,
                            baseAmount,
                            null
                    ),
                    related
            );
        }
        if (STAKING_REWARD.equals(getTransactionType())) {
            return new TransactionCluster(
                    new ImportedTransactionBean(
                            orderId,
                            date,
                            marketBase, //potrebuju zde trade fill
                            marketBase, //a tady trade payment
                            STAKING_REWARD,
                            baseAmount,
                            null
                    ),
                    related
            );
        }
        if (UNSTAKE.equals(getTransactionType())) {
            return new TransactionCluster(
                    new ImportedTransactionBean(
                            orderId,
                            date,
                            marketBase, //potrebuju zde trade fill
                            marketBase, //a tady trade payment
                            UNSTAKE,
                            baseAmount,
                            null
                    ),
                    related
            );
        }
        return null;
    }
}
