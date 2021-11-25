package io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean;

import io.everytrade.server.model.Currency;
import io.everytrade.server.model.TransactionType;
import io.everytrade.server.plugin.api.parser.BuySellImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.FeeRebateImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.ParsingProblem;
import io.everytrade.server.plugin.api.parser.TransactionCluster;
import io.everytrade.server.plugin.impl.everytrade.parser.exception.ParsingProcessException;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.ExchangeBean;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.ExchangeBean.FEE_UID_PART;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class GeneralBytesBeanV2Test {
    private static final String HEADER_CORRECT = "Terminal SN;Server Time;Terminal Time;Local Transaction Id;" +
        "Remote Transaction Id;Type;Cash Amount;Cash Currency;Crypto Amount;Crypto Currency;Used Discount;" +
        "Actual Discount (%);Destination Address;Related Remote Transaction Id;Identity;Status;Phone Number;" +
        "Transaction Detail;Transaction Note;Rate Incl. Fee;Rate Without Fee;Fixed Transaction Fee;" +
        "Expected Profit Percent Setting;Expected Profit Value;Crypto Setting Name;Transaction Scoring Result;" +
        "Expense;Expense Currency;\n";

    @Test
    void testCorrectHeader() {
        try {
            ParserTestUtils.testParsing(HEADER_CORRECT);
        } catch (ParsingProcessException e) {
            fail("Unexpected exception has been thrown." + e.getMessage());
        }
    }

    @Test
    void testWrongHeader() {
        final String headerWrong = "Server Time;Terminal Time;Local Transaction Id;" +
            "Remote Transaction Id;Type;Cash Amount;Cash Currency;Crypto Amount;Crypto Currency;Used Discount;" +
            "Actual Discount (%);Destination Address;Related Remote Transaction Id;Identity;Status;Phone Number;" +
            "Transaction Detail;Transaction Note;Rate Incl. Fee;Rate Without Fee;Fixed Transaction Fee;" +
            "Expected Profit Percent Setting;Expected Profit Value;Crypto Setting Name;Transaction Scoring Result;" +
            "Expense;Expense Currency\n";
        try {
            ParserTestUtils.testParsing(headerWrong);
            fail("No expected exception has been thrown.");
        } catch (ParsingProcessException ignored) {
        }
    }

    @Test
    void testCorrectParsingRawTransactionBuy() {
        final String row = "B1;2020-12-07 15:33:55;2020-12-07 16:33:55;L;R;SELL;200;CZK;0.107841;LTC;;0.00;x;;;" +
            "PAYMENT ARRIVED;;;;1854.582209;1854.57753657;0;0;0;ltc  ltc ltc;;0.0053;LTC;\n";
        final TransactionCluster actual = ParserTestUtils.getTransactionCluster(HEADER_CORRECT + row);
        final TransactionCluster expected = new TransactionCluster(
            new BuySellImportedTransactionBean(
                "L-R",
                Instant.parse("2020-12-07T15:33:55Z"),
                Currency.LTC,
                Currency.CZK,
                TransactionType.BUY,
                new BigDecimal("0.107841"),
                new BigDecimal("1854.5822089929"),
                "R"
            ),
            List.of(
                new FeeRebateImportedTransactionBean(
                    "L-R" + FEE_UID_PART,
                    Instant.parse("2020-12-07T15:33:55Z"),
                    Currency.LTC,
                    Currency.CZK,
                    TransactionType.FEE,
                    new BigDecimal("0.0053"),
                    Currency.LTC,
                    "R"
                )
            )
        );
        ParserTestUtils.checkEqual(expected, actual);
    }

    @Test
    void testCorrectParsingRawTransactionSell() {
        final String row = "B1;2020-12-10 15:02:41;2020-12-10 16:02:41;L;R;BUY;100;CZK;0.04595383;LTC;;0.00;x;;;" +
            "COMPLETED (0);;;;2176.097183;2176.0974;0;0;0;LTC test;;0.0001;LTC;\n";
        final TransactionCluster actual = ParserTestUtils.getTransactionCluster(HEADER_CORRECT + row);
        final TransactionCluster expected = new TransactionCluster(
            new BuySellImportedTransactionBean(
                "L-R",
                Instant.parse("2020-12-10T15:02:41Z"),
                Currency.LTC,
                Currency.CZK,
                TransactionType.SELL,
                new BigDecimal("0.04595383"),
                new BigDecimal("2176.0971827593"),
                "R"
            ),
            List.of(
                new FeeRebateImportedTransactionBean(
                    "L-R" + FEE_UID_PART,
                    Instant.parse("2020-12-10T15:02:41Z"),
                    Currency.LTC,
                    Currency.CZK,
                    TransactionType.FEE,
                    new BigDecimal("0.0001"),
                    Currency.LTC,
                    "R"
                )
            )
        );
        ParserTestUtils.checkEqual(expected, actual);
    }

    @Test
    void testCorrectParsingRawTransactionDate_d_m_yy() {
        final String row = "B1;8/6/18 06:38:43.0;2020-12-10 16:02:41;L;R;BUY;100;CZK;0.04595383;LTC;;0.00;x;;;" +
            "COMPLETED (0);;;;2176.097183;2176.0974;0;0;0;LTC test;;0.0001;LTC;\n";
        final TransactionCluster actual = ParserTestUtils.getTransactionCluster(HEADER_CORRECT + row);
        final TransactionCluster expected = new TransactionCluster(
            new BuySellImportedTransactionBean(
                "L-R",
                Instant.parse("2018-08-06T06:38:43Z"),
                Currency.LTC,
                Currency.CZK,
                TransactionType.SELL,
                new BigDecimal("0.04595383"),
                new BigDecimal("2176.0971827593"),
                "R"
            ),
            List.of(
                new FeeRebateImportedTransactionBean(
                    "L-R" + FEE_UID_PART,
                    Instant.parse("2018-08-06T06:38:43Z"),
                    Currency.LTC,
                    Currency.CZK,
                    TransactionType.FEE,
                    new BigDecimal("0.0001"),
                    Currency.LTC,
                    "R"
                )
            )
        );
        ParserTestUtils.checkEqual(expected, actual);
    }

    @Test
    void testCorrectParsingRawTransactionDate_d_m_yyTime_h_mm() {
        final String row = "B1;8/6/18 6:38:00 AM;2020-12-10 16:02:41;L;R;BUY;100;CZK;0.04595383;LTC;;0.00;x;;;" +
            "COMPLETED (0);;;;2176.097183;2176.0974;0;0;0;LTC test;;0.0001;LTC;\n";
        final TransactionCluster actual = ParserTestUtils.getTransactionCluster(HEADER_CORRECT + row);
        final TransactionCluster expected = new TransactionCluster(
            new BuySellImportedTransactionBean(
                "L-R",
                Instant.parse("2018-08-06T06:38:00Z"),
                Currency.LTC,
                Currency.CZK,
                TransactionType.SELL,
                new BigDecimal("0.04595383"),
                new BigDecimal("2176.0971827593"),
                "R"
            ),
            List.of(
                new FeeRebateImportedTransactionBean(
                    "L-R" + FEE_UID_PART,
                    Instant.parse("2018-08-06T06:38:00Z"),
                    Currency.LTC,
                    Currency.CZK,
                    TransactionType.FEE,
                    new BigDecimal("0.0001"),
                    Currency.LTC,
                    "R"
                )
            )
        );
        ParserTestUtils.checkEqual(expected, actual);
    }

    @Test
    void testUnknownStatus() {
        final String row = "B1;8/6/18 6:38:00 AM;2020-12-10 16:02:41;L;R;BUY;100;CZK;0.04595383;LTC;;0.00;x;;;" +
            "UNKNOWN;;;;2176.097183;2176.0974;0;0;0;LTC test;;0.0001;LTC;\n";
     final ParsingProblem parsingProblem = ParserTestUtils.getParsingProblem(HEADER_CORRECT + row);
        final String error = parsingProblem.getMessage();
        assertTrue(error.contains(ExchangeBean.UNSUPPORTED_STATUS_TYPE.concat("UNKNOWN")));
    }

    @Test
    void testIgnoredTransactionType() {
        final String row = "B1;8/6/18 6:38:00 AM;2020-12-10 16:02:41;L;R;SOLD;100;CZK;0.04595383;LTC;;0.00;x;;;" +
            "UNKNOWN;;;;2176.097183;2176.0974;0;0;0;LTC test;;0.0001;LTC;\n";
        final ParsingProblem parsingProblem = ParserTestUtils.getParsingProblem(HEADER_CORRECT + row);
        final String error = parsingProblem.getMessage();
        assertTrue(error.contains(ExchangeBean.UNSUPPORTED_TRANSACTION_TYPE.concat("SOLD")));
    }
}