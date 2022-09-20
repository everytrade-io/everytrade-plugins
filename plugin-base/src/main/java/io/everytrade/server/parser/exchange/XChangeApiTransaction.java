package io.everytrade.server.parser.exchange;

import com.univocity.parsers.common.DataValidationException;
import io.everytrade.server.model.Currency;
import io.everytrade.server.model.CurrencyPair;
import io.everytrade.server.model.TransactionType;
import io.everytrade.server.plugin.api.parser.FeeRebateImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.ImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.TransactionCluster;
import lombok.Builder;
import lombok.Value;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.account.FundingRecord;
import org.knowm.xchange.dto.trade.UserTrade;
import org.knowm.xchange.instrument.Instrument;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static io.everytrade.server.model.TransactionType.FEE;
import static io.everytrade.server.plugin.impl.everytrade.parser.ParserUtils.nullOrZero;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.ExchangeBean.FEE_UID_PART;
import static java.util.Collections.emptyList;

@Value
@Builder
public class XChangeApiTransaction implements IXChangeApiTransaction {

    String id;
    Instant timestamp;
    TransactionType type;
    Currency base;
    Currency quote;
    Currency feeCurrency;
    BigDecimal originalAmount;
    BigDecimal price;
    BigDecimal feeAmount;
    String address;
    @Builder.Default
    boolean logIgnoredFees = true;

    public static XChangeApiTransaction fromTrade(UserTrade trade) {
        final Instrument instrument = trade.getInstrument();
        if (!(instrument instanceof org.knowm.xchange.currency.CurrencyPair)) {
            throw new DataValidationException("Derivatives are not supported yet.");
        }
        org.knowm.xchange.currency.CurrencyPair currencyPair = (org.knowm.xchange.currency.CurrencyPair) instrument;

        return XChangeApiTransaction.builder()
            .id(trade.getId())
            .timestamp(trade.getTimestamp().toInstant())
            .type(orderTypeToTxType(trade.getType()))
            .base(convert(currencyPair.base))
            .quote(convert(currencyPair.counter))
            .originalAmount(trade.getOriginalAmount())
            .price(trade.getPrice())
            .feeAmount(trade.getFeeAmount())
            .feeCurrency((trade.getFeeCurrency() == null) ? null : convert(trade.getFeeCurrency()))
            .build();
    }

    public static XChangeApiTransaction fromFunding(FundingRecord record) {
        var currency = convert(record.getCurrency());
        return XChangeApiTransaction.builder()
            .id(record.getInternalId())
            .timestamp(record.getDate().toInstant())
            .type(fundingTypeToTxType(record))
            .base(currency)
            .quote(null)
            .originalAmount(record.getAmount())
            .feeAmount(record.getFee())
            .feeCurrency(currency)
            .address(record.getAddress())
            .build();
    }

    public TransactionCluster toTransactionCluster() {
        if (type.isBuyOrSell()) {
            try {
                new CurrencyPair(base, quote);
            } catch (CurrencyPair.FiatCryptoCombinationException e) {
                throw new DataValidationException(e.getMessage());
            }
        }

        final boolean isIncorrectFee = (feeCurrency == null);

        List<ImportedTransactionBean> related;
        if (nullOrZero(feeAmount) || isIncorrectFee) {
            related = emptyList();
        } else {
            related = List.of(new FeeRebateImportedTransactionBean(
                    id + FEE_UID_PART,
                    timestamp,
                    feeCurrency,
                    feeCurrency,
                    FEE,
                    feeAmount,
                    feeCurrency
                )
            );
        }

        TransactionCluster cluster;
        if (type.isDepositOrWithdrawal()) {
            cluster = createDepositWithdrawalTx(related);
        } else if (type.isBuyOrSell() || type.isZeroCostGain() || type.isStaking()) {
            cluster = createTx(related);
        } else {
            throw new DataValidationException("Unsupported type " + type.name());
        }

        if (isIncorrectFee && logIgnoredFees) {
            cluster.setFailedFee(1, "Fee " + (feeCurrency != null ? feeCurrency.code() : "null") + " currency is not base or quote");
        } else if (nullOrZero(feeAmount)) {
//            cluster.setIgnoredFee(1, "Fee amount is 0 " + (feeCurrency != null ? feeCurrency.code() : ""));
        }
        return cluster;
    }

    private TransactionCluster createDepositWithdrawalTx(List<ImportedTransactionBean> related) {
        return new TransactionCluster(
            ImportedTransactionBean.createDepositWithdrawal(
                id,
                timestamp,
                base,
                quote,
                type,
                originalAmount,
                address
            ),
            related
        );
    }

    private TransactionCluster createTx(List<ImportedTransactionBean> related) {
        return new TransactionCluster(
            new ImportedTransactionBean(
                id,
                timestamp,
                base,
                quote,
                type,
                originalAmount,
                price
            ),
            related
        );
    }

    private static TransactionType orderTypeToTxType(Order.OrderType orderType) {
        switch (orderType) {
            case ASK:
                return TransactionType.SELL;
            case BID:
                return TransactionType.BUY;
            default:
                throw new DataValidationException("ExchangeBean.UNSUPPORTED_TRANSACTION_TYPE ".concat(orderType.name()));
        }
    }
    private static TransactionType fundingTypeToTxType(FundingRecord record) {
        switch (record.getType()) {
            case WITHDRAWAL:
                return TransactionType.WITHDRAWAL;
            case DEPOSIT:
                return TransactionType.DEPOSIT;
            case OTHER_INFLOW:
                // TODO this is binance specific - move it elsewhere
                if (isAirdrop(record)) {
                    return TransactionType.AIRDROP;
                }
                if (isStake(record)) {
                    return TransactionType.STAKE;
                }
                if (isUnstake(record)) {
                    return TransactionType.UNSTAKE;
                }
                if (isStakingReward(record)) {
                    return TransactionType.STAKING_REWARD;
                }
            default:
                throw new DataValidationException("ExchangeBean.UNSUPPORTED_TRANSACTION_TYPE ".concat(record.getType().name()));
        }
    }

    protected static Currency convert(org.knowm.xchange.currency.Currency currency) {
        try {
            return Currency.fromCode(currency.getCurrencyCode());
        } catch (IllegalArgumentException e) {
            final org.knowm.xchange.currency.Currency currencyConverted =
                org.knowm.xchange.currency.Currency.getInstance(currency.getCurrencyCode()).getCommonlyUsedCurrency();
            return Currency.fromCode(currencyConverted.getCurrencyCode());
        }
    }

    private static boolean isAirdrop(FundingRecord r) {
        return r.getDescription() != null && r.getDescription().toLowerCase().endsWith("airdrop");
    }

    private static boolean isStake(FundingRecord r) {
        return r.getDescription() != null && r.getDescription().toLowerCase().startsWith("staking");
    }

    private static boolean isUnstake(FundingRecord r) {
        return false; // TODO dont know how this looks in API
    }

    private static boolean isStakingReward(FundingRecord r) {
        return r.getDescription() != null && r.getDescription().toLowerCase().endsWith("distribution");
    }
}
