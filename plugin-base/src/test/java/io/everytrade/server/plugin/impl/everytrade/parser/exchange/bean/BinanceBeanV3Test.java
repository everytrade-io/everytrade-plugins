package io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean;

import io.everytrade.server.model.Currency;
import io.everytrade.server.model.TransactionType;
import io.everytrade.server.plugin.api.parser.FeeRebateImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.ImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.ParseResult;
import io.everytrade.server.plugin.api.parser.TransactionCluster;
import io.everytrade.server.plugin.impl.everytrade.parser.exception.ParsingProcessException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static io.everytrade.server.model.Currency.ADA;
import static io.everytrade.server.model.Currency.BNB;
import static io.everytrade.server.model.Currency.BTC;
import static io.everytrade.server.model.Currency.BUSD;
import static io.everytrade.server.model.TransactionType.BUY;
import static io.everytrade.server.model.TransactionType.FEE;
import static io.everytrade.server.model.TransactionType.SELL;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.ExchangeBean.FEE_UID_PART;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

class BinanceBeanV3Test {
    public static final String HEADER_CORRECT = "\uFEFFDate(UTC),Pair,Side,Price,Executed,Amount,Fee\n";

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
    void testCorrectParsingRawTransactionBuy() {
        final String row0 = "\"2020-05-29 11:13:27,ADABTC,BUY,\"\"1,000.0000067200\"\",\"\"3,813.0000000000ADA\"\"," +
            "\"\"1,000.02562336BTC\"\",\"\"3,813.0000000000ADA\"\"\"\n";
        final String row1 = "\"2020-05-29 11:13:27,ADABTC,BUY,1000.0000067200,\"\"3,813.0000000000ADA\"\",\"\"1,000.02562336BTC\"\"," +
            "\"\"3,813.0000000000ADA\"\"\"\n";
        final String row2 = "\"2020-05-29 11:13:27,ADABTC,BUY,1000.0000067200,\"\"3,813.0000000000ADA\"\",1000.02562336BTC," +
            "\"\"3,813.0000000000ADA\"\"\"\n";
        final String row3 = "\"2020-05-29 11:13:27,ADABTC,BUY,1000.0000067200,\"\"3,813.0000000000ADA\"\",1000.02562336BTC," +
            "3813.0000000000ADA\"\n";
        final String row4 = "\"2020-05-29 11:13:27,ADABTC,BUY,1000.0000067200,3813.0000000000ADA,1000.02562336BTC,3813.0000000000ADA\"\n";
        final String row5 = "2020-05-29 11:13:27,ADABTC,BUY,1000.0000067200,3813.0000000000ADA,1000.02562336BTC,3813.0000000000ADA\n";
        final String row6 = "2020-05-29 11:13:27,ADABTC,BUY,1000.0000067200,\"\"3,813.0000000000ADA\"\",1000.02562336BTC," +
            "3813.0000000000ADA\n";
        final String row7 = "2020-05-29 11:13:27,ADABTC,BUY,\"\"1,000.0000067200\"\",3813.0000000000ADA,1000.02562336BTC," +
            "3813.0000000000ADA\n";
        final String row8 = "\"2020-05-29 11:13:27,ADABTC,BUY,\"\"1,000.0000067200\"\",3813.0000000000ADA,1000.02562336BTC," +
            "3813.0000000000ADA\"\n";
        final String row9 = "\"2020-05-29 11:13:27,ADABTC,BUY,1000.0000067200,\"\"3813.0000000000ADA\"\",1000.02562336BTC," +
            "3813.0000000000ADA\"\n";
        final String row10 = "2020-05-29 11:13:27,ADABTC,BUY,1000.0000067200,3813.0000000000ADA,\"\"1000.02562336BTC\"\"," +
            "3813.0000000000ADA\n";
        final String row11 = "2020-05-29 11:13:27,ADABTC,BUY,1000.0000067200,3813.0000000000ADA,1000.02562336BTC," +
            "\"\"3,813.0000000000ADA\"\"\n";

        final TransactionCluster actual0 = ParserTestUtils.getTransactionCluster(HEADER_CORRECT + row0);
        final TransactionCluster actual1 = ParserTestUtils.getTransactionCluster(HEADER_CORRECT + row1);
        final TransactionCluster actual2 = ParserTestUtils.getTransactionCluster(HEADER_CORRECT + row2);
        final TransactionCluster actual3 = ParserTestUtils.getTransactionCluster(HEADER_CORRECT + row3);
        final TransactionCluster actual4 = ParserTestUtils.getTransactionCluster(HEADER_CORRECT + row4);
        final TransactionCluster actual5 = ParserTestUtils.getTransactionCluster(HEADER_CORRECT + row5);
        final TransactionCluster actual6 = ParserTestUtils.getTransactionCluster(HEADER_CORRECT + row6);
        final TransactionCluster actual7 = ParserTestUtils.getTransactionCluster(HEADER_CORRECT + row7);
        final TransactionCluster actual8 = ParserTestUtils.getTransactionCluster(HEADER_CORRECT + row8);
        final TransactionCluster actual9 = ParserTestUtils.getTransactionCluster(HEADER_CORRECT + row9);
        final TransactionCluster actual10 = ParserTestUtils.getTransactionCluster(HEADER_CORRECT + row10);
        final TransactionCluster actual11 = ParserTestUtils.getTransactionCluster(HEADER_CORRECT + row11);

        final TransactionCluster expected = new TransactionCluster(
            new ImportedTransactionBean(
                null,
                Instant.parse("2020-05-29T11:13:27Z"),
                ADA,
                BTC,
                BUY,
                new BigDecimal("3813.00000000000000000"),
                new BigDecimal("0.26226740712300026")
            ),
            List.of(
                new FeeRebateImportedTransactionBean(
                    FEE_UID_PART,
                    Instant.parse("2020-05-29T11:13:27Z"),
                    ADA,
                    ADA,
                    FEE,
                    new BigDecimal("3813.00000000000000000"),
                    ADA
                )
            )
        );
        ParserTestUtils.checkEqual(expected, actual0);
        ParserTestUtils.checkEqual(expected, actual1);
        ParserTestUtils.checkEqual(expected, actual2);
        ParserTestUtils.checkEqual(expected, actual3);
        ParserTestUtils.checkEqual(expected, actual4);
        ParserTestUtils.checkEqual(expected, actual5);
        ParserTestUtils.checkEqual(expected, actual6);
        ParserTestUtils.checkEqual(expected, actual7);
        ParserTestUtils.checkEqual(expected, actual8);
        ParserTestUtils.checkEqual(expected, actual9);
        ParserTestUtils.checkEqual(expected, actual10);
        ParserTestUtils.checkEqual(expected, actual11);
    }

