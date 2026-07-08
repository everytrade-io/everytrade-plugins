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
import static io.everytrade.server.model.Currency.EUR;
import static io.everytrade.server.model.Currency.SOL;
import static io.everytrade.server.model.Currency.USDT;
import static io.everytrade.server.model.Currency._1INCH;
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
        final String row0 = "\"2022-02-18 13:24:05,ADABTC,BUY,\"\"1,500.0000050000\"\",\"\"2,500.0000000000ADA\"\"," +
            "\"\"1,250.0000000000BTC\"\",\"\"2,500.0000000000ADA\"\"\"\n";
        final String row1 = "\"2022-02-18 13:24:05,ADABTC,BUY,1500.0000050000,\"\"2,500.0000000000ADA\"\",\"\"1,250.0000000000BTC\"\"," +
            "\"\"2,500.0000000000ADA\"\"\"\n";
        final String row2 = "\"2022-02-18 13:24:05,ADABTC,BUY,1500.0000050000,\"\"2,500.0000000000ADA\"\",1250.0000000000BTC," +
            "\"\"2,500.0000000000ADA\"\"\"\n";
        final String row3 = "\"2022-02-18 13:24:05,ADABTC,BUY,1500.0000050000,\"\"2,500.0000000000ADA\"\",1250.0000000000BTC," +
            "2500.0000000000ADA\"\n";
        final String row4 = "\"2022-02-18 13:24:05,ADABTC,BUY,1500.0000050000,2500.0000000000ADA,1250.0000000000BTC,2500.0000000000ADA\"\n";
        final String row5 = "2022-02-18 13:24:05,ADABTC,BUY,1500.0000050000,2500.0000000000ADA,1250.0000000000BTC,2500.0000000000ADA\n";
        final String row6 = "2022-02-18 13:24:05,ADABTC,BUY,1500.0000050000,\"\"2,500.0000000000ADA\"\",1250.0000000000BTC," +
            "2500.0000000000ADA\n";
        final String row7 = "2022-02-18 13:24:05,ADABTC,BUY,\"\"1,500.0000050000\"\",2500.0000000000ADA,1250.0000000000BTC," +
            "2500.0000000000ADA\n";
        final String row8 = "\"2022-02-18 13:24:05,ADABTC,BUY,\"\"1,500.0000050000\"\",2500.0000000000ADA,1250.0000000000BTC," +
            "2500.0000000000ADA\"\n";
        final String row9 = "\"2022-02-18 13:24:05,ADABTC,BUY,1500.0000050000,\"\"2500.0000000000ADA\"\",1250.0000000000BTC," +
            "2500.0000000000ADA\"\n";
        final String row10 = "2022-02-18 13:24:05,ADABTC,BUY,1500.0000050000,2500.0000000000ADA,1250.0000000000BTC,2500.0000000000ADA\n";
        final String row11 = "2022-02-18 13:24:05,ADABTC,BUY,1500.0000050000,2500.0000000000ADA,1250.0000000000BTC,2500.0000000000ADA\n";

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
                Instant.parse("2022-02-18T13:24:05Z"),
                ADA,
                BTC,
                BUY,
                new BigDecimal("2500.00000000000000000"),
                new BigDecimal("0.50000000000000000")
            ),
            List.of(
                new FeeRebateImportedTransactionBean(
                    FEE_UID_PART,
                    Instant.parse("2022-02-18T13:24:05Z"),
                    ADA,
                    ADA,
                    FEE,
                    new BigDecimal("2500.00000000000000000"),
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
    void testLocalizedCzechHeaderWithTwoDigitYear() {
        // Binance Spot Trade History exported with Czech UI: translated header + 2-digit year (yy-MM-dd). See ETS-5030.
        final String header = "﻿Čas,Pár,Strana,Cena,Provedeno,Částka,Poplatek\n";
        final String row0 = "24-03-08 10:15:42,BTCEUR,BUY,50000,0.05BTC,2500EUR,0.002BNB\n";

        final TransactionCluster actual = ParserTestUtils.getTransactionCluster(header + row0);

        final TransactionCluster expected = new TransactionCluster(
            new ImportedTransactionBean(
                null,
                Instant.parse("2024-03-08T10:15:42Z"),
                BTC,
                EUR,
                BUY,
                new BigDecimal("0.05000000000000000"),
                new BigDecimal("50000.00000000000000000")
            ),
            List.of(
                new FeeRebateImportedTransactionBean(
                    FEE_UID_PART,
                    Instant.parse("2024-03-08T10:15:42Z"),
                    BNB,
                    BNB,
                    FEE,
                    new BigDecimal("0.00200000000000000"),
                    BNB
                )
            )
        );
        ParserTestUtils.checkEqual(expected, actual);
    }

    @Test
    void testDigitLeadingTickerFromClientCsv() {
        // ETS-5077: Binance glues amount+ticker with no separator ("5.11INCH" = 5.1 of 1INCH). A digit-leading ticker
        // (1INCH) used to split as amount 5.11 + unknown currency "INCH" and the whole row failed (ROW_PARSING_FAILED).
        // The pair is now resolved from the "Pair" column (1INCHUSDT) and the ticker stripped as a literal suffix.
        // Both rows are the customer's original data; SOLUSDT is the control that always worked.
        final String rowSol = "2022-01-20 23:17:49,SOLUSDT,BUY,129.95,0.48SOL,62.376USDT,0.00010548BNB\n";
        final String row1Inch = "2022-01-10 14:36:20,1INCHUSDT,BUY,1.969,5.11INCH,10.0419USDT,0.00001853BNB\n";

        final List<TransactionCluster> actual = ParserTestUtils.getTransactionClusters(HEADER_CORRECT + rowSol + row1Inch);
        final ParseResult result = ParserTestUtils.getParseResult(HEADER_CORRECT + rowSol + row1Inch);
        assertEquals(0, result.getParsingProblems().size(), "no row must fail to parse");

        final TransactionCluster expectedSol = new TransactionCluster(
            new ImportedTransactionBean(
                null,
                Instant.parse("2022-01-20T23:17:49Z"),
                SOL,
                USDT,
                BUY,
                new BigDecimal("0.48000000000000000"),
                new BigDecimal("129.95000000000000000")
            ),
            List.of(
                new FeeRebateImportedTransactionBean(
                    FEE_UID_PART,
                    Instant.parse("2022-01-20T23:17:49Z"),
                    BNB,
                    BNB,
                    FEE,
                    new BigDecimal("0.00010548000000000"),
                    BNB
                )
            )
        );
        final TransactionCluster expected1Inch = new TransactionCluster(
            new ImportedTransactionBean(
                null,
                Instant.parse("2022-01-10T14:36:20Z"),
                _1INCH,
                USDT,
                BUY,
                new BigDecimal("5.10000000000000000"),
                new BigDecimal("1.96900000000000000")
            ),
            List.of(
                new FeeRebateImportedTransactionBean(
                    FEE_UID_PART,
                    Instant.parse("2022-01-10T14:36:20Z"),
                    BNB,
                    BNB,
                    FEE,
                    new BigDecimal("0.00001853000000000"),
                    BNB
                )
            )
        );
        ParserTestUtils.checkEqual(expectedSol, actual.get(0));
        ParserTestUtils.checkEqual(expected1Inch, actual.get(1));
    }

    @Test
    void testCorrectParsingFee() {
        final String row0 = "2023-07-14 09:30:00,BTCBUSD,BUY,\"30,000.0000000000\",0.0010000000BTC,30.00000000BUSD,0.0000000000BNB\n";
        final String row1 = "2023-07-14 09:31:45,BTCBUSD,SELL,\"40,000.0000000000\",0.0020000000BTC,80.00000000BUSD,0.5000000000BNB\n";
        final String join = row0.concat(row1);

        final List<TransactionCluster> actual = ParserTestUtils.getTransactionClusters(HEADER_CORRECT + join);
        final ParseResult result = ParserTestUtils.getParseResult(HEADER_CORRECT + join);

        final TransactionCluster expected = new TransactionCluster(
            new ImportedTransactionBean(
                null,
                Instant.parse("2023-07-14T09:30:00Z"),
                BTC,
                BUSD,
                BUY,
                new BigDecimal("0.00100000000000000"),
                new BigDecimal("30000.00000000000000000")
            ),
            List.of()
        );
        final TransactionCluster expected1 = new TransactionCluster(
            new ImportedTransactionBean(
                null,
                Instant.parse("2023-07-14T09:31:45Z"),
                BTC,
                BUSD,
                SELL,
                new BigDecimal("0.00200000000000000"),
                new BigDecimal("40000.00000000000000000")
            ),
            List.of(
                new FeeRebateImportedTransactionBean(
                    FEE_UID_PART,
                    Instant.parse("2023-07-14T09:31:45Z"),
                    BNB,
                    BNB,
                    FEE,
                    new BigDecimal("0.50000000000000000"),
                    BNB
                )
            )
        );
        ParserTestUtils.checkEqual(expected, actual.get(0));
        ParserTestUtils.checkEqual(expected1, actual.get(1));
        assertEquals(0, result.getParsingProblems().size());
    }

}