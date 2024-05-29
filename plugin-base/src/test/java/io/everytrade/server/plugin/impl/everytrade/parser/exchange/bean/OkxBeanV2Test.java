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

import static io.everytrade.server.model.Currency.CZK;
import static io.everytrade.server.model.Currency.USDT;
import static io.everytrade.server.model.TransactionType.BUY;
import static io.everytrade.server.model.TransactionType.FEE;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.ExchangeBean.FEE_UID_PART;

public class OkxBeanV2Test {

    private static final String HEADER_CORRECT = "id,Order id,Time,Trade Type,Symbol,Action,Amount,Trading Unit,Filled Price," +
        "Filled Price Unit,PnL,Fee,Fee Unit,Position Change,Position Balance,Position Unit,Balance Change,Balance,Balance Unit\n";


    @Test
    void testCSV() {
        File file = new File("/Users/slithercze/Desktop", "feedback-25d27ea2-68d7-44bb-83a0-5b8f550df92a.csv");
        String header = HEADER_CORRECT.substring(0, HEADER_CORRECT.indexOf("\n"));
        var parser = new EverytradeCsvMultiParser().parse(file, header);
        var varTwo = parser;
    }

    @Test
    void testBuy() {
        final String row0 = """
            647848550172581894,647848550151081984,2023-11-23 10:18:50,Transfer,,Transfer out,0,cont,0E-8,USDT,0E-8,0E-8,USDT,0E-8,0E-8,USDT,-1851.73462424,0E-8,USDT
            647669143671791638,646124859063660559,2023-11-22 22:25:56,Spot,BTC-USDT,Sell,0.04902691,BTC,37800.00000000,USDT,0E-8,0E-8,BTC,0E-8,0E-8,BTC,-0.04902691,0E-8,BTC""";
        final List<TransactionCluster> actual = ParserTestUtils.getTransactionClusters(HEADER_CORRECT + row0);

        final TransactionCluster expected = new TransactionCluster(
            new ImportedTransactionBean(
                null,
                Instant.parse("2023-06-15T11:00:40.800Z"),
                USDT,
                CZK,
                BUY,
                new BigDecimal("5"),
                new BigDecimal("22.10340112000000000"),
                null,
                null
            ),
            List.of(
                new FeeRebateImportedTransactionBean(
                    FEE_UID_PART,
                    Instant.parse("2023-06-15T11:00:40.800Z"),
                    USDT,
                    USDT,
                    FEE,
                    new BigDecimal("0.05"),
                    USDT
                )
            )
        );

        TestUtils.testTxs(expected.getMain(), actual.get(0).getMain());
        TestUtils.testTxs(expected.getRelated().get(0), actual.get(0).getRelated().get(0));
    }

    @Test
    void testSell() {
        final String row0 = "Tether;USDT;2023-06-15T11:00:40.8;buy;110.5170056;5;22.10340112;0.05;CZK;USDT\n";
        final List<TransactionCluster> actual = ParserTestUtils.getTransactionClusters(HEADER_CORRECT + row0);

        final TransactionCluster expected = new TransactionCluster(
            new ImportedTransactionBean(
                null,
                Instant.parse("2023-06-15T11:00:40.800Z"),
                USDT,
                CZK,
                BUY,
                new BigDecimal("5"),
                new BigDecimal("22.10340112000000000"),
                null,
                null
            ),
            List.of(
                new FeeRebateImportedTransactionBean(
                    FEE_UID_PART,
                    Instant.parse("2023-06-15T11:00:40.800Z"),
                    USDT,
                    USDT,
                    FEE,
                    new BigDecimal("0.05"),
                    USDT
                )
            )
        );

        TestUtils.testTxs(expected.getMain(), actual.get(0).getMain());
        TestUtils.testTxs(expected.getRelated().get(0), actual.get(0).getRelated().get(0));
    }
}
