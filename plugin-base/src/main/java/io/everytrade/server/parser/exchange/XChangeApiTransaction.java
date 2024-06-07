package io.everytrade.server.parser.exchange;

import com.univocity.parsers.common.DataValidationException;
import io.everytrade.server.model.Currency;
import io.everytrade.server.model.CurrencyPair;
import io.everytrade.server.model.TransactionType;
import io.everytrade.server.plugin.api.parser.FeeRebateImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.ImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.TransactionCluster;
import io.everytrade.server.util.CoinMateDataUtil;
import lombok.Builder;
import lombok.Value;
import org.knowm.xchange.coinbase.v2.dto.account.transactions.CoinbaseShowTransactionV2;
import org.knowm.xchange.coinbase.v2.dto.account.transactions.CoinbaseTransactionV2Expand;
import org.knowm.xchange.coinmate.dto.trade.CoinmateTransactionHistoryEntry;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.account.FundingRecord;
import org.knowm.xchange.dto.trade.UserTrade;
import org.knowm.xchange.instrument.Instrument;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static io.everytrade.server.model.TransactionType.BUY;
import static io.everytrade.server.model.TransactionType.EARNING;
import static io.everytrade.server.model.TransactionType.FEE;
import static io.everytrade.server.model.TransactionType.REWARD;
import static io.everytrade.server.model.TransactionType.SELL;
import static io.everytrade.server.model.TransactionType.WITHDRAWAL;
import static io.everytrade.server.plugin.impl.everytrade.parser.ParserUtils.ROUNDING_MODE;
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

    public static XChangeApiTransaction tradeCoinbase(List<CoinbaseShowTransactionV2> transaction) {
        var base = transaction.stream()
            .filter(t -> t.getAmount().getAmount().compareTo(BigDecimal.ZERO) > 0)
            .findFirst()
            .orElseThrow(() -> new DataValidationException("Trade transaction missing pair base"));
        var quote = transaction.stream()
            .filter(t -> t.getAmount().getAmount().compareTo(BigDecimal.ZERO) < 0)
            .findFirst()
            .orElseThrow(() -> new DataValidationException("Trade transaction missing pair quote"));

        return XChangeApiTransaction.builder()
            .id(String.valueOf(base.getId()))
            .timestamp(Instant.parse(base.getCreatedAt()))
            .type(BUY)
            .base(Currency.fromCode(base.getAmount().getCurrency()))
            .quote(Currency.fromCode(quote.getAmount().getCurrency()))
            .price(quote.getAmount().getAmount().abs().divide(base.getAmount().getAmount().abs(), 8, ROUNDING_MODE))
            .originalAmount(base.getAmount().getAmount())
            .build();
    }
    public static XChangeApiTransaction buySellCoinbase(CoinbaseShowTransactionV2 transaction) {
        TransactionType type = null;
        CoinbaseTransactionV2Expand buySellTx = null;
        switch (transaction.getType()) {
            case "buy" -> {
                type = BUY;
                buySellTx = transaction.getBuy();
            }
            case "sell" -> {
                type = SELL;
                buySellTx = transaction.getSell();
            }
        }

        if (buySellTx == null) {
            throw new DataValidationException("Coinbase transaction type not supported: " + transaction.getType());
        }

        Currency feeCurrency = null;
        if (buySellTx.getFee() != null){
            feeCurrency = buySellTx.getFee().getCurrency() != null ? Currency.fromCode(buySellTx.getFee().getCurrency()) : null;
        }

        return XChangeApiTransaction.builder()
            .id(String.valueOf(transaction.getId()))
            .timestamp(Instant.parse(transaction.getCreatedAt()))
            .type(type)
            .price(buySellTx.getSubtotal().getAmount())
            .base(Currency.fromCode(transaction.getAmount().getCurrency()))
            .quote(Currency.fromCode(transaction.getNativeAmount().getCurrency()))
            .originalAmount(transaction.getAmount().getAmount())
            .feeAmount(feeCurrency != null ? buySellTx.getFee().getAmount() : BigDecimal.ZERO)
            .feeCurrency(feeCurrency)
            .build();
    }

    public static XChangeApiTransaction rewardWithdrawalCoinbase(CoinbaseShowTransactionV2 transaction) {
        TransactionType type = null;
        switch (transaction.getType()) {
            case "tx", "interest" -> type = REWARD;
            case "send" -> type = WITHDRAWAL;
            case "earn_payout" -> type = EARNING;
        }

        return XChangeApiTransaction.builder()
            .id(String.valueOf(transaction.getId()))
            .timestamp(Instant.parse(transaction.getCreatedAt()))
            .type(type)
            .base(Currency.fromCode(transaction.getAmount().getCurrency()))
            .quote(Currency.fromCode(transaction.getAmount().getCurrency()))
            .originalAmount(transaction.getAmount().getAmount().abs())
            .build();
    }

    public static XChangeApiTransaction fromCoinMateTransactions(CoinmateTransactionHistoryEntry transaction) {
        CoinMateDataUtil.adaptTransactionStatus(transaction.getStatus());
        var currency = Currency.fromCode(transaction.getAmountCurrency());
        String priceCurrency = transaction.getPriceCurrency();
        Currency quote = priceCurrency != null ? Currency.fromCode(priceCurrency) : null;
        String feeCurrency = transaction.getFeeCurrency();
        Currency fee = feeCurrency != null ? Currency.fromCode(feeCurrency) : null;
        return XChangeApiTransaction.builder()
            .id(String.valueOf(transaction.getTransactionId()))
            .timestamp(Instant.ofEpochMilli(transaction.getTimestamp()))
            .type(CoinMateDataUtil.mapCoinMateType(transaction.getTransactionType()))
            .price(transaction.getPrice())
            .base(currency)
            .quote(quote)
            .originalAmount(transaction.getAmount())
            .feeAmount(transaction.getFee())
            .feeCurrency(fee)
            .build();

    }

    public TransactionCluster toTransactionCluster() {
        boolean isFailedFee = false;
        final boolean isIgnoredFee = (feeCurrency == null && !nullOrZero(feeAmount));
        String failedFeeMessage = "";

        if (type.isBuyOrSell()) {
            try {
                new CurrencyPair(base, quote);
            } catch (CurrencyPair.FiatCryptoCombinationException e) {
                throw new DataValidationException(e.getMessage());
            }
        }

        List<ImportedTransactionBean> related;
        if (nullOrZero(feeAmount) || isIgnoredFee) {
            related = emptyList();
        } else {
            try {
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
            } catch (Exception e) {
                isFailedFee = true;
                failedFeeMessage = e.getMessage();
                related = emptyList();
            }
        }

        TransactionCluster cluster;
        if (type.isDepositOrWithdrawal()) {
            cluster = createDepositWithdrawalTx(related);
        } else if (type.isBuyOrSell() || type.isZeroCostGain() || type.isStaking()) {
            cluster = createTx(related);
        } else {
            throw new DataValidationException("Unsupported type " + type.name());
        }

        if (isIgnoredFee && logIgnoredFees) {
            cluster.setIgnoredFee(1, "Fee " + (feeCurrency != null ? feeCurrency.code() : "null") + " currency is not base or quote");
        }
        if (isFailedFee) {
            cluster.setFailedFee(1, String.format("Fee transaction failed - %s", failedFeeMessage));
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
