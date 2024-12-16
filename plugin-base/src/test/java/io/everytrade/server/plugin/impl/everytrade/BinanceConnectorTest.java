package io.everytrade.server.plugin.impl.everytrade;

import io.everytrade.server.model.Currency;
import io.everytrade.server.model.CurrencyPair;
import io.everytrade.server.model.TransactionType;
import io.everytrade.server.plugin.api.parser.ImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.TransactionCluster;
import io.everytrade.server.test.mock.BinanceExchangeMock;
import org.junit.jupiter.api.Test;
import org.knowm.xchange.dto.account.FundingRecord;
import org.knowm.xchange.dto.trade.UserTrade;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static io.everytrade.server.test.TestUtils.findOneCluster;
import static io.everytrade.server.test.TestUtils.fundingRecord;
import static io.everytrade.server.test.TestUtils.userTrade;
import static io.everytrade.server.model.Currency.BTC;
import static io.everytrade.server.model.Currency.USD;
import static io.everytrade.server.model.Currency.ETH;
import static io.everytrade.server.model.Currency.SOL;
import static io.everytrade.server.model.TransactionType.BUY;
import static io.everytrade.server.model.TransactionType.DEPOSIT;
import static io.everytrade.server.model.TransactionType.SELL;
import static io.everytrade.server.model.TransactionType.WITHDRAWAL;
import static java.math.BigDecimal.ONE;
import static java.math.BigDecimal.TEN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class BinanceConnectorTest {

    private static final CurrencyPair PAIR = new CurrencyPair(BTC, USD);
    private static final CurrencyPair PAIR_CONVERT = new CurrencyPair(SOL, ETH);
    private static final String ADDRESS = "addrs0";
    private static final BigDecimal CONVERT_VOLUME = new BigDecimal("19.75595584");
    private static final BigDecimal CONVERT_PRICE = new BigDecimal("0.0344200000000000");

    @Test
    void testBuySellDepositWithdrawal() {
        List<UserTrade> trades = List.of(
            userTrade(String.valueOf(100), BUY, TEN, PAIR, new BigDecimal("10000"), TEN, USD),
            userTrade(String.valueOf(101), SELL, ONE, PAIR, new BigDecimal("20000"), TEN, USD),
            userTrade(String.valueOf(102), BUY, CONVERT_VOLUME, PAIR_CONVERT, CONVERT_PRICE, BigDecimal.ZERO, ETH)
        );

        List<FundingRecord> records = List.of(
            fundingRecord(DEPOSIT, TEN, BTC, ONE, ADDRESS),
            fundingRecord(WITHDRAWAL, TEN, BTC, ONE, ADDRESS)
        );

        var connector = new BinanceConnector(new BinanceExchangeMock(trades, records), new XChangeConnectorParser(), "BTC/USD", true);
        var result = connector.getTransactions(null);

        assertNotNull(result.getDownloadStateData());
        assertEquals(5, result.getParseResult().getTransactionClusters().size());
        assertEquals(0, result.getParseResult().getParsingProblems().size());

        assertTx(findOneCluster(result, BUY), TEN);
        assertTx(findOneCluster(result, SELL), ONE);
        assertTx(findOneCluster(result, WITHDRAWAL), TEN);
        assertTx(findOneCluster(result, DEPOSIT), TEN);
        assertTxConvert(result.getParseResult().getTransactionClusters().get(2));
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
        assertEquals(volume, tx.getVolume());

        if (type.isDepositOrWithdrawal()) {
            assertNotNull(tx.getAddress());
        } else if (type.isBuyOrSell()) {
            assertEquals(Currency.USD, tx.getQuote());
        }
    }

    private void assertTxConvert(TransactionCluster res) {
        final TransactionCluster expected = new TransactionCluster(
            new ImportedTransactionBean(
                "102",
                Instant.now(),
                Currency.ETH,
                Currency.SOL,
                TransactionType.BUY,
                new BigDecimal("0.68"),
                new BigDecimal("19.75595584")
            ),
            List.of(
            )
        );
        assertEquals(res.getMain().getAction(), TransactionType.BUY);
        assertEquals(res.getMain().getUid(), "102");
        assertEquals(res.getMain().getBase(), SOL);
        assertEquals(res.getMain().getQuote(), ETH);
        assertEquals(res.getMain().getVolume(), new BigDecimal("19.75595584"));
        assertEquals(res.getMain().getUnitPrice(), new BigDecimal("0.0344200000000000"));
    }
}
