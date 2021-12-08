package io.everytrade.server.plugin.impl.everytrade;

import io.everytrade.server.model.Currency;
import io.everytrade.server.model.CurrencyPair;
import io.everytrade.server.plugin.api.parser.BuySellImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.DepositWithdrawalImportedTransaction;
import io.everytrade.server.plugin.api.parser.TransactionCluster;
import io.everytrade.server.test.mock.CoinbaseExchangeMock;
import org.junit.jupiter.api.Test;
import org.knowm.xchange.dto.account.FundingRecord;
import org.knowm.xchange.dto.trade.UserTrade;

import java.math.BigDecimal;
import java.util.List;

import static io.everytrade.server.test.TestUtils.findOneCluster;
import static io.everytrade.server.test.TestUtils.fundingRecord;
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

class CoinbaseConnectorTest {

    private static final CurrencyPair PAIR = new CurrencyPair(BTC, USD);
    private static final String ADDRESS = "addrs0";

    // TODO trouble with mockito and mocking Coinbase
    // @Test
    void testBuySellDepositWithdrawal() {
        List<UserTrade> trades = List.of(
            userTrade(BUY, TEN, PAIR, new BigDecimal("10000"), TEN, USD),
            userTrade(SELL, ONE, PAIR, new BigDecimal("20000"), TEN, USD)
        );

        List<FundingRecord> records = List.of(
            fundingRecord(DEPOSIT, TEN, BTC, ONE, ADDRESS),
            fundingRecord(WITHDRAWAL, TEN, BTC, ONE, ADDRESS)
        );

        var connector = new CoinbaseConnector(new CoinbaseExchangeMock(trades, records));
        var result = connector.getTransactions(null);

        assertNotNull(result.getDownloadStateData());
        assertEquals(2, result.getParseResult().getTransactionClusters().size());
        assertEquals(0, result.getParseResult().getParsingProblems().size());

        assertTx(findOneCluster(result, BUY), TEN);
        assertTx(findOneCluster(result, SELL), ONE);
        // TODO implement funding
        //assertTx(findOneCluster(result, WITHDRAWAL), TEN);
        //assertTx(findOneCluster(result, DEPOSIT), TEN);
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
            var depositWithdrawal = (DepositWithdrawalImportedTransaction) tx;
            assertEquals(volume, depositWithdrawal.getVolume());
            assertNotNull(depositWithdrawal.getAddress());
        } else if (type.isBuyOrSell()) {
            var buySell  = (BuySellImportedTransactionBean) cluster.getMain();
            assertEquals(volume, buySell.getBaseQuantity());
            assertEquals(Currency.USD, tx.getQuote());
        }
    }
}
