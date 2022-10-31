package io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean;

import com.univocity.parsers.common.DataValidationException;
import io.everytrade.server.model.Currency;
import io.everytrade.server.model.TransactionType;
import io.everytrade.server.plugin.api.parser.FeeRebateImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.ImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.TransactionCluster;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.ExchangeBean;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.util.List;

import static io.everytrade.server.model.TransactionType.FEE;
import static io.everytrade.server.model.TransactionType.REBATE;
import static io.everytrade.server.model.TransactionType.REWARD;
import static io.everytrade.server.plugin.impl.everytrade.parser.ParserUtils.DECIMAL_DIGITS;
import static io.everytrade.server.plugin.impl.everytrade.parser.ParserUtils.nullOrZero;
import static java.math.RoundingMode.HALF_UP;
import static java.util.Collections.emptyList;
import static lombok.AccessLevel.PROTECTED;

@Setter
@Getter
@FieldDefaults(level = PROTECTED)
public abstract class BaseTransactionMapperV1 extends ExchangeBean {
    boolean ignoredFee;
    boolean failedFee;
    String ignoredOrFailedFeeMessage;

    protected abstract TransactionType findTransactionType();

    protected abstract BaseClusterData mapData();

    @Override
    public TransactionCluster toTransactionCluster() {
        var data = mapData();
        TransactionCluster cluster =
            switch (data.transactionType) {
                case REWARD -> createRewardTransactionCluster(data);
                case BUY, SELL -> createBuySellTransactionCluster(data);
                case DEPOSIT, WITHDRAWAL -> createDepositOrWithdrawalTxCluster(data);
                case STAKE, UNSTAKE, STAKING_REWARD, EARNING, FORK, AIRDROP -> createOtherTransactionCluster(data);
                case FEE, REBATE -> createUnrelatedFeeOrRebate(data);
                default -> throw new DataValidationException(String.format("Unsupported transaction type %s.",
                    data.transactionType.name()));
            };
        if (ignoredFee) {
            cluster.setIgnoredFee(1, ignoredOrFailedFeeMessage);
        }
        if (failedFee) {
            cluster.setFailedFee(1, ignoredOrFailedFeeMessage);
        }
        return cluster;
    }

    private TransactionCluster createDepositOrWithdrawalTxCluster(BaseClusterData data) {
        var tx = ImportedTransactionBean.createDepositWithdrawal(
            data.uid,
            data.executed,
            data.base, //base
            data.quote,  //quote
            data.transactionType,
            data.volume,
            data.base.isFiat() ? null : data.address
        );
        return new TransactionCluster(tx, getRelatedTransactions(data));
    }

    private TransactionCluster createRewardTransactionCluster(BaseClusterData data) {
        return new TransactionCluster(
            new ImportedTransactionBean(
                data.uid,
                data.executed,
                data.base,
                data.base,
                REWARD,
                data.volume,
                null
            ),
            emptyList()
        );
    }

    private void validateBuySellTx(BaseClusterData data) {
        validateCurrencyPair(data.base, data.quote);
        validateCurrencyPair(data.base, data.quote, data.transactionType);
        if (data.base.isFiat()) {
            throw new DataValidationException(String.format("Base currency is not crypto: %s", data.base.code()));
        }
    }

    private TransactionCluster createBuySellTransactionCluster(BaseClusterData data) {
        validateBuySellTx(data);
        TransactionCluster cluster;
        cluster = new TransactionCluster(
            new ImportedTransactionBean(
                data.uid,
                data.executed,
                data.base,
                data.quote,
                data.transactionType,
                data.volume,
                data.unitPrice
            ),
            getRelatedTransactions(data)
        );
        return cluster;
    }

    public BigDecimal countUnitPrice(BaseClusterData data) {
        return evalUnitPrice(data.quoteAmount, data.volume);
    }

    private List<ImportedTransactionBean> getRelatedTransactions(BaseClusterData data) {
        List<ImportedTransactionBean> related = new java.util.ArrayList<>(emptyList());
        try {
            if (!nullOrZero(data.feeAmount)) {
                data.feeCurrency = Currency.fromCode(data.fee);
                var feeTx = new FeeRebateImportedTransactionBean(
                    (data.uid != null) ? data.uid + FEE_UID_PART : null,
                    data.executed,
                    data.feeCurrency,
                    data.feeCurrency,
                    FEE,
                    data.feeAmount.setScale(DECIMAL_DIGITS, HALF_UP),
                    data.feeCurrency
                );
                related.add(feeTx);
            }
            if (!nullOrZero(data.rebateAmount)) {
                data.rebateCurrency = Currency.fromCode(data.rebate);
                var rebateTx = new FeeRebateImportedTransactionBean(
                    (data.uid != null) ? data.uid + REBATE_UID_PART : null,
                    data.executed,
                    data.rebateCurrency,
                    data.rebateCurrency,
                    REBATE,
                    data.rebateAmount.setScale(DECIMAL_DIGITS, HALF_UP),
                    data.rebateCurrency
                );
                related.add(rebateTx);
            }
        } catch (IllegalArgumentException e) {
            ignoredFee = true;
            ignoredOrFailedFeeMessage += e.getMessage();
        } catch (Exception e) {
            failedFee = true;
            ignoredOrFailedFeeMessage += e.getMessage();
        }
        return related;
    }

    private TransactionCluster createUnrelatedFeeOrRebate(BaseClusterData data) {
        return new TransactionCluster(new FeeRebateImportedTransactionBean(
            data.uid,
            data.executed,
            data.base,
            data.base,
            data.transactionType,
            data.volume,
            data.base
        ), getRelatedTransactions(data));
    }

    private TransactionCluster createOtherTransactionCluster(BaseClusterData data) {
        var tx = new ImportedTransactionBean(
            data.uid,
            data.executed,
            data.base,
            data.base,
            data.transactionType,
            data.volume,
            data.unitPrice,
            data.note,
            data.address
        );
        return new TransactionCluster(tx, getRelatedTransactions(data));
    }
}
