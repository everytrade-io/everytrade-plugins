package io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean;

import io.everytrade.server.model.Currency;
import io.everytrade.server.model.TransactionType;
import io.everytrade.server.plugin.api.parser.ImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.TransactionCluster;
import io.everytrade.server.plugin.impl.everytrade.parser.exception.ParsingProcessException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.fail;

class BinanceBeanV5Test {
    public static final String HEADER_CORRECT = "\uFEFFDate(UTC);Product Name;Coin;Amount\n";

    @Test
    void testWrongHeader() {
        final String headerWrong = "Date(UTC);Pair;Type;OrdeX Price;Order Amount;AvgTrading Price;Filled;Total;status\n";
        try {
            ParserTestUtils.testParsing(headerWrong);
            fail("No expected exception has been thrown.");
        } catch (ParsingProcessException ignored) {
        }
    }

    @Test
    void testEarningTxs() {
        final String row1 = "2023-01-04 05:43:39;IOTX;IOTX;0.00006298";
        final String row2 = "2023-01-04 05:10:50;BUSD;BUSD;0.00032876";
        final String row3 = "2023-01-04 04:59:00;LDO;LDO;0.00005492";

        final TransactionCluster actual1 = ParserTestUtils.getTransactionCluster(HEADER_CORRECT + row1);
        final TransactionCluster actual2 = ParserTestUtils.getTransactionCluster(HEADER_CORRECT + row2);
        final TransactionCluster actual3 = ParserTestUtils.getTransactionCluster(HEADER_CORRECT + row3);

        final TransactionCluster expected1 = new TransactionCluster(
            new ImportedTransactionBean(
                null,
                Instant.parse("2023-01-04T05:43:39Z"),
                Currency.IOTX,
                Currency.IOTX,
                TransactionType.EARNING,
                new BigDecimal("0.00006298"),
                null,
                null,
                null

            ), Collections.emptyList());

        final TransactionCluster expected2 = new TransactionCluster(
            new ImportedTransactionBean(
                null,
                Instant.parse("2023-01-04T05:10:50Z"),
                Currency.BUSD,
                Currency.BUSD,
                TransactionType.EARNING,
                new BigDecimal("0.00032876"),
                null,
                null,
                null

            ), Collections.emptyList());

        final TransactionCluster expected3 = new TransactionCluster(
            new ImportedTransactionBean(
                null,
                Instant.parse("2023-01-04T04:59:00Z"),
                Currency.LDO,
                Currency.LDO,
                TransactionType.EARNING,
                new BigDecimal("0.00005492"),
                null,
                null,
                null

            ), Collections.emptyList());

        ParserTestUtils.checkEqual(expected1, actual1);
        ParserTestUtils.checkEqual(expected2, actual2);
        ParserTestUtils.checkEqual(expected3, actual3);
    }

}
