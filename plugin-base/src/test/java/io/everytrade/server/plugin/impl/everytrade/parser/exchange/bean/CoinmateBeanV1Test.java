package io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean;

import io.everytrade.server.model.Currency;
import io.everytrade.server.model.TransactionType;
import io.everytrade.server.plugin.api.parser.FeeRebateImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.ImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.ParsingProblem;
import io.everytrade.server.plugin.api.parser.TransactionCluster;
import io.everytrade.server.plugin.impl.everytrade.parser.exception.ParsingProcessException;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.ExchangeBean;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static io.everytrade.server.model.Currency.CZK;
import static io.everytrade.server.model.TransactionType.REWARD;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.ExchangeBean.FEE_UID_PART;
import static io.everytrade.server.util.CoinMateDataUtil.MARKET_BUY_OPERATION;
import static io.everytrade.server.util.CoinMateDataUtil.MARKET_SELL_OPERATION;
import static io.everytrade.server.util.CoinMateDataUtil.QUICK_BUY_OPERATION;
import static java.util.Collections.emptyList;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class CoinmateBeanV1Test {
    private static final String HEADER_CORRECT = "ID;Date;Type;Amount;Amount Currency;Price;Price Currency;Fee;" +
        "Fee Currency;Total;" +
        "Total Currency;Description;Status\n";

    private static final String HEADER_TWO = "ID;Date;Account;Type;Amount;Amount Currency;Price;Price Currency;Fee;" +
        "Fee Currency;Total;Total Currency;Description;Status;First balance after;" +
        "First balance after Currency;Second balance after;Second balance after Currency\n";

    private static final String HEADER_THREE = "ID;Datum;Účet;Typ;Částka;Částka měny;Cena;Cena měny;Poplatek;Poplatek měny;" +
        "Celkem;Celkem měny;Popisek;Status;První zůstatek po;První zůstatek po měně;Druhý zůstatek po;Druhý zůstatek po měně\n";

    @Test
    void testCorrectHeader() {
        try {
            ParserTestUtils.testParsing(HEADER_CORRECT);
        } catch (ParsingProcessException e) {
            fail("Unexpected exception has been thrown.");
        }
    }

    @Test
    void testWrongHeader() {
        final String headerWrong = "ID;Date;Type;Amount;Amount Xurrency;Price;Price Currency;Fee;Fee Currency;Total;" +
            "Total Currency;Description;Status\n";
        try {
            ParserTestUtils.testParsing(headerWrong);
            fail("No expected exception has been thrown.");
        } catch (ParsingProcessException ignored) {
        }
    }


    @Test
    void testCorrectParsingRawTransaction() {
        final String row = "4;2019-08-30 05:05:24;BUY;0.0019;BTC;8630.7;EUR;0.03443649;EUR;16.43276649;EUR;;OK\n";
        final TransactionCluster actual = ParserTestUtils.getTransactionCluster(HEADER_CORRECT + row);
        final TransactionCluster expected = new TransactionCluster(
            new ImportedTransactionBean(
                "4",
                Instant.parse("2019-08-30T05:05:24Z"),
                Currency.BTC,
                Currency.EUR,
                TransactionType.BUY,
                new BigDecimal("0.0019"),
                new BigDecimal("8630.7")
            ),
            List.of(
                new FeeRebateImportedTransactionBean(
                    "4" + FEE_UID_PART,
                    Instant.parse("2019-08-30T05:05:24Z"),
                    Currency.EUR,
                    Currency.EUR,
                    TransactionType.FEE,
                    new BigDecimal("0.03443649"),
                    Currency.EUR
                )
            )
        );
        ParserTestUtils.checkEqual(expected, actual);
    }

    @Test
    void testCorrectParsingRawTransactionWithCzechHeader() {
        var czechHeader = "ID;Datum;Účet;Typ;Částka;Částka měny;Cena;Cena měny;Poplatek;Poplatek měny;Celkem;Celkem měny;Popisek;Status;" +
            "První zůstatek po;První zůstatek po měně;Druhý zůstatek po;Druhý zůstatek po měně\n";
        var row = "7722246;2021-03-21 18:54:15;M;QUICK_BUY;0.01574751;BTC;1265612.25778996;CZK;69.75584587;CZK;-19999.99753154;CZK;;OK;" +
            "0.01574751;BTC;0.00591488;CZK\n";
        final TransactionCluster actual = ParserTestUtils.getTransactionCluster(czechHeader + row);
        final TransactionCluster expected = new TransactionCluster(
            new ImportedTransactionBean(
                "7722246",
                Instant.parse("2021-03-21T18:54:15Z"),
                Currency.BTC,
                CZK,
                TransactionType.BUY,
                new BigDecimal("0.01574751"),
                new BigDecimal("1265612.25778996"),
                QUICK_BUY_OPERATION,
                null
            ),
            List.of(
                new FeeRebateImportedTransactionBean(
                    "7722246" + FEE_UID_PART,
                    Instant.parse("2021-03-21T18:54:15Z"),
                    CZK,
                    CZK,
                    TransactionType.FEE,
                    new BigDecimal("69.75584587"),
                    CZK
                )
            )
        );
        ParserTestUtils.checkEqual(expected, actual);
    }

    @Test
    void testCorrectParsingRawTransactionAnotherAction() {
        final String row = "4;2019-08-30 05:05:24;QUICK_BUY;0.0019;BTC;8630.7;EUR;0.03443649;EUR;16.43276649;" +
            "EUR;;OK\n";
        final TransactionCluster actual = ParserTestUtils.getTransactionCluster(HEADER_CORRECT + row);
        final TransactionCluster expected = new TransactionCluster(
            new ImportedTransactionBean(
                "4",
                Instant.parse("2019-08-30T05:05:24Z"),
                Currency.BTC,
                Currency.EUR,
                TransactionType.BUY,
                new BigDecimal("0.0019"),
                new BigDecimal("8630.7"),
                QUICK_BUY_OPERATION,
                null
            ),
            List.of(
                new FeeRebateImportedTransactionBean(
                    "4" + FEE_UID_PART,
                    Instant.parse("2019-08-30T05:05:24Z"),
                    Currency.EUR,
                    Currency.EUR,
                    TransactionType.FEE,
                    new BigDecimal("0.03443649"),
                    Currency.EUR
                )
            )
        );
        ParserTestUtils.checkEqual(expected, actual);
    }

    @Test
    void testCorrectParsingRawTransactionMarketBuySell() {
        final String row0 = "4;2019-08-30 05:05:24;MARKET_BUY;0.0019;BTC;8630.7;EUR;0.03443649;EUR;16.43276649;" +
            "EUR;;OK\n";
        final String row1 = "4;2019-08-30 05:05:24;MARKET_SELL;0.0019;BTC;8630.7;EUR;0.03443649;EUR;16.43276649;" +
            "EUR;;OK\n";
        final TransactionCluster actual0 = ParserTestUtils.getTransactionCluster(HEADER_CORRECT + row0);
        final TransactionCluster actual1 = ParserTestUtils.getTransactionCluster(HEADER_CORRECT + row1);
        final TransactionCluster expected0 = new TransactionCluster(
            new ImportedTransactionBean(
                "4",
                Instant.parse("2019-08-30T05:05:24Z"),
                Currency.BTC,
                Currency.EUR,
                TransactionType.BUY,
                new BigDecimal("0.0019"),
                new BigDecimal("8630.7"),
                MARKET_BUY_OPERATION,
                null
            ),
            List.of(
                new FeeRebateImportedTransactionBean(
                    "4" + FEE_UID_PART,
                    Instant.parse("2019-08-30T05:05:24Z"),
                    Currency.EUR,
                    Currency.EUR,
                    TransactionType.FEE,
                    new BigDecimal("0.03443649"),
                    Currency.EUR
                )
            )
        );

        final TransactionCluster expected1 = new TransactionCluster(
            new ImportedTransactionBean(
                "4",
                Instant.parse("2019-08-30T05:05:24Z"),
                Currency.BTC,
                Currency.EUR,
                TransactionType.SELL,
                new BigDecimal("0.0019"),
                new BigDecimal("8630.7"),
                MARKET_SELL_OPERATION,
                null
            ),
            List.of(
                new FeeRebateImportedTransactionBean(
                    "4" + FEE_UID_PART,
                    Instant.parse("2019-08-30T05:05:24Z"),
                    Currency.EUR,
                    Currency.EUR,
                    TransactionType.FEE,
                    new BigDecimal("0.03443649"),
                    Currency.EUR
                )
            )
        );
        ParserTestUtils.checkEqual(expected0, actual0);
        ParserTestUtils.checkEqual(expected1, actual1);
    }

    @Test
    void testTransactionRebateReward() {
        final String row = "8477834;16.08.2021 9:42;M;;1.84599318;CZK; ; ; ; ; ; ;" +
            "User: georgesoft (ID: 85425, Account ID: 88299);OK;913180.69082074;CZK; ; ";
        final TransactionCluster actual = ParserTestUtils.getTransactionCluster(HEADER_TWO + row);
        final TransactionCluster expected = new TransactionCluster(
            new ImportedTransactionBean(
                "8477834",
                Instant.parse("2021-08-16T09:42:00Z"),
                CZK,
                CZK,
                REWARD,
                new BigDecimal("1.84599318"),
                null
            ),
            emptyList()
        );
        ParserTestUtils.checkEqual(expected, actual);
    }

    @Test
    void testTransactionRebateRewardAffiliate() {
        final String row = "8669156;2021-10-10 20:26:11;M;AFFILIATE;5.14366556;CZK;;;;;5.14366556;CZK;" +
            "User: georgesoft (ID: 85425, Account ID: 88299);OK;520422.2716417;CZK;;";
        final TransactionCluster actual = ParserTestUtils.getTransactionCluster(HEADER_THREE + row);
        final TransactionCluster expected = new TransactionCluster(
            new ImportedTransactionBean(
                "8669156",
                Instant.parse("2021-10-10T20:26:11Z"),
                CZK,
                CZK,
                REWARD,
                new BigDecimal("5.14366556"),
                null,
                "AFFILIATE",
                null
            ),
            emptyList()
        );
        ParserTestUtils.checkEqual(expected, actual);
    }

    @Test
    void testIgnoredTransactionType() {
        var row = "4;2019-08-30 05:05:24;DEPOSITZ;0.0019;BTC;8630.7;EUR;0.03443649;EUR;16.43276649;EUR;;OK\n";
        var parsingProblem = ParserTestUtils.getParsingProblem(HEADER_CORRECT + row);
        var error = parsingProblem.getMessage();
        assertTrue(error.contains(ExchangeBean.UNSUPPORTED_TRANSACTION_TYPE.concat("DEPOSIT")));

    }

    @Test
    void testIgnoredStatus() {
        // Verify that currency unit at "Price Currency" and "Fee Currency" fields are the same,
        // if not skip the row and report an invalid one
        final String row = "4;2019-08-30 05:05:24;BUY;0.0019;BTC;8630.7;EUR;0.03443649;EUR;16.43276649;EUR;;" +
            "n/a\n";
        final ParsingProblem parsingProblem = ParserTestUtils.getParsingProblem(HEADER_CORRECT + row);
        final String error = parsingProblem.getMessage();
        assertTrue(error.contains(ExchangeBean.UNSUPPORTED_STATUS_TYPE.concat("n/a")));
    }
}