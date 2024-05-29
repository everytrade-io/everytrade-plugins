package io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean;

import io.everytrade.server.plugin.api.parser.FeeRebateImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.ImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.TransactionCluster;
import io.everytrade.server.plugin.impl.everytrade.parser.EverytradeCsvMultiParser;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static io.everytrade.server.model.Currency.BTC;
import static io.everytrade.server.model.Currency.ETH;
import static io.everytrade.server.model.Currency.GAME;
import static io.everytrade.server.model.Currency.LTC;
import static io.everytrade.server.model.Currency.USDT;
import static io.everytrade.server.model.TransactionType.BUY;
import static io.everytrade.server.model.TransactionType.DEPOSIT;
import static io.everytrade.server.model.TransactionType.FEE;
import static io.everytrade.server.model.TransactionType.SELL;
import static io.everytrade.server.model.TransactionType.WITHDRAWAL;

public class PoloniexBeanTest {
    private static final String HEADER_BUY_SELL = "create_time,trade_id,market,buyer_wallet,side,price,amount,fee,fee_currency,fee_total\n";
    private static final String HEADER_DEPOSIT = "f_created_at,currency,f_amount,f_address,f_status\n";
    private static final String HEADER_WITHDRAWAL = "f_date,currency,f_amount,f_feededucted,f_status\n";

    @Test
    void testBuy() {
        final String row0 = "2017-08-13 21:23:06,208692912,BTCETH,exchange,Buy,0.072939820000000002,0.137099320000000000,0.0015," +
            "ETH,0.00020564898\n";
        var actual = ParserTestUtils.getParseResult(HEADER_BUY_SELL + row0);

        final TransactionCluster expected = new TransactionCluster(
            new ImportedTransactionBean(
                null,
                Instant.parse("2017-08-13T21:23:06Z"),
                ETH,
                BTC,
                BUY,
                new BigDecimal("0.137099320000000000"),
                new BigDecimal("0.072939820000000002"),
                null,
                null
            ),
            List.of(
                new FeeRebateImportedTransactionBean(
                    null,
                    Instant.parse("2017-08-13T21:23:06Z"),
                    ETH,
                    ETH,
                    FEE,
                    new BigDecimal("0.00020564898000000"),
                    ETH
                )
            )
        );

        ParserTestUtils.checkEqual(expected, actual.getTransactionClusters().get(0));
    }

    @Test
    void testSell() {
        final String row0 = "2017-09-02 22:02:46,224008205,USDTLTC,exchange,Sell,73.536038189999999000,0.100000000000000010,0.0015," +
            "USDT,0.00015000000000000004\n";
        var actual = ParserTestUtils.getParseResult(HEADER_BUY_SELL + row0);

        final TransactionCluster expected = new TransactionCluster(
            new ImportedTransactionBean(
                null,
                Instant.parse("2017-09-02T22:02:46Z"),
                LTC,
                USDT,
                SELL,
                new BigDecimal("0.100000000000000010"),
                new BigDecimal("73.536038189999999000"),
                null,
                null
            ),
            List.of(
                new FeeRebateImportedTransactionBean(
                    null,
                    Instant.parse("2017-09-02T22:02:46Z"),
                    USDT,
                    USDT,
                    FEE,
                    new BigDecimal("0.00015000000000000"),
                    USDT
                )
            )
        );

        ParserTestUtils.checkEqual(expected, actual.getTransactionClusters().get(0));
    }

    @Test
    void testDeposit() {
        final String row0 = "2017-08-13 20:29:47,BTC,0.03435352,14qu5sFXxnVoBaoDvqEbGKx6FhGWyGTDsT,COMPLETED\n";
        var actual = ParserTestUtils.getParseResult(HEADER_DEPOSIT + row0);

        final TransactionCluster expected = new TransactionCluster(
            new ImportedTransactionBean(
                null,
                Instant.parse("2017-08-13T20:29:47Z"),
                BTC,
                BTC,
                DEPOSIT,
                new BigDecimal("0.03435352"),
                null,
                null,
                "14qu5sFXxnVoBaoDvqEbGKx6FhGWyGTDsT"
            ),
            List.of()
        );

        ParserTestUtils.checkEqual(expected, actual.getTransactionClusters().get(0));
    }

    @Test
    void testWithdrawal() {
        final String row0 = "2019-12-15 21:29:36,GAME,1.99500000,0.01000000,COMPLETED\n";
        var actual = ParserTestUtils.getParseResult(HEADER_WITHDRAWAL + row0);

        final TransactionCluster expected = new TransactionCluster(
            new ImportedTransactionBean(
                null,
                Instant.parse("2019-12-15T21:29:36Z"),
                GAME,
                GAME,
                WITHDRAWAL,
                new BigDecimal("1.99500000"),
                null,
                null,
                null
            ),
            List.of(
                new FeeRebateImportedTransactionBean(
                    null,
                    Instant.parse("2019-12-15T21:29:36Z"),
                    GAME,
                    GAME,
                    FEE,
                    new BigDecimal("0.01000000000000000"),
                    GAME
                )
            )
        );

        ParserTestUtils.checkEqual(expected, actual.getTransactionClusters().get(0));
    }

    @Test
    void testCSV() {
        File file = new File("/Users/slithercze/Desktop", "202405 poloniex podpora Spot history.csv");
        String header = HEADER_BUY_SELL.substring(0, HEADER_BUY_SELL.indexOf("\n"));
        var parser = new EverytradeCsvMultiParser().parse(file, header);
        var varTwo = parser;
    }
}
