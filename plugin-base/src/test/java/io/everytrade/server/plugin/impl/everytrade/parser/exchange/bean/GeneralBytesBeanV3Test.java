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
import static org.junit.jupiter.api.Assertions.assertEquals;
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
    void testImportingByStatus() {
        final String row0 = """
            BT401641,2025-01-18 16:01:09,2025-01-18 16:01:09,L4AXMH,RI4IYV,BUY,6000,CZK,0.00220596,BTC,,null,1JazPSi36YwX9DXbP3wbLJ8XjH9oN\
            1AEAi,,ICIY3PORHZ8ZKYRU,COMPLETED (-1),'''+46 73 751 75 09',,,2719904.25937,,,,,,,0,BTC,NORMAL,PHIHP3,,
            BT401639,2025-01-02 11:41:37,2025-01-02 11:41:37,L3VABP,RXKISY,BUY,24000,CZK,0.0100549,BTC,,0.00,,,,COMPLETED (0),,,FS\
             approved on: 2025-01-02T17:33:54.157Z,2386895.941282,2355877.1197,75,1,236.88118812,kvapay_BTC_CZK,,0,BTC,NORMAL,POU3B7,,
            BT401000,2025-06-12 09:07:13,2025-06-12 09:07:13,LIT4XE,RKJYS7,BUY,52000,CZK,2278.237938,USDC,,0.00,\
            ,,,COMPLETED (1),'''+420 737 583 627',,,22.824657,21.51199098,50,6,2940.56603774,kvapay_USDC_CZK,,0,USDC,NORMAL,PXUPXS,,
            BT301084,2025-01-11 18:25:13,2025-01-11 18:25:13,L8YU7P,RDSI2Y,BUY,10000,CZK,0.00413979,BTC,,0.00,,,,\
            ERROR (EXCHANGE WITHDRAWAL),,,,2415581.466693,2305256.5271,75,4,381.73076923,kvapay_BTC_CZK,,0,BTC,NORMAL,P8UMVY,,
            BT401838,2025-06-20 12:32:46,2025-06-20 12:32:46,LX2EUT,R6676R,BUY,4000,CZK,0.00087611,LBTC,,null,,,,\
            ERROR (INVALID UNKNOWN ERROR),,,,4565636.735113,,,,,,,0,LBTC,NORMAL,PYTSCV,,
            BT401084,2025-06-25 11:16:52,2025-06-25 11:16:52,LMVHRV,RWNX9I,BUY,22000,CZK,0.00908924,BTC,,null,,,,\
            ERROR (NO ERROR),'''+420 602 888 650',,,2420444.393591,,,,,,,0,BTC,NORMAL,PHKZH8,,
            BT300894,2025-05-10 20:26:10,2025-05-10 20:26:10,LZPTHD,RO3KOT,BUY,2000,CZK,0.00081397,BTC,,null,,,,IN PROGRESS,\
            ,,,2457093.013256,,,,,,,0,BTC,NORMAL,PZRB3K,,
            BT300894,2025-02-02 17:48:08,2025-02-02 17:48:08,L8MDKU,RZVYO2,SELL,4000,CZK,0.00178625,BTC,,0.00,,,,\
            ERROR (COINS UNCONFIRMED ON EXCHANGE),,,,2239328.20154,2397156.3573,25,6,227.83018868,kvapay_BTC_CZK,,0,BTC,NORMAL,PZRB3K,,
            BT400575,2023-02-13 10:31:54,2023-02-13 10:31:54,LDTMA4,RKKEM2,SELL,950,EUR,0.04919391,BTC,,0.00,,,,\
            ERROR (INVALID UNKNOWN ERROR),,,,19311.333456,20158.5,3,3.9,35.77189605,,,0,BTC,NORMAL,,,
            BT401664,2025-06-27 12:08:16,2025-06-27 12:08:16,LMK7DC,R57B62,SELL,24000,CZK,1224.755148,USDC,,0.00,,,I5I6CUTQ4SVQWK2Q,\
            ERROR (WITHDRAWAL PROBLEM),,,,19.595754,21.1146,50,7,1573.36448598,kvapay_USDC_CZK,,0,USDC,NORMAL,P8YOBR,,
            BT401780,2025-01-16 17:17:43,2025-01-16 17:17:43,L2PB8M,RAKB7E,SELL,10000,CZK,1817.993218,TRX,,0.00,,,IPCI2H42MHXAU7DZ,\
            PAYMENT ARRIVED,'''+420 775 923 250',,,5.500571,5.8663,25,6,567.45283019,kvapay_TRX_CZK,,0,TRX,NORMAL,PZPMCO,,
            """;

        final List<TransactionCluster> actual =
            ParserTestUtils.getTransactionClusters(HEADER_CORRECT.replace(';', ',') + row0, "profile 1");

        assertEquals(11, actual.size());
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
                new BigDecimal("29661.26831583318502699"),
                "ROPVC8",
                "bc1q9pm3el8xmep52uu3gngmpat06fg4wscq08n039",
                "COMPLETED (0)"
            ),
            List.of(
                new FeeRebateImportedTransactionBean(
                    "LHSP8Y-ROPVC8" + FEE_UID_PART,
                    Instant.parse("2023-04-14T12:30:55Z"),
                    BTC,
                    BTC,
                    TransactionType.FEE,
                    new BigDecimal("0.00004250000000000"),
                    BTC,
                    "ROPVC8",
                    null,
                    "COMPLETED (0)"
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