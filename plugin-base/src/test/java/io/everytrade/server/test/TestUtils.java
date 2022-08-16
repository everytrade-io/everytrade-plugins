package io.everytrade.server.test;

import io.everytrade.server.model.CurrencyPair;
import io.everytrade.server.model.TransactionType;
import io.everytrade.server.plugin.api.connector.DownloadResult;
import io.everytrade.server.plugin.api.parser.TransactionCluster;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.account.FundingRecord;
import org.knowm.xchange.dto.trade.UserTrade;

import java.math.BigDecimal;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestUtils {

    public static void bigDecimalEquals(BigDecimal expected, BigDecimal actual) {
        if (expected == null || actual == null) {
            assertEquals(expected, actual);
            return;
        }
        assertTrue(expected.compareTo(actual) == 0);
    }

    public static TransactionCluster findOneCluster(DownloadResult result, TransactionType type) {
        Optional<TransactionCluster> c = result.getParseResult().getTransactionClusters().stream()
            .filter(it -> it.getMain().getAction() == type)
            .findFirst();
        assertTrue(c.isPresent());
        return c.get();
    }

    public static UserTrade userTrade(
        TransactionType type,
        BigDecimal volume,
        CurrencyPair pair,
        BigDecimal price,
        BigDecimal fee,
        io.everytrade.server.model.Currency feeCurrency
    ) {
        return userTrade(type, volume, pair, price, fee, feeCurrency, new Date(), UUID.randomUUID().toString());
    }

    public static UserTrade userTrade(
        TransactionType type,
        BigDecimal volume,
        CurrencyPair pair,
        BigDecimal price,
        BigDecimal fee,
        io.everytrade.server.model.Currency feeCurrency,
        Date date,
        String orderId
    ) {
        return new UserTrade(
            type == TransactionType.BUY ? Order.OrderType.BID : Order.OrderType.ASK,
            volume,
            toXchangePair(pair),
            price,
            date,
            UUID.randomUUID().toString(),
            orderId,
            fee,
            toXchangeCurrency(feeCurrency),
            UUID.randomUUID().toString()
        );
    }

    public static UserTrade userTrade(
        String id,
        TransactionType type,
        BigDecimal volume,
        CurrencyPair pair,
        BigDecimal price,
        BigDecimal fee,
        io.everytrade.server.model.Currency feeCurrency
    ) {
        return new UserTrade(
            type == TransactionType.BUY ? Order.OrderType.BID : Order.OrderType.ASK,
            volume,
            toXchangePair(pair),
            price,
            new Date(),
            id,
            UUID.randomUUID().toString(),
            fee,
            toXchangeCurrency(feeCurrency),
            UUID.randomUUID().toString()
        );
    }

    public static FundingRecord fundingRecord(
        TransactionType type,
        BigDecimal volume,
        io.everytrade.server.model.Currency currency,
        BigDecimal fee,
        String address
    ) {
        return new FundingRecord(
            address,
            new Date(),
            toXchangeCurrency(currency),
            volume,
            UUID.randomUUID().toString(),
            UUID.randomUUID().toString(),
            type == TransactionType.DEPOSIT ? FundingRecord.Type.DEPOSIT : FundingRecord.Type.WITHDRAWAL,
            FundingRecord.Status.COMPLETE,
            BigDecimal.ONE,
            fee,
            null
        );
    }

    public static org.knowm.xchange.currency.CurrencyPair toXchangePair(CurrencyPair etPair) {
        return new org.knowm.xchange.currency.CurrencyPair(toXchangeCurrency(etPair.getBase()), toXchangeCurrency(etPair.getQuote()));
    }

    public static Currency toXchangeCurrency(io.everytrade.server.model.Currency etCurrency) {
        return new Currency(etCurrency.code());
    }
}
