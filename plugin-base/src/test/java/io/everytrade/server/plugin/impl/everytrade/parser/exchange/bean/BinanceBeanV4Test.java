package io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean;

import io.everytrade.server.model.Currency;
import io.everytrade.server.model.TransactionType;
import io.everytrade.server.plugin.api.parser.FeeRebateImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.ImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.TransactionCluster;
import io.everytrade.server.test.TestUtils;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static io.everytrade.server.model.TransactionType.BUY;
import static io.everytrade.server.model.TransactionType.SELL;

class BinanceBeanV4Test {
    public static final String HEADER_CORRECT = "\uFEFFUser_ID,UTC_Time,Account,Operation,Coin,Change,Remark\n";

    @Test
    void testConvertSell() {
        final String row0 = "41438313,2022-01-01 10:56:29,Spot,Sell,ETH,76.65970000,\"\"\n";
        final String row1 = "41438313,2022-01-01 10:56:29,Spot,Fee,BNB,-0.00011168,\"\"\n";
        final String row2 = "41438313,2022-01-01 10:56:29,Spot,Sell,BTC,-13.00000000,\"\"\n";
        final String join = row0 + row1 + row2;

        final TransactionCluster actual = ParserTestUtils.getTransactionCluster(HEADER_CORRECT + join);

        final TransactionCluster expected = new TransactionCluster(
            new ImportedTransactionBean(
                "[2, 4, 3]",
                Instant.parse("2022-01-01T10:56:29Z"),
                Currency.ETH,
                Currency.BTC,
                SELL,
                new BigDecimal("76.6597000000"),
                new BigDecimal("0.1695806271")
            ),
            List.of(
                new FeeRebateImportedTransactionBean(
                    null,
                    Instant.parse("2022-01-01T10:56:29Z"),
                    Currency.BNB,
                    Currency.BNB,
                    TransactionType.FEE,
                    new BigDecimal("0.00011168"),
                    Currency.BNB
                )
            )
        );
        TestUtils.testTxs( expected.getRelated().get(0),actual.getRelated().get(0));
        TestUtils.testTxs( expected.getMain(),actual.getMain());
    }

    @Test
    void testConvertBuy() {
        final String row0 = "41438313,2022-01-01 10:58:15,Spot,Buy,BTC,-5896.73580000,\"\"\n";
        final String row1 = "41438313,2022-01-01 10:58:15,Spot,Fee,BNB,-0.00859660,\"\"\n";
        final String row2 = "41438313,2022-01-01 10:58:15,Spot,Buy,ETH,4477.40000000,\"\"\n";
        final String join = row0 + row1 + row2;

        final TransactionCluster actual = ParserTestUtils.getTransactionCluster(HEADER_CORRECT + join);

        final TransactionCluster expected = new TransactionCluster(
            new ImportedTransactionBean(
                "[4, 2, 3]",
                Instant.parse("2022-01-01T10:58:15Z"),
                Currency.ETH,
                Currency.BTC,
                BUY,
                new BigDecimal("4477.4000000000"),
                new BigDecimal("1.3170000000")
            ),
            List.of(
                new FeeRebateImportedTransactionBean(
                    null,
                    Instant.parse("2022-01-01T10:58:15Z"),
                    Currency.BNB,
                    Currency.BNB,
                    TransactionType.FEE,
                    new BigDecimal("0.00859660"),
                    Currency.BNB
                )
            )
        );
        TestUtils.testTxs( expected.getRelated().get(0),actual.getRelated().get(0));
        TestUtils.testTxs( expected.getMain(),actual.getMain());
    }

}