    @Test
    void testCorrectParsingFee() {
        final String row0 = "2021-06-02 16:45:27,BTCBUSD,BUY,\"37,850.0000000000\",0.0009100000BTC,34.44350000BUSD,0.0000000000BNB\n";
        final String row1 = "2021-06-02 16:07:12,BTCBUSD,SELL,\"37,900.0000000000\",0.0009090000BTC,34.45110000BUSD,1.0000000000BNB\n";
        final String join = row0.concat(row1);

        final List<TransactionCluster> actual = ParserTestUtils.getTransactionClusters(HEADER_CORRECT + join);
        final ParseResult result = ParserTestUtils.getParseResult(HEADER_CORRECT + join);

        final TransactionCluster expected = new TransactionCluster(
            new ImportedTransactionBean(
                null,
                Instant.parse("2021-06-02T16:45:27Z"),
                BTC,
                BUSD,
                BUY,
                new BigDecimal("0.00091000000000000"),
                new BigDecimal("37850.00000000000000000")
            ),
            List.of()
        );
        final TransactionCluster expected1 = new TransactionCluster(
            new ImportedTransactionBean(
                null,
                Instant.parse("2021-06-02T16:07:12Z"),
                BTC,
                BUSD,
                SELL,
                new BigDecimal("0.00090900000000000"),
                new BigDecimal("37900.00000000000000000")
            ),
            List.of(
                new FeeRebateImportedTransactionBean(
                    FEE_UID_PART,
                    Instant.parse("2021-06-02T16:07:12Z"),
                    BNB,
                    BNB,
                    FEE,
                    new BigDecimal("1.00000000000000000"),
                    BNB
                )
            )
        );
        ParserTestUtils.checkEqual(expected, actual.get(0));
        ParserTestUtils.checkEqual(expected1, actual.get(1));
        assertEquals(0, result.getParsingProblems().size());
    }

}