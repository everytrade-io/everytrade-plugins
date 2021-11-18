package io.everytrade.server.plugin.impl.everytrade;

import io.everytrade.server.model.Currency;
import io.everytrade.server.model.CurrencyPair;
import io.everytrade.server.plugin.api.parser.BuySellImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.DepositWithdrawalImportedTransaction;
import io.everytrade.server.plugin.api.parser.TransactionCluster;
import io.everytrade.server.test.mock.BitmexExchangeMock;
import io.everytrade.server.test.mock.BittrexExchangeMock;
import org.junit.jupiter.api.Test;
import org.knowm.xchange.bittrex.dto.account.BittrexDepositHistory;
import org.knowm.xchange.bittrex.dto.account.BittrexWithdrawalHistory;
import org.knowm.xchange.dto.account.FundingRecord;
import org.knowm.xchange.dto.trade.UserTrade;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

import static io.everytrade.server.model.Currency.BTC;
import static io.everytrade.server.model.Currency.USD;
import static io.everytrade.server.model.TransactionType.BUY;
import static io.everytrade.server.model.TransactionType.DEPOSIT;
import static io.everytrade.server.model.TransactionType.SELL;
import static io.everytrade.server.model.TransactionType.WITHDRAW;
import static io.everytrade.server.test.TestUtils.findOneCluster;
import static io.everytrade.server.test.TestUtils.fundingRecord;
import static io.everytrade.server.test.TestUtils.userTrade;
import static java.math.BigDecimal.ONE;
import static java.math.BigDecimal.TEN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class BittrexConnectorTest {

    private static final CurrencyPair PAIR = new CurrencyPair(BTC, USD);
    private static final String ADDRESS = "addrs0";

    @Test
    void testBuySellDepositWithdraw() {
        List<UserTrade> trades = List.of(
            userTrade(BUY, TEN, PAIR, new BigDecimal("10000"), TEN, USD),
            userTrade(SELL, ONE, PAIR, new BigDecimal("20000"), TEN, USD)
        );

        List<BittrexDepositHistory> deposits = List.of(deposit());
        List<BittrexWithdrawalHistory> withdrawals = List.of(withdrawal());

        var connector = new BittrexConnector(new BittrexExchangeMock(trades, deposits, withdrawals));
        var result = connector.getTransactions(null);

        assertNotNull(result.getDownloadStateData());
        assertEquals(4, result.getParseResult().getTransactionClusters().size());
        assertEquals(0, result.getParseResult().getParsingProblems().size());

        assertTx(findOneCluster(result, BUY), TEN);
        assertTx(findOneCluster(result, SELL), ONE);
        assertTx(findOneCluster(result, WITHDRAW), TEN);
        assertTx(findOneCluster(result, DEPOSIT), TEN);
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

        if (type.isDepositOrWithdraw()) {
            var depositWithdraw = (DepositWithdrawalImportedTransaction) tx;
            assertEquals(volume, depositWithdraw.getVolume());
            assertNotNull(depositWithdraw.getAddress());
        } else if (type.isBuyOrSell()) {
            var buySell  = (BuySellImportedTransactionBean) cluster.getMain();
            assertEquals(volume, buySell.getBaseQuantity());
            assertEquals(Currency.USD, tx.getQuote());
        }
    }

    private BittrexDepositHistory deposit() {
        var deposit = new BittrexDepositHistory();
        deposit.setId("1");
        deposit.setCryptoAddress(ADDRESS);
        deposit.setQuantity(TEN);
        deposit.setCurrencySymbol("BTC");
        deposit.setUpdatedAt(new Date());
        return deposit;
    }

    private BittrexWithdrawalHistory withdrawal() {
        var deposit = new BittrexWithdrawalHistory();
        deposit.setId("1");
        deposit.setCryptoAddress(ADDRESS);
        deposit.setQuantity(TEN);
        deposit.setCurrencySymbol("BTC");
        deposit.setCompletedAt(new Date());
        deposit.setTxCost(new BigDecimal("0.01"));
        return deposit;
    }
}
