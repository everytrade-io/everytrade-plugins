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
                throw new DataIgnoredException("Ignored operation type: " + type);
            }
        }

        if (TransactionType.BUY.equals(getTransactionType())) {
            return new TransactionCluster(
                    new ImportedTransactionBean(
                            orderId,
                            date,
                            marketBase,
                            marketQuote,
                            TransactionType.BUY,
                            baseAmount,
                            baseAmount.divide(quoteAmount.abs(), DECIMAL_DIGITS, RoundingMode.HALF_UP)
                    ),
                    related
            );
        }
        if (TransactionType.SELL.equals(getTransactionType())) {

            return new TransactionCluster(
                    new ImportedTransactionBean(
                            orderId,
                            date,
                            marketBase, //base = trade payment
                            marketQuote, //quote = trade fill
                            TransactionType.SELL,
                            baseAmount,
                            baseAmount.abs().divide(quoteAmount, DECIMAL_DIGITS, RoundingMode.HALF_UP)
                    ),
                    related
            );
        }

        if (TransactionType.DEPOSIT.equals(getTransactionType())) {
            return new TransactionCluster(
                    ImportedTransactionBean.createDepositWithdrawal(
                            orderId,
                            date,
                            marketBase,
                            marketQuote,
                            TransactionType.DEPOSIT,
                            baseAmount,
                            null
                    ),
                    related
            );
        }
        if (TransactionType.WITHDRAWAL.equals(getTransactionType())) {
            return new TransactionCluster(
                    ImportedTransactionBean.createDepositWithdrawal(
                            orderId,
                            date,
                            marketBase,
                            marketQuote,
                            TransactionType.WITHDRAWAL,
                            baseAmount,
                            null
                    ),
                    related
            );
        }
        if (TransactionType.STAKE.equals(getTransactionType())) {
            return new TransactionCluster(
                    new ImportedTransactionBean(
                            orderId,
                            date,
                            marketBase, //potrebuju zde trade fill
                            marketBase, //a tady trade payment
                            TransactionType.STAKE,
                            baseAmount,
                            null
                    ),
                    related
            );
        }
        if (TransactionType.STAKING_REWARD.equals(getTransactionType())) {
            return new TransactionCluster(
                    new ImportedTransactionBean(
                            orderId,
                            date,
                            marketBase, //potrebuju zde trade fill
                            marketBase, //a tady trade payment
                            TransactionType.STAKING_REWARD,
                            baseAmount,
                            null
                    ),
                    related
            );
        }
        if (TransactionType.UNSTAKE.equals(getTransactionType())) {
            return new TransactionCluster(
                    new ImportedTransactionBean(
                            orderId,
                            date,
                            marketBase, //potrebuju zde trade fill
                            marketBase, //a tady trade payment
                            TransactionType.UNSTAKE,
                            baseAmount,
                            null
                    ),
                    related
            );
        }
        return null;
    }
}
