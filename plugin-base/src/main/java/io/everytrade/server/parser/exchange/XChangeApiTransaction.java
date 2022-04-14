package io.everytrade.server.parser.exchange;

import com.univocity.parsers.common.DataValidationException;
import io.everytrade.server.model.Currency;
import io.everytrade.server.model.CurrencyPair;
import io.everytrade.server.model.TransactionType;
import io.everytrade.server.plugin.api.parser.BuySellImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.DepositWithdrawalImportedTransaction;
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
public class XChangeApiTransaction {

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
    Boolean logIgnoredFees;

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
            .feeCurrency(convert(trade.getFeeCurrency()))
            .build();
    }

    public static XChangeApiTransaction fromFunding(FundingRecord record) {
        var currency = convert(record.getCurrency());
        return XChangeApiTransaction.builder()
            .id(record.getInternalId())
            .timestamp(record.getDate().toInstant())
            .type(fundingTypeToTxType(record.getType()))
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

        final boolean isIgnoredFee = (feeCurrency == null);

        List<ImportedTransactionBean> related;
        if (nullOrZero(feeAmount) || isIgnoredFee) {
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
        if (type.isBuyOrSell()) {
            cluster = createBuySellTx(related);
        } else if (type.isDepositOrWithdrawal()) {
            cluster = createDepositWithdrawalTx(related);
        } else {
            throw new DataValidationException("Unsupported type " + type.name());
        }

        if (isIgnoredFee) {
            if(!logIgnoredFees) {
                // ignore - Bittrex does not send any data for deposit fees because the deposit is free of charge.
            } else {
                cluster.setIgnoredFee(1, "Fee " + (feeCurrency != null ? feeCurrency.code() : "null") + " currency is not base or quote");
            }
        }
        return cluster;
    }

    private TransactionCluster createDepositWithdrawalTx(List<ImportedTransactionBean> related) {
        return new TransactionCluster(
            new DepositWithdrawalImportedTransaction(
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

    private TransactionCluster createBuySellTx(List<ImportedTransactionBean> related) {
        return new TransactionCluster(
            new BuySellImportedTransactionBean(
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
    private static TransactionType fundingTypeToTxType(FundingRecord.Type type) {
        switch (type) {
            case WITHDRAWAL:
                return TransactionType.WITHDRAWAL;
            case DEPOSIT:
                return TransactionType.DEPOSIT;
            default:
                throw new DataValidationException("ExchangeBean.UNSUPPORTED_TRANSACTION_TYPE ".concat(type.name()));
        }
    }

    private static Currency convert(org.knowm.xchange.currency.Currency currency) {
        try {
            return Currency.fromCode(currency.getCurrencyCode());
        } catch (IllegalArgumentException e) {
            final org.knowm.xchange.currency.Currency currencyConverted =
                org.knowm.xchange.currency.Currency.getInstance(currency.getCurrencyCode()).getCommonlyUsedCurrency();
            return Currency.fromCode(currencyConverted.getCurrencyCode());
        }
    }
}
