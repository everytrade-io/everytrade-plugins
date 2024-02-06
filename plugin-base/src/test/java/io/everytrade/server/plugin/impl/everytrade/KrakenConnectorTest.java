package io.everytrade.server.plugin.impl.everytrade;

import io.everytrade.server.model.Currency;
import io.everytrade.server.model.CurrencyPair;
import io.everytrade.server.model.TransactionType;
import io.everytrade.server.plugin.api.parser.ImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.TransactionCluster;
import io.everytrade.server.test.mock.KrakenExchangeMock;
import org.junit.jupiter.api.Test;
import org.knowm.xchange.dto.account.FundingRecord;
import org.knowm.xchange.dto.trade.UserTrade;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static io.everytrade.server.model.TransactionType.STAKE;
import static io.everytrade.server.test.TestUtils.findOneCluster;
import static io.everytrade.server.test.TestUtils.fundingRecord;
import static io.everytrade.server.test.TestUtils.testTxs;
import static io.everytrade.server.test.TestUtils.userTrade;
import static io.everytrade.server.model.Currency.BTC;
import static io.everytrade.server.model.Currency.USD;
import static io.everytrade.server.model.TransactionType.BUY;
import static io.everytrade.server.model.TransactionType.DEPOSIT;
import static io.everytrade.server.model.TransactionType.SELL;
import static io.everytrade.server.model.TransactionType.WITHDRAWAL;
import static java.math.BigDecimal.ONE;
import static java.math.BigDecimal.TEN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class KrakenConnectorTest {

    private static final CurrencyPair PAIR = new CurrencyPair(BTC, USD);
    private static final String ADDRESS = "addrs0";

    @Test
    void testBuySellDepositWithdrawal() {
        List<UserTrade> trades = List.of(
            userTrade(BUY, TEN, PAIR, new BigDecimal("10000"), TEN, USD),
            userTrade(SELL, ONE, PAIR, new BigDecimal("20000"), TEN, USD)
        );

        List<FundingRecord> records = List.of(
            fundingRecord(DEPOSIT, TEN, BTC, ONE, ADDRESS),
            fundingRecord(WITHDRAWAL, TEN, BTC, ONE, ADDRESS)
        );

        var connector = new KrakenConnector(new KrakenExchangeMock(trades, records));
        var result = connector.getTransactions(null);
        KrakenExchangeMock.close();

        assertNotNull(result.getDownloadStateData());
        assertEquals(4, result.getParseResult().getTransactionClusters().size());
        assertEquals(0, result.getParseResult().getParsingProblems().size());

        assertTx(findOneCluster(result, BUY), TEN);
        assertTx(findOneCluster(result, SELL), ONE);
        assertTx(findOneCluster(result, WITHDRAWAL), TEN);
        assertTx(findOneCluster(result, DEPOSIT), TEN);
    }

    @Test
    void testBoundingAsStake() {
        List<FundingRecord> records = new ArrayList<>();

        var actual = new FundingRecord(
            null,
            Date.from(Instant.parse("2023-01-31T10:01:29Z")),
            new org.knowm.xchange.currency.Currency("DOT28.S"),
            new BigDecimal("163.6967014800"),
            "RVFIYQT-IMIRSQ-KO5UGU",
            null,
            FundingRecord.Type.OTHER_INFLOW,
            FundingRecord.Status.COMPLETE,
            null,
            null,
            "bonding"
        );

        var expected = new ImportedTransactionBean(
            "RVFIYQT-IMIRSQ-KO5UGU",
            Instant.parse("2023-01-31T10:01:29Z"),
            Currency.DOT,
            null,
            STAKE,
            new BigDecimal("163.6967014800"),
            null,
            null,
            null
        );
        records.add(actual);
        var connector = new KrakenConnector(new KrakenExchangeMock(records));
        var result = connector.getTransactions(null);
        KrakenExchangeMock.close();
        assertEquals(1, result.getParseResult().getTransactionClusters().size());
        testTxs(expected, result.getParseResult().getTransactionClusters().get(0).getMain());
    }

    @Test
    void testReward() {
        List<FundingRecord> records = new ArrayList<>();

        var actual = new FundingRecord(
            null,
            Date.from(Instant.parse("2023-01-22T02:35:21Z")),
            new org.knowm.xchange.currency.Currency("ETH2"),
            new BigDecimal("0.0068700590"),
            "RUOWBVL-JTJOHI-GWDYYM",
            null,
            FundingRecord.Type.OTHER_INFLOW,
            FundingRecord.Status.COMPLETE,
            null,
            null,
            "reward"
        );

        var expected = new ImportedTransactionBean(
            "RUOWBVL-JTJOHI-GWDYYM",
            Instant.parse("2023-01-22T02:35:21Z"),
            Currency.ETH,
            null,
            TransactionType.STAKING_REWARD,
            new BigDecimal("0.0068700590"),
            null,
            null,
            null
        );
        records.add(actual);
        var connector = new KrakenConnector(new KrakenExchangeMock(records));
        var result = connector.getTransactions(null);
        KrakenExchangeMock.close();
        assertEquals(1, result.getParseResult().getTransactionClusters().size());
        testTxs(expected, result.getParseResult().getTransactionClusters().get(0).getMain());
    }

    private void assertTx(TransactionCluster cluster, BigDecimal volume) {
        var tx = cluster.getMain();
        var type = tx.getAction();
        assertNotNull(tx);

        assertNotNull(tx.getUid());
        assertNotNull(tx.getExecuted());
        assertEquals(Currency.BTC, tx.getBase());
        assertNotNull(tx.getImported());
        assertNull(cluster.getIgnoredFeeReason());
        assertEquals(0, cluster.getIgnoredFeeTransactionCount());

        if (type.isDepositOrWithdrawal()) {
            assertEquals(volume, tx.getVolume());
        } else if (type.isBuyOrSell()) {
            assertEquals(volume, tx.getVolume());
            assertEquals(Currency.USD, tx.getQuote());
        }
    }
}
