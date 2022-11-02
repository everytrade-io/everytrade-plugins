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
import java.util.ArrayList;
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
public abstract class BaseTransactionMapper extends ExchangeBean {
    boolean ignoredFee;
    boolean failedFee;
    String ignoredOrFailedFeeMessage;

    protected abstract TransactionType findTransactionType();

    protected abstract BaseClusterData mapData();

    @Override
    public TransactionCluster toTransactionCluster() {
        var data = mapData();
        TransactionCluster cluster =
            switch (data.getTransactionType()) {
                case REWARD -> createRewardTransactionCluster(data);
                case BUY, SELL -> createBuySellTransactionCluster(data);
                case DEPOSIT, WITHDRAWAL -> createDepositOrWithdrawalTxCluster(data);
                case STAKE, UNSTAKE, STAKING_REWARD, EARNING, FORK, AIRDROP -> createOtherTransactionCluster(data);
                case FEE, REBATE -> createUnrelatedFeeOrRebate(data);
                default -> throw new DataValidationException(String.format("Unsupported transaction type %s.",
                    data.getTransactionType().name()));
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
            data.getUid(),
            data.getExecuted(),
            data.getBase(), //base
            data.getQuote(),  //quote
            data.getTransactionType(),
            data.getVolume(),
            data.getBase().isFiat() ? null : data.getAddress()
        );
        return new TransactionCluster(tx, getRelatedTransactions(data));
    }

    private TransactionCluster createRewardTransactionCluster(BaseClusterData data) {
        return new TransactionCluster(
            new ImportedTransactionBean(
                data.getUid(),
                data.getExecuted(),
                data.getBase(),
                data.getBase(),
                REWARD,
                data.getVolume(),
                null
            ),
            emptyList()
        );
    }

    private void validateBuySellTx(BaseClusterData data) {
        validateCurrencyPair(data.getBase(), data.getQuote(), data.getTransactionType());
        if (data.getBase().isFiat()) {
            throw new DataValidationException(String.format("Base currency is not crypto: %s", data.getBase().code()));
        }
    }

    private TransactionCluster createBuySellTransactionCluster(BaseClusterData data) {
        validateBuySellTx(data);
        TransactionCluster cluster;
        cluster = new TransactionCluster(
            new ImportedTransactionBean(
                data.getUid(),
                data.getExecuted(),
                data.getBase(),
                data.getQuote(),
                data.getTransactionType(),
                data.getVolume(),
                data.getUnitPrice()
            ),
            getRelatedTransactions(data)
        );
        return cluster;
    }

    public BigDecimal countUnitPrice(BaseClusterData data) {
        return evalUnitPrice(data.getQuoteAmount(), data.getVolume());
    }

    private List<ImportedTransactionBean> getRelatedTransactions(BaseClusterData data) {
        List<ImportedTransactionBean> related = new ArrayList<>();
        try {
            if (!nullOrZero(data.getFeeAmount())) {
                var feeCurrency = Currency.fromCode(data.getFee());
                var feeTx = new FeeRebateImportedTransactionBean(
                    (data.getUid() != null) ? data.getUid() + FEE_UID_PART : null,
                    data.getExecuted(),
                    feeCurrency,
                    feeCurrency,
                    FEE,
                    data.getFeeAmount().setScale(DECIMAL_DIGITS, HALF_UP),
                    feeCurrency
                );
                related.add(feeTx);
            }
            if (!nullOrZero(data.getRebateAmount())) {
                var rebateCurrency = Currency.fromCode(data.getRebate());
                var rebateTx = new FeeRebateImportedTransactionBean(
                    (data.getUid() != null) ? data.getUid() + REBATE_UID_PART : null,
                    data.getExecuted(),
                    rebateCurrency,
                    rebateCurrency,
                    REBATE,
                    data.getRebateAmount().setScale(DECIMAL_DIGITS, HALF_UP),
                    rebateCurrency
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
        return new TransactionCluster(
            new FeeRebateImportedTransactionBean(
                data.getUid(),
                data.getExecuted(),
                data.getBase(),
                data.getBase(),
                data.getTransactionType(),
                data.getVolume(),
                data.getBase()
            ), getRelatedTransactions(data));
    }

    private TransactionCluster createOtherTransactionCluster(BaseClusterData data) {
        var tx = new ImportedTransactionBean(
            data.getUid(),
            data.getExecuted(),
            data.getBase(),
            data.getBase(),
            data.getTransactionType(),
            data.getVolume(),
            data.getUnitPrice(),
            data.getNote(),
            data.getAddress()
        );
        return new TransactionCluster(tx, getRelatedTransactions(data));
    }
}
