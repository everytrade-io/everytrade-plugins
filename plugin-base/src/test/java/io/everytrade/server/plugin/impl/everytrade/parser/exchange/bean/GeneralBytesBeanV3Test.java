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

import static io.everytrade.server.model.Currency.BTC;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.ExchangeBean.FEE_UID_PART;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class GeneralBytesBeanV3Test {
    private static final String HEADER_CORRECT = "Terminal SN;Server Time;Terminal Time;Local Transaction Id;Remote Transaction Id;Type;" +
        "Cash Amount;Cash Currency;Crypto Amount;Crypto Currency;Used Discount;Actual Discount (%);Destination Address;Related Remote " +
        "Transaction Id;Identity;Status;Phone Number;Transaction Detail;Transaction Note;Rate Incl. Fee;Rate Without Fee;" +
        "Fixed Transaction Fee;Expected Profit Percent Setting;Expected Profit Value;Crypto Setting Name;Transaction Scoring Result;" +
        "Expense;Expense Currency;Classification\n";

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
        final String row = "BT401537;2023-04-14 12:30:55;2023-04-14 12:30:55;LHSP8Y;ROPVC8;BUY;400;EUR;0.0134856;BTC;" +
            ";0.00;bc1q9pm3el8xmep52uu3gngmpat06fg4wscq08n039;;I78RBT3O7XE42VOZ;COMPLETED (0);" +
            ";0a288d3bab585a40f25b8bf08626f70e718181ee11f272be45afce7d7bafd08e;;29661.268316;27983.64971;3;5.2;19.62357414;" +
            "EUR->BTC native only;null:null:false:false:false:null;0.0000425;BTC;NORMAL\n";
        final TransactionCluster actual = ParserTestUtils.getTransactionCluster(HEADER_CORRECT + row);
        final TransactionCluster expected = new TransactionCluster(
            new ImportedTransactionBean(
                "LHSP8Y-ROPVC8",
                Instant.parse("2023-04-14T12:30:55Z"),
                BTC,
                Currency.EUR,
                TransactionType.SELL,
                new BigDecimal("0.0134856"),
                new BigDecimal("29661.2683158332"),
                "ROPVC8",
                "bc1q9pm3el8xmep52uu3gngmpat06fg4wscq08n039"
            ),
            List.of(
                new FeeRebateImportedTransactionBean(
                    "LHSP8Y-ROPVC8" + FEE_UID_PART,
                    Instant.parse("2023-04-14T12:30:55Z"),
                    BTC,
                    BTC,
                    TransactionType.FEE,
                    new BigDecimal("0.0000425000"),
                    BTC,
                    "ROPVC8"
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