package io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean;

import io.everytrade.server.plugin.api.parser.FeeRebateImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.ImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.TransactionCluster;
import io.everytrade.server.plugin.impl.everytrade.parser.EverytradeCsvMultiParser;
import io.everytrade.server.test.TestUtils;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static io.everytrade.server.model.Currency.BTC;
import static io.everytrade.server.model.Currency.USDT;
import static io.everytrade.server.model.TransactionType.BUY;
import static io.everytrade.server.model.TransactionType.FEE;
import static io.everytrade.server.model.TransactionType.SELL;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.ExchangeBean.FEE_UID_PART;

public class OkxBeanV2Test {

    private static final String HEADER_CORRECT = "id,Order id,Time,Trade Type,Symbol,Action,Amount,Trading Unit,Filled Price," +
        "Filled Price Unit,PnL,Fee,Fee Unit,Position Change,Position Balance,Position Unit,Balance Change,Balance,Balance Unit\n";

    @Test
    void testBuy() {
        final String row0 = """
            597524845211287598,597489919078301696,2023-07-07 13:30:24,Spot,BTC-USDT,Buy,718.40213000,BTC,30200.00000000,\
            USDT,0E-8,-0.57472170,USDT,0E-8,0E-8,USDT,717.82740830,1953.25677549,USDT
            597524845211287596,597489919078301696,2023-07-07 13:30:24,Spot,BTC-USDT,Sell,0.02378815,BTC,30200.00000000,USDT,\
            0E-8,0E-8,BTC,0E-8,0E-8,BTC,-0.02378815,0E-8,BTC
            """;
        final List<TransactionCluster> actual = ParserTestUtils.getTransactionClusters(HEADER_CORRECT + row0);

        final TransactionCluster expected = new TransactionCluster(
            new ImportedTransactionBean(
                null,
                Instant.parse("2023-07-07T13:30:24Z"),
                USDT,
                BTC,
                BUY,
                new BigDecimal("718.40213000"),
                new BigDecimal("0.00003311258278146"),
                null,
                null
            ),
            List.of(
                new FeeRebateImportedTransactionBean(
                    FEE_UID_PART,
                    Instant.parse("2023-07-07T13:30:24Z"),
                    USDT,
                    USDT,
                    FEE,
                    new BigDecimal("0.57472170"),
                    USDT
                )
            )
        );

        TestUtils.testTxs(expected.getMain(), actual.get(0).getMain());
        TestUtils.testTxs(expected.getRelated().get(0), actual.get(0).getRelated().get(0));
    }

    @Test
    void testSell() {
        final String row0 = """
            647669143671791638,646124859063660559,2023-11-22 22:25:56,Spot,BTC-USDT,Sell,0.04902691,BTC,37800.00000000,USDT,\
            0E-8,0E-8,BTC,0E-8,0E-8,BTC,-0.04902691,0E-8,BTC
            647669143671791639,646124859063660559,2023-11-22 22:25:56,Spot,BTC-USDT,Buy,1853.21719800,BTC,37800.00000000,USDT,\
            0E-8,-1.48257376,USDT,0E-8,0E-8,USDT,1851.73462424,1851.73462424,USDT
            """;
        final List<TransactionCluster> actual = ParserTestUtils.getTransactionClusters(HEADER_CORRECT + row0);

        final TransactionCluster expected = new TransactionCluster(
            new ImportedTransactionBean(
                null,
                Instant.parse("2023-11-22T22:25:56Z"),
                BTC,
                USDT,
                SELL,
                new BigDecimal("0.04902691"),
                new BigDecimal("37800.00000000000000000"),
                null,
                null
            ),
            List.of(
                new FeeRebateImportedTransactionBean(
                    FEE_UID_PART,
                    Instant.parse("2023-11-22T22:25:56Z"),
                    USDT,
                    USDT,
                    FEE,
                    new BigDecimal("1.48257376"),
                    USDT
                )
            )
        );

        TestUtils.testTxs(expected.getMain(), actual.get(0).getMain());
        TestUtils.testTxs(expected.getRelated().get(0), actual.get(0).getRelated().get(0));
    }
}
